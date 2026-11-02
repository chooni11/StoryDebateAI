package com.storydebate.backend;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DatabaseHealthController {

    private final DataSource dataSource;

    public DatabaseHealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/api/db/health")
    public String checkDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(3)) {
                return "PostgreSQL connection successful!";
            }
            return "PostgreSQL connection failed.";
        } catch (SQLException e) {
            return "PostgreSQL connection failed: " + e.getMessage();
        }
    }
}
