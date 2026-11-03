package ro.ubb.tickets.txmanager;

import ro.ubb.tickets.db.DbName;

import java.util.*;

public record UndoEntry(
        DbName db,
        String sql,
        List<Object> params,
        String description
) {
    public UndoEntry {
        Objects.requireNonNull(db, "db must not be null");
        Objects.requireNonNull(sql, "sql must not be null");
        Objects.requireNonNull(params, "params must not be null");
    }

    public static UndoEntry of(DbName db, String description, String sql, Object... params) {
        return new UndoEntry(db, sql,
                Collections.unmodifiableList(Arrays.asList(params.clone())), description);
    }
}
