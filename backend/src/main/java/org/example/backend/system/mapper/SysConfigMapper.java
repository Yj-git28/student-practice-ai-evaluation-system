package org.example.backend.system.mapper;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class SysConfigMapper {

    private final JdbcClient jdbc;

    public SysConfigMapper(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> select(String key) {
        return jdbc.sql("SELECT config_value FROM sys_config WHERE config_key = :key")
                .param("key", key)
                .query(String.class)
                .optional();
    }

    public void upsert(String key, String value) {
        jdbc.sql("""
                INSERT INTO sys_config (config_key, config_value) VALUES (:key, :value)
                ON DUPLICATE KEY UPDATE config_value = :value
                """)
                .param("key", key)
                .param("value", value)
                .update();
    }
}
