package com.finmates.admin.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
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
        DataSource ds = DataSourceBuilder.create().build();
        mainDataSourceRef = ds;
        logger.info("[MainDataSource] HikariCP pool initialized");
        return ds;
    }

    @Primary
    @Bean("mainEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean mainEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("mainDataSource") DataSource dataSource) {
        return builder
                .dataSource(dataSource)
                .packages("com.finmates.admin.entity.main")
                .persistenceUnit("main")
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
        DataSource ds = DataSourceBuilder.create().build();
        cryptoDataSourceRef = ds;
        logger.info("[CryptoDataSource] HikariCP pool initialized");
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
        DataSource ds = DataSourceBuilder.create().build();
        aggregatorDataSourceRef = ds;
        logger.info("[AggregatorDataSource] HikariCP pool initialized");
        return ds;
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
