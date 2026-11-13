package com.finmates.admin.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.security.GeneralSecurityException;

/**
 * Provides Keycloak admin client instances for fm-admin service.
 * Used for admin operations like password reset, user management, etc.
 *
 * <p><b>TLS:</b> this client carries the Keycloak ADMIN username/password, so the server certificate
 * chain AND host name are verified in EVERY profile (no dev exception). {@code auth.finmates.com}
 * serves the public GlobalSign AlphaSSL wildcard, so the default JVM truststore is correct. Optional
 * escape hatch (same properties as the JWKS fetch): {@code finmates.security.jwks.trust-store},
 * {@code .trust-store-password}, {@code .trust-store-type} - when set, that truststore replaces the
 * default one for this client (chain + host name are still verified; this is NOT trust-all). A set but
 * unreadable truststore path fails the boot.
 */
@Slf4j
@Component
public class KeycloakAdminProvider {

    @Value("${keycloak.auth-server-url}")
    private String authServerUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.admin.username}")
    private String adminUsername;

    @Value("${keycloak.admin.password}")
    private String adminPassword;

    @Value("${finmates.security.jwks.trust-store:}")
    private String trustStore;

    @Value("${finmates.security.jwks.trust-store-password:}")
    private String trustStorePassword;

    @Value("${finmates.security.jwks.trust-store-type:PKCS12}")
    private String trustStoreType;

    /** null = default JVM truststore. */
    private SSLContext customSslContext;

    /** Loads the optional truststore once; a set-but-unreadable path aborts startup. */
    @PostConstruct
    void init() {
        try {
            customSslContext = JwksTlsSupport.customTrustStoreSslContext(trustStore, trustStorePassword, trustStoreType);
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException(
                    "Cannot load Keycloak admin client truststore finmates.security.jwks.trust-store=" + trustStore, e);
        }
    }

    /**
     * Creates a Keycloak admin client instance authenticated with admin credentials.
     * Authenticates against the master realm using admin-cli client.
     *
     * @return Keycloak admin client instance
     */
    public Keycloak newKeycloakAdminClient() {
        return KeycloakBuilder.builder()
                .serverUrl(authServerUrl)
                .realm("master")
                .clientId("admin-cli")
                .username(adminUsername)
                .password(adminPassword)
                .resteasyClient(verifiedResteasyClient(customSslContext))
                .build();
    }

    /**
     * RESTEasy client with normal TLS verification: certificate chain + host name. A {@code null}
     * {@code sslContext} means the default JVM truststore. Never installs a trust-all manager or a
     * permissive host name verifier.
     */
    static ResteasyClient verifiedResteasyClient(SSLContext sslContext) {
        ResteasyClientBuilder builder = (ResteasyClientBuilder) ResteasyClientBuilder.newBuilder();
        if (sslContext != null) {
            builder.sslContext(sslContext);
        }
        return builder.build();
    }
}
