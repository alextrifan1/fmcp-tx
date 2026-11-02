package ro.ubb.tickets.db;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.EnumMap;
import java.util.Map;

@Component
public class DatabaseRegistry {

    private final Map<DbName, DataSource> sources = new EnumMap<>(DbName.class);

    public DatabaseRegistry(HikariDataSource accountsDataSource,
                            HikariDataSource eventsDataSource) {
        sources.put(DbName.ACCOUNTS, accountsDataSource);
        sources.put(DbName.EVENTS, eventsDataSource);
    }

    public DataSource get(DbName dbName) {
        return sources.get(dbName);
    }
}
