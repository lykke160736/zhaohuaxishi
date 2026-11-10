package com.zhx.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

@Component
@Order(1)
public class DatabaseBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseBootstrap.class);
    private static final List<String> TABLES = List.of(
            "settings", "accounts", "stores", "batches", "orders", "payment_intents",
            "refunds", "aftersales", "reviews", "favorites", "wallets", "coupons",
            "notices", "logs", "drafts", "applications");
    private static final Set<String> MONEY_COLUMNS = Set.of(
            "price", "amount", "original_amount", "discount", "min_spend");
    private static final Pattern ISO = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{3}Z$");

    private final DataSource dataSource;
    private final ObjectMapper objectMapper;
    private final boolean seedDemo;

    public DatabaseBootstrap(DataSource dataSource,
                             ObjectMapper objectMapper,
                             org.springframework.core.env.Environment env) {
        this.dataSource = dataSource;
        this.objectMapper = objectMapper;
        this.seedDemo = Boolean.parseBoolean(env.getProperty("app.demo.seed", "true"));
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        initSchema();
        if (seedDemo) {
            importDemoDataIfEmpty();
        }
        ensureTokenSecret();
        try (Connection c = dataSource.getConnection()) {
            log.info("Database ready: product={}", c.getMetaData().getDatabaseProductName());
        }
    }

    private void initSchema() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            DatabaseMetaData meta = c.getMetaData();
            String product = meta.getDatabaseProductName().toUpperCase();
            if (product.contains("H2")) {
                ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                        new ClassPathResource("db/schema-h2.sql"));
                populator.execute(dataSource);
                log.info("H2 schema ensured");
                return;
            }
            String ddl = readMysqlDdl();
            executeMysqlDdl(c, ddl);
            log.info("MySQL schema ensured");
        }
    }

    private String readMysqlDdl() throws IOException {
        List<Path> candidates = List.of(
                Path.of("docs/java-migration/mysql-ddl.sql"),
                Path.of("../docs/java-migration/mysql-ddl.sql"),
                Path.of("../../docs/java-migration/mysql-ddl.sql"));
        for (Path p : candidates) {
            if (Files.exists(p)) return Files.readString(p, StandardCharsets.UTF_8);
        }
        Resource fallback = new ClassPathResource("db/schema-mysql.sql");
        return new String(fallback.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }

    private void executeMysqlDdl(Connection c, String ddl) throws Exception {
        StringBuilder cleaned = new StringBuilder();
        for (String line : ddl.split("\\R")) {
            if (line.stripLeading().startsWith("--")) continue;
            cleaned.append(line).append('\n');
        }
        for (String raw : cleaned.toString().split(";")) {
            String sql = raw.trim();
            if (sql.isEmpty()) continue;
            String upper = sql.toUpperCase();
            if (upper.startsWith("SET ") || upper.startsWith("DROP TABLE")) continue;
            if (upper.startsWith("CREATE TABLE")) {
                sql = sql.replaceFirst("(?i)CREATE TABLE", "CREATE TABLE IF NOT EXISTS");
            }
            try (Statement st = c.createStatement()) {
                st.execute(sql);
            }
        }
    }

    private void ensureTokenSecret() throws Exception {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             var rs = st.executeQuery("SELECT `value` FROM `settings` WHERE `key` = 'token_secret'")) {
            if (rs.next()) return;
        }
        byte[] secret = new byte[32];
        java.security.SecureRandom random = new java.security.SecureRandom();
        random.nextBytes(secret);
        StringBuilder hex = new StringBuilder(64);
        for (byte b : secret) hex.append(String.format("%02x", b));
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO `settings` (`key`, `value`) VALUES ('token_secret', ?)")) {
            ps.setString(1, hex.toString());
            ps.executeUpdate();
            log.info("token_secret initialized");
        }
    }

    private void importDemoDataIfEmpty() throws Exception {
        importDemoData(false);
    }

    /** 测试支持：无条件重建演示数据。 */
    public void resetDemoData() throws Exception {
        importDemoData(true);
    }

    private void importDemoData(boolean force) throws Exception {
        if (!force) {
            try (Connection c = dataSource.getConnection()) {
                try (Statement st = c.createStatement();
                     var rs = st.executeQuery("SELECT COUNT(*) FROM accounts")) {
                    rs.next();
                    if (rs.getInt(1) > 0) {
                        log.info("Accounts already present, skip demo import");
                        return;
                    }
                }
            }
        }

        Map<String, List<Map<String, Object>>> data;
        try (var in = new ClassPathResource("demo-seed.json").getInputStream()) {
            var type = objectMapper.getTypeFactory().constructMapType(
                    LinkedHashMap.class,
                    objectMapper.getTypeFactory().constructType(String.class),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Map.class));
            data = objectMapper.readValue(in, type);
        }

        Instant anchor = Instant.parse(String.valueOf(data.get("batches").get(0).get("pickup_start"))).plusSeconds(20 * 60);
        long shift = java.time.Duration.between(anchor, Instant.now()).toMillis();
        shiftSeedTimes(data, shift);
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                for (String table : TABLES) {
                    try (Statement st = c.createStatement()) {
                        st.execute("DELETE FROM `" + table + "`");
                    }
                }
                for (String table : TABLES) {
                    List<Map<String, Object>> rows = data.getOrDefault(table, List.of());
                    if (rows.isEmpty()) continue;
                    insertRows(c, table, rows);
                }
                c.commit();
            } catch (Exception e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(true);
            }
        }
        log.info("Demo data imported from in-memory Node seed");
    }

    @SuppressWarnings("unchecked")
    private Object shiftSeedTimes(Object value, long shift) throws IOException {
        if (value instanceof Map<?, ?> map) {
            for (var entry : ((Map<String, Object>) map).entrySet()) {
                entry.setValue(shiftSeedTimes(entry.getValue(), shift));
            }
        } else if (value instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                ((List<Object>) list).set(i, shiftSeedTimes(list.get(i), shift));
            }
        } else if (value instanceof String text) {
            if (ISO.matcher(text).matches()) return new java.time.format.DateTimeFormatterBuilder().appendInstant(3).toFormatter().format(Instant.parse(text).plusMillis(shift));
            if (text.startsWith("{") || text.startsWith("[")) {
                Object nested = objectMapper.readValue(text, Object.class);
                return objectMapper.writeValueAsString(shiftSeedTimes(nested, shift));
            }
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private void insertRows(Connection c, String table, List<Map<String, Object>> rows) throws Exception {
        Map<String, Object> first = rows.get(0);
        List<String> columns = new ArrayList<>(first.keySet());
        StringBuilder sql = new StringBuilder("INSERT INTO `").append(table).append("` (");
        StringBuilder values = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sql.append(',');
                values.append(',');
            }
            sql.append('`').append(columns.get(i)).append('`');
            values.append('?');
        }
        sql.append(") VALUES (").append(values).append(')');

        try (PreparedStatement ps = c.prepareStatement(sql.toString())) {
            for (Map<String, Object> row : rows) {
                for (int i = 0; i < columns.size(); i++) {
                    Object value = row.get(columns.get(i));
                    setValue(ps, i + 1, columns.get(i), value);
                }
                ps.addBatch();
            }
            ps.executeBatch();
        }
        log.info("  {} rows -> {}", rows.size(), table);
    }

    private void setValue(PreparedStatement ps, int index, String column, Object value) throws Exception {
        if (value == null) {
            ps.setObject(index, null);
            return;
        }
        if (value instanceof String s && ISO.matcher(s).matches()) {
            LocalDateTime dt = LocalDateTime.ofInstant(Instant.parse(s), ZoneOffset.UTC);
            ps.setObject(index, dt);
            return;
        }
        if (MONEY_COLUMNS.contains(column) && value instanceof Number n) {
            ps.setBigDecimal(index, BigDecimal.valueOf(n.longValue(), 2));
            return;
        }
        if ((column.equals("lat") || column.equals("lng")) && value instanceof Number n) {
            ps.setBigDecimal(index, BigDecimal.valueOf(n.doubleValue()));
            return;
        }
        if (value instanceof Integer i) {
            ps.setInt(index, i);
        } else if (value instanceof Long l) {
            ps.setLong(index, l);
        } else if (value instanceof Number n) {
            ps.setBigDecimal(index, new BigDecimal(n.toString()));
        } else {
            ps.setString(index, value.toString());
        }
    }
}
