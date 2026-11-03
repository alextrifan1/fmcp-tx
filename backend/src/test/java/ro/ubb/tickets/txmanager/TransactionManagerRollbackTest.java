package ro.ubb.tickets.txmanager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import ro.ubb.tickets.db.DatabaseRegistry;
import ro.ubb.tickets.db.DbName;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Needs both Docker databases running with fresh seed data. */
@SpringBootTest
class TransactionManagerRollbackTest {

    @Autowired TransactionManager tm;
    @Autowired DatabaseRegistry db;

    @Test
    void sqlFailureRollsBackBothDatabases() throws SQLException {
        long seatId = 1;
        long carolWallet = 3;
        Map<String, Object> seatBefore = row(DbName.EVENTS, "seats", seatId);
        Map<String, Object> walletBefore = row(DbName.ACCOUNTS, "wallets", carolWallet);
        long reservationsBefore = count(DbName.EVENTS, "reservations");

        assertThrows(TransactionAbortedException.class, () -> tm.inTransaction(tx -> {
            tm.execute(tx, Operation.update(DbName.EVENTS, "seats", seatId,
                    "UPDATE seats SET status = 'RESERVED' WHERE id = ?", seatId));
            tm.execute(tx, Operation.insert(DbName.EVENTS, "reservations",
                    "INSERT INTO reservations (seat_id, user_id) VALUES (?, ?) RETURNING id", seatId, 3));
            // carol has 20, ticket costs 60 -> CHECK (balance >= 0) fails
            tm.execute(tx, Operation.update(DbName.ACCOUNTS, "wallets", carolWallet,
                    "UPDATE wallets SET balance = balance - ? WHERE id = ?", new BigDecimal("60"), carolWallet));
            return null;
        }));

        assertEquals(seatBefore, row(DbName.EVENTS, "seats", seatId));
        assertEquals(walletBefore, row(DbName.ACCOUNTS, "wallets", carolWallet));
        assertEquals(reservationsBefore, count(DbName.EVENTS, "reservations"));
    }

    @Test
    void businessAbortUndoesInsertUpdateAndDelete() throws SQLException {
        long aliceWallet = 1;
        long seatId = 10;
        Map<String, Object> walletBefore = row(DbName.ACCOUNTS, "wallets", aliceWallet);
        Map<String, Object> seatBefore = row(DbName.EVENTS, "seats", seatId);
        long paymentsBefore = count(DbName.ACCOUNTS, "payments");
        assertNotNull(seatBefore, "seat 10 must exist - reset the DBs with 'docker compose down -v'");

        assertThrows(IllegalStateException.class, () -> tm.inTransaction(tx -> {
            tm.execute(tx, Operation.update(DbName.ACCOUNTS, "wallets", aliceWallet,
                    "UPDATE wallets SET balance = balance - 120 WHERE id = ?", aliceWallet));
            tm.execute(tx, Operation.insert(DbName.ACCOUNTS, "payments",
                    "INSERT INTO payments (wallet_id, amount, type) VALUES (?, ?, 'PURCHASE') RETURNING id",
                    aliceWallet, new BigDecimal("120")));
            tm.execute(tx, Operation.delete(DbName.EVENTS, "seats", seatId,
                    "DELETE FROM seats WHERE id = ?", seatId));
            throw new IllegalStateException("business rule failed");
        }));

        assertEquals(walletBefore, row(DbName.ACCOUNTS, "wallets", aliceWallet));
        assertEquals(seatBefore, row(DbName.EVENTS, "seats", seatId));   // same id, same values
        assertEquals(paymentsBefore, count(DbName.ACCOUNTS, "payments"));
    }

    // ---------- helpers (plain JDBC, outside the transaction manager) ----------

    private Map<String, Object> row(DbName name, String table, long id) throws SQLException {
        try (Connection c = db.get(name).getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM " + table + " WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                ResultSetMetaData meta = rs.getMetaData();
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    row.put(meta.getColumnLabel(i), rs.getObject(i));
                }
                return row;
            }
        }
    }

    private long count(DbName name, String table) throws SQLException {
        try (Connection c = db.get(name).getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM " + table)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}