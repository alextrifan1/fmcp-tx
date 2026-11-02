package ro.ubb.tickets.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DataSourceConfig {

    @Bean(destroyMethod = "close")
    public HikariDataSource accountsDataSource(
            @Value("${app.db.accounts.url}") String url,
            @Value("${app.db.accounts.username}") String user,
            @Value("${app.db.accounts.password}") String pass) {
        return build("accounts-pool", url, user, pass);
    }

    @Bean(destroyMethod = "close")
    public HikariDataSource eventsDataSource(
            @Value("${app.db.events.url}") String url,
            @Value("${app.db.events.username}") String user,
            @Value("${app.db.events.password}") String pass) {
        return build("events-pool", url, user, pass);
    }

    private HikariDataSource build(String poolName, String url, String user, String pass) {
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setPoolName(poolName);
        hikariConfig.setJdbcUrl(url);
        hikariConfig.setUsername(user);
        hikariConfig.setPassword(pass);
        hikariConfig.setAutoCommit(true);
        return new HikariDataSource(hikariConfig);
    }
}
