package com.moo.charactermanagerservice.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.*;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;


@RestController
@RequestMapping("/health")
public class HealthController {

    @Autowired
    private DataSource dataSource;

    @GetMapping("/db")
    public ResponseEntity<Map<String, Object>> checkDatabaseHealth() {
        Map<String, Object> response = new HashMap<>();
        try (Connection conn = dataSource.getConnection()) {
            boolean valid = conn.isValid(1);
            response.put("status", valid ? "UP" : "DOWN");
            response.put("user", conn.getMetaData().getUserName());
            return ResponseEntity.status(valid ? 200 : 503).body(response);
        } catch (SQLException e) {
            response.put("status", "DOWN");
            response.put("error", e.getMessage());
            return ResponseEntity.status(503).body(response);
        }
    }

}
