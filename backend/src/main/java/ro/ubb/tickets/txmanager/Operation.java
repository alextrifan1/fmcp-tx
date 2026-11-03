package ro.ubb.tickets.txmanager;

import ro.ubb.tickets.db.DbName;

import java.util.*;

public record Operation(
        DbName db,
        String table,
        Long recordId,
        OperationType type,
        String sql,
        List<Object> params
) {
    public Operation {
        Objects.requireNonNull(db, "db must not be null");
        Objects.requireNonNull(table, "table must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(sql, "sql must not be null");
        Objects.requireNonNull(params, "params must not be null");

        if (type == OperationType.INSERT) {
            if (recordId != null) {
                throw new IllegalArgumentException("INSERT nu are recordId: id-ul vine din RETURNING id");
            }
            if (!sql.toLowerCase(Locale.ROOT).contains("returning id")) {
                throw new IllegalArgumentException("INSERT trebuie sa aiba RETURNING id: " + sql);
            }
        } else if (recordId == null) {
            throw new IllegalArgumentException(type + " trebuie să aibă recordId");
        }
    }

    // factory methods
    public static Operation select(DbName db, String table, Long recordId, String sql, Object... params) {
        return new Operation(db, table, recordId, OperationType.SELECT, sql, toList(params));
    }

    public static Operation insert(DbName db, String table, String sql, Object... params) {
        return new Operation(db, table, null, OperationType.INSERT, sql, toList(params));
    }

    public static Operation update(DbName db, String table, Long recordId, String sql, Object... params) {
        return new Operation(db, table, recordId, OperationType.UPDATE, sql, toList(params));
    }

    public static Operation delete(DbName db, String table, Long recordId, String sql, Object... params) {
        return new Operation(db, table, recordId, OperationType.DELETE, sql, toList(params));
    }

    // List.of nu accepta null
    private static List<Object> toList(Object... params) {
        return Collections.unmodifiableList(Arrays.asList(params.clone()));
    }
}
