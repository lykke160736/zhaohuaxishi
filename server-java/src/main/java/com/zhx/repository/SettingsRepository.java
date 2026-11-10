package com.zhx.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SettingsRepository {
    private final JdbcTemplate jdbc;

    public SettingsRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String findValue(String key) {
        var rows = jdbc.queryForList("SELECT `value` FROM `settings` WHERE `key` = ?", key);
        if (rows.isEmpty()) return null;
        Object value = rows.get(0).get("value");
        return value == null ? null : String.valueOf(value);
    }

    public void upsert(String key, String value) {
        jdbc.update("INSERT INTO `settings` (`key`, `value`) VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE `value` = VALUES(`value`)", key, value);
    }
}
