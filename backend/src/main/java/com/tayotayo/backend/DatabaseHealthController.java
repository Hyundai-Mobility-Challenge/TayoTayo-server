package com.tayotayo.backend;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DatabaseHealthController {
    private final JdbcTemplate jdbcTemplate;

    public DatabaseHealthController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/health/database")
    public Map<String, String> databaseHealth() {
        String databaseName = jdbcTemplate.queryForObject("SELECT DATABASE()", String.class);
        return Map.of("status", "UP", "database", databaseName);
    }
}
