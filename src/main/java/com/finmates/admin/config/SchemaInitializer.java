package com.finmates.admin.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/**
 * Ensures required schema changes exist in the main DB.
 * Executes one-time migrations that should have been applied by finmates-main.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SchemaInitializer {

    private final DataSource mainDataSource;

    @PostConstruct
    public void init() {
        try {
            addContentColumnToAdminNews();
        } catch (Exception e) {
            log.error("Schema initialization failed: {}", e.getMessage());
            // Don't fail startup — column might already exist
        }
    }

    private void addContentColumnToAdminNews() throws Exception {
        try (Connection conn = mainDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE admin_news ADD COLUMN IF NOT EXISTS content TEXT;");
            log.info("Ensured admin_news.content column exists");
        }
    }
}
