package com.finmates.admin.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.OutputStream;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;

import javax.net.ssl.SSLException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.keycloak.admin.client.Keycloak;
import org.springframework.test.util.ReflectionTestUtils;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.tls.HandshakeCertificates;
import okhttp3.tls.HeldCertificate;

/**
 * Proves the Keycloak admin client (which carries the admin username/password) verifies TLS in every
 * profile: a Keycloak served over HTTPS with a self-signed certificate is rejected by the default
 * truststore, accepted only when that exact certificate is added via
 * {@code finmates.security.jwks.trust-store*} (additive to the JVM default anchors), and still rejected on a host
 * name mismatch.
 * The client is built through {@link KeycloakAdminProvider#newKeycloakAdminClient()} exactly as in production.
 */
class KeycloakAdminProviderTlsTest {

    private static final String TOKEN_JSON =
            "{\"access_token\":\"test-admin-token\",\"expires_in\":300,\"refresh_expires_in\":0,\"token_type\":\"Bearer\"}";

    @TempDir
    Path tmp;

    private MockWebServer server;

    @AfterEach
    void stop() throws Exception {
        if (server != null) {
            server.shutdown();
        }
    }

    /** HTTPS "Keycloak" on 127.0.0.1 presenting the given (self-signed) certificate. */
    private String startKeycloak(HeldCertificate cert) throws Exception {
        HandshakeCertificates serverCerts = new HandshakeCertificates.Builder().heldCertificate(cert).build();
        server = new MockWebServer();
        server.useHttps(serverCerts.sslSocketFactory(), false);
        server.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                String path = request.getPath();
                if (path != null && path.contains("/protocol/openid-connect/token")) {
                    return new MockResponse().setHeader("Content-Type", "application/json").setBody(TOKEN_JSON);
                }
                return new MockResponse().setHeader("Content-Type", "application/json").setBody("[]");
            }
        });
        server.start(InetAddress.getByName("127.0.0.1"), 0);
        return "https://127.0.0.1:" + server.getPort();
    }

    private static HeldCertificate selfSigned(String san) {
        return new HeldCertificate.Builder().commonName(san).addSubjectAlternativeName(san).build();
    }

    private Path trustStoreWith(HeldCertificate cert, String password) throws Exception {
        KeyStore ks = KeyStore.getInstance("PKCS12");
        ks.load(null, null);
        ks.setCertificateEntry("keycloak", cert.certificate());
        Path file = tmp.resolve("keycloak-truststore.p12");
        try (OutputStream out = Files.newOutputStream(file)) {
            ks.store(out, password.toCharArray());
        }
        return file;
    }

    /** The production provider, wired as Spring would (fields + @PostConstruct). */
    private static KeycloakAdminProvider provider(String serverUrl, String trustStore, String password) {
        KeycloakAdminProvider p = new KeycloakAdminProvider();
        ReflectionTestUtils.setField(p, "authServerUrl", serverUrl);
        ReflectionTestUtils.setField(p, "realm", "finmates");
        ReflectionTestUtils.setField(p, "adminUsername", "admin");
        ReflectionTestUtils.setField(p, "adminPassword", "secret-admin-password");
        ReflectionTestUtils.setField(p, "trustStore", trustStore);
        ReflectionTestUtils.setField(p, "trustStorePassword", password);
        ReflectionTestUtils.setField(p, "trustStoreType", "PKCS12");
        p.init();
        return p;
    }

    private static boolean causedBySslFailure(Throwable t) {
        for (; t != null; t = t.getCause()) {
            if (t instanceof SSLException) {
                return true;
            }
        }
        return false;
    }

    @Test
    void defaultTruststoreRejectsUntrustedSelfSignedKeycloak() throws Exception {
        String url = startKeycloak(selfSigned("127.0.0.1"));
        Keycloak kc = provider(url, "", "").newKeycloakAdminClient();

        assertThatThrownBy(() -> kc.realms().findAll())
                .satisfies(e -> assertThat(causedBySslFailure(e)).as("TLS failure in cause chain: %s", e).isTrue());
        assertThat(server.getRequestCount()).as("admin credentials must never reach an untrusted server").isZero();
    }

    @Test
    void customTruststoreWithTheServerCertificateAccepts() throws Exception {
        HeldCertificate cert = selfSigned("127.0.0.1");
        String url = startKeycloak(cert);
        Path trustStore = trustStoreWith(cert, "changeit");

        Keycloak kc = provider(url, trustStore.toString(), "changeit").newKeycloakAdminClient();

        assertThat(kc.realms().findAll()).isEmpty();
        assertThat(server.getRequestCount()).isGreaterThanOrEqualTo(2); // token + admin call
    }

    @Test
    void customTruststoreStillVerifiesTheHostName() throws Exception {
        HeldCertificate cert = selfSigned("some-other-host.example"); // trusted, but wrong name for 127.0.0.1
        String url = startKeycloak(cert);
        Path trustStore = trustStoreWith(cert, "changeit");

        Keycloak kc = provider(url, trustStore.toString(), "changeit").newKeycloakAdminClient();

        assertThatThrownBy(() -> kc.realms().findAll())
                .satisfies(e -> assertThat(causedBySslFailure(e)).as("TLS failure in cause chain: %s", e).isTrue());
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void customTruststoreDoesNotTrustAnUnrelatedCertificate() throws Exception {
        String url = startKeycloak(selfSigned("127.0.0.1"));
        Path trustStore = trustStoreWith(selfSigned("127.0.0.1"), "changeit"); // a different cert

        Keycloak kc = provider(url, trustStore.toString(), "changeit").newKeycloakAdminClient();

        assertThatThrownBy(() -> kc.realms().findAll()).isNotNull();
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void unreadableTruststorePathFailsTheBoot() {
        String missing = tmp.resolve("missing.p12").toString();
        assertThatThrownBy(() -> provider("https://127.0.0.1:1", missing, "x"))
                .isInstanceOf(IllegalStateException.class)
                .hasRootCauseInstanceOf(java.io.IOException.class);
    }
}
