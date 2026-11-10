package com.zhx.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

@Configuration
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    @Bean
    @Primary
    public DataSource dataSource(Environment env) throws Exception {
        String url = env.getProperty("spring.datasource.url");
        String user = env.getProperty("spring.datasource.username", "root");
        String pass = env.getProperty("spring.datasource.password", "");

        if (url != null && url.startsWith("jdbc:h2:")) {
            return buildHikari(url, env.getProperty("spring.datasource.username", "sa"), pass, "h2");
        }

        DriverManager.setLoginTimeout(4);
        try (Connection c = DriverManager.getConnection(url, user, pass)) {
            log.info("MySQL connected: {}", safeUrl(url));
        } catch (Exception mysqlError) {
            // Only an unknown database can be repaired automatically. Never switch datasets.
            createDatabaseIfMissing(url, user, pass, mysqlError);
            try (Connection c = DriverManager.getConnection(url, user, pass)) {
                log.info("MySQL connected after database creation: {}", safeUrl(url));
            }
        }
        return buildHikari(url, user, pass, "mysql");
    }

    private DataSource buildHikari(String url, String user, String pass, String mode) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(pass);
        config.setMaximumPoolSize(12);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(5000);
        config.setPoolName("zhx-pool");
        log.info("Using datasource {} ({})", mode, safeUrl(url));
        return new HikariDataSource(config);
    }

    private void createDatabaseIfMissing(String url, String user, String pass, Exception error) throws Exception {
        String msg = error.getMessage() == null ? "" : error.getMessage();
        if (!msg.toLowerCase().contains("unknown database")) throw error;
        int q = url.indexOf('?');
        String base = q >= 0 ? url.substring(0, q) : url;
        int slash = base.lastIndexOf('/');
        if (slash < 0) return;
        String dbName = base.substring(slash + 1);
        if (!dbName.matches("[A-Za-z0-9_]+")) throw error;
        String adminUrl = base.substring(0, slash + 1) + "mysql" + (q >= 0 ? url.substring(q) : "");
        try (Connection c = DriverManager.getConnection(adminUrl, user, pass);
             Statement st = c.createStatement()) {
            st.execute("CREATE DATABASE IF NOT EXISTS `" + dbName + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_bin");
            log.info("Created database {} because it was missing", dbName);
        }
    }

    private String safeUrl(String url) {
        return url == null ? "null" : url.replaceAll("password=[^&]*", "password=***");
    }
}
