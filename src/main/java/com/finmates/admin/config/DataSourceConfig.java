package com.finmates.admin.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import javax.sql.DataSource;
import java.util.Map;
import java.util.logging.Logger;
import jakarta.annotation.PreDestroy;

@Configuration
@EnableTransactionManagement
public class DataSourceConfig {

    private static final Logger logger = Logger.getLogger(DataSourceConfig.class.getName());

    // Active Spring profile — controls ddl-auto for the main datasource only.
    // Dev follows finmates-main's pattern (Hibernate auto-evolves the schema, no Flyway on main).
    // Non-dev profiles use 'validate' to fail-fast on schema drift.
    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    private String mainDdlAuto() {
        return "dev".equalsIgnoreCase(activeProfile) ? "update" : "validate";
    }

    // ── DataSource references for graceful shutdown ────────────────────────────────
    private DataSource mainDataSourceRef;
    private DataSource cryptoDataSourceRef;
    private DataSource aggregatorDataSourceRef;

    // ── RestTemplate for HTTP calls (e.g., AggregatorClient) ────────────────────────
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // Set generous timeouts for aggregator service
        // Token discovery returns large payloads (600+ tokens) which takes time to deserialize
        factory.setConnectTimeout(10000);      // 10 second connection timeout
        factory.setReadTimeout(150000);        // 150 second read timeout — discovery fetches 5 exchanges (30s each) + DB upserts (~1800 rows)
        return new RestTemplate(factory);
    }

    // ── healthRestTemplate: short-timeout client for the services-status dashboard ──
    // Must be injected via @Qualifier("healthRestTemplate") — never the shared bean
    // above, or one hung service stalls the whole /api/admin/services/status response.
    @Bean(name = "healthRestTemplate")
    public RestTemplate healthRestTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(5000);
        return new RestTemplate(factory);
    }

    // ── EntityManagerFactoryBuilder (manual — HibernateJpaAutoConfiguration excluded) ──

    @Bean
    public EntityManagerFactoryBuilder entityManagerFactoryBuilder() {
        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        vendorAdapter.setShowSql(false);
        vendorAdapter.setGenerateDdl(false);
        return new EntityManagerFactoryBuilder(vendorAdapter, Map.of(
                "hibernate.hbm2ddl.auto", "none",
                "hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect",
                // Converts camelCase field names to snake_case column names (firstName → first_name)
                "hibernate.physical_naming_strategy",
                    "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"
        ), null);
    }

    // ── Main datasource (finmates DB — users, admin_news) ────────────────────────────

    @Primary
    @Bean("mainDataSource")
    @ConfigurationProperties(prefix = "datasource.main")
    public DataSource mainDataSource() {
        HikariDataSource ds = DataSourceBuilder.create().type(HikariDataSource.class).build();
        applyAdminPoolSizing(ds, "Main");
        mainDataSourceRef = ds;
        return ds;
    }

    @Primary
    @Bean("mainEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean mainEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("mainDataSource") DataSource dataSource) {
        // Override ddl-auto for the main datasource only.
        // Dev: 'update' so Hibernate auto-creates Hibernate-owned tables (e.g. audit_log) — finmates-main
        //      follows the same convention; main DB has no Flyway. Other profiles: 'validate'.
        // Crypto and aggregator EMFs continue to use the builder's default 'none'.
        String ddlAuto = mainDdlAuto();
        logger.info("[MainEntityManagerFactory] hibernate.hbm2ddl.auto = " + ddlAuto + " (profile: " + activeProfile + ")");
        return builder
                .dataSource(dataSource)
                .packages("com.finmates.admin.entity.main")
                .persistenceUnit("main")
                .properties(Map.of("hibernate.hbm2ddl.auto", ddlAuto))
                .build();
    }

    @Primary
    @Bean("mainTransactionManager")
    public PlatformTransactionManager mainTransactionManager(
            @Qualifier("mainEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }

    // ── Crypto datasource (crypto DB — portfolios, trades, positions) ─────────────────

    @Bean("cryptoDataSource")
    @ConfigurationProperties(prefix = "datasource.crypto")
    public DataSource cryptoDataSource() {
        HikariDataSource ds = DataSourceBuilder.create().type(HikariDataSource.class).build();
        applyAdminPoolSizing(ds, "Crypto");
        cryptoDataSourceRef = ds;
        return ds;
    }

    @Bean("cryptoEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean cryptoEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("cryptoDataSource") DataSource dataSource) {
        return builder
                .dataSource(dataSource)
                .packages("com.finmates.admin.entity.crypto")
                .persistenceUnit("crypto")
                .build();
    }

    @Bean("cryptoTransactionManager")
    public PlatformTransactionManager cryptoTransactionManager(
            @Qualifier("cryptoEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }

    // ── Aggregator datasource (crypto_data DB — assets, prices) ─────────────────

    @Bean("aggregatorDataSource")
    @ConfigurationProperties(prefix = "datasource.aggregator")
    public DataSource aggregatorDataSource() {
        HikariDataSource ds = DataSourceBuilder.create().type(HikariDataSource.class).build();
        applyAdminPoolSizing(ds, "Aggregator");
        aggregatorDataSourceRef = ds;
        return ds;
    }

    /**
     * Set HikariCP sizing programmatically on each datasource.
     *
     * Why not properties? The three datasources are built via
     * {@code DataSourceBuilder.create().build()} with
     * {@code @ConfigurationProperties(prefix="datasource.{ds}")}. Spring's relaxed
     * binding on that prefix only navigates HikariDataSource's top-level setters
     * (jdbcUrl, username, password, driverClassName). There is no
     * {@code getHikari()} accessor on HikariDataSource, so a nested
     * {@code datasource.{ds}.hikari.maximum-pool-size} key has no binding target —
     * Spring silently ignores it (verified after commit aaa4d55: the JAR contained
     * the nested config and admin-panel still held 10/10/10).
     *
     * Programmatic sizing here is the single source of truth: visible in code,
     * applied before Spring's prefix binding (which only touches the connection
     * fields above), and immune to future property-binding regressions.
     *
     * fm-admin is low-traffic; 3 connections per DS is generous. min-idle=0 lets
     * pools shrink fully between requests at the cost of a cold-connection round
     * trip on the first request after idle — acceptable for an admin panel.
     */
    private void applyAdminPoolSizing(HikariDataSource ds, String label) {
        ds.setMaximumPoolSize(3);
        ds.setMinimumIdle(0);
        ds.setConnectionTimeout(10000);
        ds.setIdleTimeout(600000);
        ds.setMaxLifetime(1800000);
        logger.info("[" + label + "DataSource] HikariCP pool initialized (max=3, min-idle=0)");
    }

    @Bean("aggregatorEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean aggregatorEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("aggregatorDataSource") DataSource dataSource) {
        return builder
                .dataSource(dataSource)
                .packages("com.finmates.admin.entity.aggregator")
                .persistenceUnit("aggregator")
                .build();
    }

    @Bean("aggregatorTransactionManager")
    public PlatformTransactionManager aggregatorTransactionManager(
            @Qualifier("aggregatorEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }

    // ── Graceful shutdown: close all datasource pools ────────────────────────────────
    @PreDestroy
    public void closeDataSources() {
        logger.info("[DataSourceConfig] Graceful shutdown: closing all datasource pools");

        // Close main datasource
        if (mainDataSourceRef instanceof com.zaxxer.hikari.HikariDataSource) {
            try {
                ((com.zaxxer.hikari.HikariDataSource) mainDataSourceRef).close();
                logger.info("[MainDataSource] HikariCP pool closed successfully");
            } catch (Exception e) {
                logger.warning("[MainDataSource] Error closing pool: " + e.getMessage());
            }
        }

        // Close crypto datasource
        if (cryptoDataSourceRef instanceof com.zaxxer.hikari.HikariDataSource) {
            try {
                ((com.zaxxer.hikari.HikariDataSource) cryptoDataSourceRef).close();
                logger.info("[CryptoDataSource] HikariCP pool closed successfully");
            } catch (Exception e) {
                logger.warning("[CryptoDataSource] Error closing pool: " + e.getMessage());
            }
        }

        // Close aggregator datasource
        if (aggregatorDataSourceRef instanceof com.zaxxer.hikari.HikariDataSource) {
            try {
                ((com.zaxxer.hikari.HikariDataSource) aggregatorDataSourceRef).close();
                logger.info("[AggregatorDataSource] HikariCP pool closed successfully");
            } catch (Exception e) {
                logger.warning("[AggregatorDataSource] Error closing pool: " + e.getMessage());
            }
        }
    }
}
