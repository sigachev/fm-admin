package com.finmates.admin.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.List;

/**
 * Read-only access to {@code pg_stat_activity} for the services dashboard.
 *
 * <p>fm-admin's DB role is superuser, so the view returns rows for every
 * database — including {@code main}, {@code crypto}, {@code crypto_data},
 * {@code messaging}, {@code social}, {@code trading}, {@code uniswap}, and
 * {@code keycloak}. Runs ONE query per dashboard refresh.
 *
 * <p>Query mirrors the connection-pressure diagnostic documented in the project
 * CLAUDE.md.
 */
@Service
public class PgConnectionService {

    private static final Logger log = LoggerFactory.getLogger(PgConnectionService.class);

    private static final String QUERY = """
            SELECT datname,
                   client_addr,
                   application_name,
                   state,
                   count(*) AS conns
            FROM pg_stat_activity
            WHERE datname IS NOT NULL
            GROUP BY datname, client_addr, application_name, state
            ORDER BY conns DESC
            """;

    private final JdbcTemplate jdbcTemplate;

    public PgConnectionService(@Qualifier("mainDataSource") DataSource mainDataSource) {
        this.jdbcTemplate = new JdbcTemplate(mainDataSource);
    }

    public List<PgActivityRow> fetchActivity() {
        try {
            return jdbcTemplate.query(QUERY, (rs, rowNum) -> new PgActivityRow(
                    rs.getString("datname"),
                    rs.getString("client_addr"),
                    rs.getString("application_name"),
                    rs.getString("state"),
                    rs.getInt("conns")
            ));
        } catch (RuntimeException e) {
            // Don't poison the dashboard if pg_stat_activity errors transiently; return empty
            // so callers degrade honestly (cluster panel will be empty rather than 500).
            log.warn("pg_stat_activity query failed: {}", e.toString());
            return List.of();
        }
    }

    public record PgActivityRow(
            String datname,
            String clientAddr,
            String applicationName,
            String state,
            int conns
    ) {
    }
}
