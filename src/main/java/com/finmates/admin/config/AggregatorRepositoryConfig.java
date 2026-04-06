package com.finmates.admin.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA Repository configuration for aggregator datasource.
 * Scans repository.aggregator package for aggregator-related repositories.
 */
@Configuration
@EnableJpaRepositories(
        basePackages = "com.finmates.admin.repository.aggregator",
        entityManagerFactoryRef = "aggregatorEntityManagerFactory",
        transactionManagerRef = "aggregatorTransactionManager"
)
public class AggregatorRepositoryConfig {
}
