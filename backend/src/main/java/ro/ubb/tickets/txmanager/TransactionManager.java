package ro.ubb.tickets.txmanager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import ro.ubb.tickets.db.DatabaseRegistry;

import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


/**
 * Application-level transaction manager.
 * Every SQL statement runs with autoCommit=true; atomicity across both
 * databases comes from the undo log (compensating statements).
 */
@Component
public class TransactionManager {

    private static final Logger log = LoggerFactory.getLogger(TransactionManager.class);
    private static final Pattern IDENTIFIER = Pattern.compile("[a-z_][a-z0-9_]*");

    private final DatabaseRegistry databases;
    private final AtomicLong nextId = new AtomicLong(1);

    private final Map<Long, Transaction> transactions = new ConcurrentHashMap<>();

    public TransactionManager(DatabaseRegistry databases) {
        this.databases = databases;
    }

    // public API

    public Transaction begin() {
        Transaction tx = new Transaction(nextId.getAndIncrement());
        transactions.put(tx.getId(), tx);
        log.info("{} BEGIN", tx);
        return tx;
    }

    // exec one operation and pushes its compensating statement to the undo log
    public OperationResult execute(Transaction tx, Operation op) {
        tx.ensureActive();
        validateIdentifier(op.table());

        try (Connection conn = databases.get(op.db()).getConnection()) {
            Executed done = switch (op.type()) {
                case SELECT -> executeSelect(conn, op);
                case INSERT -> executeInsert(conn, op);
                case UPDATE -> executeUpdate(conn, op);
                case DELETE -> executeDelete(conn, op);
            };
            tx.recordExecuted(op, done.undo());
            return done.result();
        } catch (SQLException e) {
            // The failed statement itself changed nothing (autoCommit), so we only undo earlier ones.
            abort(tx, "SQL error: " + e.getMessage());
            throw new TransactionAbortedException(tx.getId(), e.getMessage(), e);
        }
    }



    public void commit(Transaction tx) {
        if (!tx.markCommitted()) {
            if (tx.getStatus() == TransactionStatus.ABORT) {
                // Aborted by someone else (e.g. deadlock victim): finish the rollback here.
                abort(tx, "aborted before commit");
                throw new TransactionAbortedException(tx.getId(), "Transaction aborted before commit");
            }
            throw new IllegalStateException("Transaction already committed: " + tx);
        }
        tx.clearUndoLog();
        log.info("{} COMMIT", tx);
    }

    /**
     * Rolls back by running compensations in reverse order. Idempotent.
     * Must be called from the transaction's own thread (the undo log is not thread-safe).
     */
    public void abort(Transaction tx, String reason) {
        if (tx.getStatus() == TransactionStatus.COMMIT) {
            throw new IllegalStateException("Transaction already committed: " + tx);
        }
        tx.markAborted();
        rollback(tx);
        log.warn("{} ABORT: {}", tx, reason);
    }

    // begin -> body -> commit/abort or any exception
    public <T> T inTransaction(Function<Transaction, T> body) {
        Transaction tx = begin();
        try {
            T result = body.apply(tx);
            commit(tx);
            return result;
        } catch (RuntimeException e) {
            if (tx.getStatus() != TransactionStatus.COMMIT) {
                abort(tx, e.getMessage());
            }
            throw e;
        }
    }

    public Collection<Transaction> getTransactions() {
        return List.copyOf(transactions.values());
    }

    // one method per op type

    private record Executed(OperationResult result, UndoEntry undo) {}

    private Executed executeSelect(Connection conn, Operation op) throws SQLException {
        return new Executed(OperationResult.ofRows(query(conn, op.sql(), op.params())), null);
    }

    private Executed executeInsert(Connection conn, Operation op) throws SQLException {
        long newId;
        try (PreparedStatement ps = prepare(conn, op.sql(), op.params());
            ResultSet rs = ps.executeQuery()) {
            if (!rs.next()) {
                throw new SQLException("INSERT ... RETURNING id returned no row");
            }
            newId = rs.getLong("id");
        }
        UndoEntry undo = UndoEntry.of(op.db(), "undo INSERT " + op.table() + " #" + newId,
                "DELETE FROM " + op.table() + " WHERE id = ?", newId);
        return new Executed(OperationResult.ofInsert(newId), undo);
    }

    private Executed executeUpdate(Connection conn, Operation op) throws SQLException {
        Map<String, Object> before = readRow(conn, op.table(), op.recordId());
        int affected = update(conn, op.sql(), op.params());
        //nothing changed -> nothing to undo
        UndoEntry undo = (before == null || affected == 0) ? null : restoreColumns(op, before);
        return new Executed(OperationResult.ofUpdate(affected), undo);
    }

    private Executed executeDelete(Connection conn, Operation op) throws SQLException {
        Map<String, Object> before = readRow(conn, op.table(), op.recordId());
        int affected = update(conn, op.sql(), op.params());
        UndoEntry undo = (before == null || affected == 0) ? null : reinsertRow(op, before);
        return new Executed(OperationResult.ofUpdate(affected), undo);
    }

    // compensation builders

    /** UPDATE t SET every column back to its old value WHERE id = ? */
    private UndoEntry restoreColumns(Operation op, Map<String, Object> before) {
        List<String> columns = before.keySet().stream().filter(c -> !c.equals("id")).toList();
        String setClause = columns.stream().map(c -> c + " = ?").collect(Collectors.joining(", "));

        List<Object> params = new ArrayList<>(columns.size() + 1);
        columns.forEach(c -> params.add(before.get(c)));
        params.add(op.recordId());

        return new UndoEntry(op.db(),
                "UPDATE " + op.table() + " SET " + setClause + " WHERE id = ?",
                Collections.unmodifiableList(params),
                "undo UPDATE " + op.table() + " #" + op.recordId());
    }

    /** INSERT the deleted row back, with its original id. */
    private static UndoEntry reinsertRow(Operation op, Map<String, Object> before) {
        String columns = String.join(", ", before.keySet());
        String marks = before.keySet().stream().map(c -> "?").collect(Collectors.joining(", "));

        return new UndoEntry(op.db(),
                "INSERT INTO " + op.table() + " (" + columns + ") VALUES (" + marks + ")",
                Collections.unmodifiableList(new ArrayList<>(before.values())),
                "undo DELETE " + op.table() + " #" + op.recordId());
    }

    private void rollback(Transaction tx) {
        UndoEntry undo;
        while ((undo = tx.popUndo()) != null) {
            try (Connection conn = databases.get(undo.db()).getConnection()) {
                update(conn, undo.sql(), undo.params());
                log.info("{} {}", tx, undo.description());
            } catch (SQLException e) {
                // Keep going: one failed compensation must not block the rest.
                log.error("{} compensation FAILED: {} ({})", tx, undo.description(), e.getMessage());
            }
        }
    }

    // jdbc helpers

    private static Map<String, Object> readRow(Connection conn, String table, long id) throws SQLException {
        List<Map<String, Object>> rows = query(conn, "SELECT * FROM " + table + " WHERE id = ?", List.of(id));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private static List<Map<String, Object>> query(Connection conn, String sql, List<Object> params) throws SQLException {
        try (PreparedStatement ps = prepare(conn, sql, params);
             ResultSet rs = ps.executeQuery()) {
            ResultSetMetaData meta = rs.getMetaData();
            List<Map<String, Object>> rows = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    row.put(meta.getColumnLabel(i), rs.getObject(i));
                }
                rows.add(row);
            }
            return rows;
        }
    }

    private static int update(Connection conn, String sql, List<Object> params) throws SQLException {
        try (PreparedStatement ps = prepare(conn, sql, params)) {
            return ps.executeUpdate();
        }
    }

    private static PreparedStatement prepare(Connection conn, String sql, List<Object> params) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(sql);
        for (int i = 0; i < params.size(); i++) {
            ps.setObject(i + 1, params.get(i));
        }
        return ps;
    }

    private static void validateIdentifier(String name) {
        if (!IDENTIFIER.matcher(name).matches()) {
            throw new IllegalArgumentException("Invalid table name: " + name);
        }
    }

}
