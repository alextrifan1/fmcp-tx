package ro.ubb.tickets.db;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class DbHealthController {

    private final DatabaseRegistry registry;

    public DbHealthController(DatabaseRegistry registry) {
        this.registry = registry;
    }

    @GetMapping("/api/health/db")
    public Map<String, Object> check() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (DbName db : DbName.values()) {
            try (Connection c = registry.get(db).getConnection();
                 Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT current_database(), count(*) " +
                                 "FROM information_schema.tables WHERE table_schema = 'public'")) {
                rs.next();
                result.put(db.name(), Map.of(
                        "database", rs.getString(1),
                        "tables", rs.getInt(2),
                        "autoCommit", c.getAutoCommit()));
            } catch (SQLException e) {
                result.put(db.name(), Map.of("error", e.getMessage()));
            }
        }
        return result;
    }
}