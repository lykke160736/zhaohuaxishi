package com.zhx;

import com.zaxxer.hikari.HikariDataSource;
import com.zhx.config.DatabaseConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatabaseConfigTest {
    private MockEnvironment environment() {
        return new MockEnvironment().withProperty("spring.datasource.url", "jdbc:mysql://localhost/zhx")
                .withProperty("spring.datasource.username", "root")
                .withProperty("spring.datasource.password", "test-only");
    }

    @Test
    void invalidMysqlCredentialsFailInsteadOfSelectingAnotherDatabase() throws Exception {
        var failure = new SQLException("Access denied", "28000");
        try (var driver = mockStatic(DriverManager.class)) {
            driver.when(() -> DriverManager.getConnection("jdbc:mysql://localhost/zhx", "root", "test-only"))
                    .thenThrow(failure);
            assertSame(failure, assertThrows(SQLException.class,
                    () -> new DatabaseConfig().dataSource(environment())));
        }
    }

    @Test
    void creatingMissingMysqlDatabaseRetriesOriginalConnection() throws Exception {
        Connection business = mock(Connection.class);
        Connection admin = mock(Connection.class);
        Statement statement = mock(Statement.class);
        when(admin.createStatement()).thenReturn(statement);
        var missingDatabase = new SQLException("Unknown database 'zhx'", "42000");
        try (var driver = mockStatic(DriverManager.class);
             var pool = mockConstruction(HikariDataSource.class)) {
            driver.when(() -> DriverManager.getConnection("jdbc:mysql://localhost/zhx", "root", "test-only"))
                    .thenThrow(missingDatabase)
                    .thenReturn(business);
            driver.when(() -> DriverManager.getConnection("jdbc:mysql://localhost/mysql", "root", "test-only"))
                    .thenReturn(admin);
            assertNotNull(new DatabaseConfig().dataSource(environment()));
            driver.verify(() -> DriverManager.getConnection("jdbc:mysql://localhost/zhx", "root", "test-only"), times(2));
            verify(statement).execute("CREATE DATABASE IF NOT EXISTS `zhx` CHARACTER SET utf8mb4 COLLATE utf8mb4_bin");
            assertEquals(1, pool.constructed().size());
        }
    }
}
