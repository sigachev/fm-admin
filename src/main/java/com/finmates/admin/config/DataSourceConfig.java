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
import javax.sql.DataSource;
import java.util.Map;

@Configuration
@EnableTransactionManagement
public class DataSourceConfig {

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
        return DataSourceBuilder.create().build();
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
        return DataSourceBuilder.create().build();
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
}
