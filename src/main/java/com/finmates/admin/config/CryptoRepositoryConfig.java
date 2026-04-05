package com.finmates.admin.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EnableJpaRepositories(
        basePackages = "com.finmates.admin.repository.crypto",
        entityManagerFactoryRef = "cryptoEntityManagerFactory",
        transactionManagerRef = "cryptoTransactionManager"
)
public class CryptoRepositoryConfig {
}
