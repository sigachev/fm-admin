package com.finmates.admin.config;

import lombok.extern.slf4j.Slf4j;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;

/**
 * Provides Keycloak admin client instances for fm-admin service.
 * Used for admin operations like password reset, user management, etc.
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
                .resteasyClient(trustAllResteasyClient())
                .build();
    }

    /**
     * Creates a ResteasyClient that trusts all certificates.
     * Required for development environments with self-signed certificates (e.g., auth.finmates.com).
     * DO NOT use in production without proper certificate validation.
     */
    private ResteasyClient trustAllResteasyClient() {
        try {
            TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                    public void checkClientTrusted(X509Certificate[] c, String a) {
                    }
                    public void checkServerTrusted(X509Certificate[] c, String a) {
                    }
                }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustAll, new java.security.SecureRandom());
            return ((ResteasyClientBuilder) ResteasyClientBuilder.newBuilder())
                    .sslContext(sslContext)
                    .hostnameVerifier((hostname, session) -> true)
                    .build();
        } catch (Exception e) {
            log.warn("Could not create trust-all ResteasyClient, using default", e);
            return (ResteasyClient) ResteasyClientBuilder.newClient();
        }
    }
}
