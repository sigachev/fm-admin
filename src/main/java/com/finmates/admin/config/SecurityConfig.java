package com.finmates.admin.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import com.finmates.admin.security.AzpReportOnlyFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestTemplate;
import com.finmates.admin.security.AzpAllowlistValidator;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    /** Comma-separated azp allowlist. See CLAUDE.md "JWT azp guard". */
    @Value("${finmates.security.azp.allowed:finmates-app}")
    private List<String> azpAllowed;

    /** false = report-only (log, never reject) for staged rollout. */
    @Value("${finmates.security.azp.enforce:true}")
    private boolean azpEnforce;

    /**
     * Optional ADDITIONAL truststore for the JWKS fetch (escape hatch; in-cluster the image's cacerts already
     * contains the pinned Keycloak cert). Unset = default JVM truststore. When set, its certificates are added to
     * the default JVM trust anchors; full chain + host name verification; this is NOT trust-all.
     */
    @Value("${finmates.security.jwks.trust-store:}")
    private String jwksTrustStore;

    @Value("${finmates.security.jwks.trust-store-password:}")
    private String jwksTrustStorePassword;

    @Value("${finmates.security.jwks.trust-store-type:PKCS12}")
    private String jwksTrustStoreType;

    private static final String JWKS_PATH = "/protocol/openid-connect/certs";

    /**
     * JwtDecoder for every profile EXCEPT {@code dev}: the JWKS is fetched with normal TLS
     * verification (default JVM truststore, plus the certificates of the optional {@code finmates.security.jwks.trust-store}),
     * including host name verification. Never trust-all here.
     */
    @Bean
    @Profile("!dev")
    public JwtDecoder jwtDecoder() throws Exception {
        return verifiedJwtDecoder(issuerUri + JWKS_PATH, issuerUri, azpAllowed, azpEnforce,
                jwksTrustStore, jwksTrustStorePassword, jwksTrustStoreType);
    }

    /**
     * {@code dev} profile ONLY: trust-all JWKS fetch for a local Keycloak with a self-signed
     * certificate. Logs a loud WARN at startup.
     */
    @Bean
    @Profile("dev")
    public JwtDecoder devTrustAllJwtDecoder() throws Exception {
        return trustAllJwtDecoder(issuerUri + JWKS_PATH, issuerUri, azpAllowed, azpEnforce);
    }

    /** dev-only composition (TLS verification disabled). */
    static NimbusJwtDecoder trustAllJwtDecoder(String jwkSetUri, String issuerUri, List<String> azpAllowed,
                                               boolean azpEnforce) throws Exception {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .restOperations(JwksTlsSupport.trustAllRestTemplateForDev())
                .build();
        return withTokenValidators(decoder, issuerUri, azpAllowed, azpEnforce);
    }

    /** Production composition for non-dev profiles (static so tests build it exactly as production does). */
    static NimbusJwtDecoder verifiedJwtDecoder(String jwkSetUri, String issuerUri, List<String> azpAllowed,
                                               boolean azpEnforce, String trustStore, String trustStorePassword,
                                               String trustStoreType) throws Exception {
        RestTemplate restTemplate = JwksTlsSupport.verifiedRestTemplate(trustStore, trustStorePassword, trustStoreType);
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri)
                .restOperations(restTemplate)
                .build();
        return withTokenValidators(decoder, issuerUri, azpAllowed, azpEnforce);
    }

    /**
     * Applies the full validator chain to a decoder: the default validators (timestamps) + issuer
     * check, plus the azp allowlist guard. Static so the wiring can be tested without a JWKS endpoint.
     */
    static NimbusJwtDecoder withTokenValidators(NimbusJwtDecoder decoder, String issuerUri,
                                                List<String> azpAllowed, boolean azpEnforce) {
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuerUri),
                new AzpAllowlistValidator(azpAllowed, azpEnforce));
        decoder.setJwtValidator(validator);
        return decoder;
    }

    /**
     * Report-only azp logging (service, method, normalised endpoint, azp - never the token). Order -50 runs
     * after Spring Security (-100) so the JWT is already authenticated. Uses the same allowlist/enforce
     * values as the decoder; a no-op when enforcement is on. See CLAUDE.md "JWT azp guard".
     */
    @Bean
    public FilterRegistrationBean<AzpReportOnlyFilter> azpReportOnlyFilter(
            @Value("${spring.application.name:fm-admin}") String serviceName) {
        FilterRegistrationBean<AzpReportOnlyFilter> registration = new FilterRegistrationBean<>(
                new AzpReportOnlyFilter(serviceName, azpAllowed, azpEnforce));
        registration.setOrder(-50);
        return registration;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/ui/**").permitAll()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .anyRequest().denyAll()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter()))
            );
        return http.build();
    }

    /**
     * Extracts roles from BOTH {@code realm_access.roles} (realm-level) and
     * {@code resource_access.<clientId>.roles} (client-level) Keycloak JWT claims.
     * Maps e.g. "admin" → ROLE_ADMIN so {@code hasRole("ADMIN")} works.
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<GrantedAuthority> authorities = new ArrayList<>();

            // 1. Realm-level roles
            Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess != null) {
                Object realmRolesObj = realmAccess.get("roles");
                if (realmRolesObj instanceof List<?> realmRoles) {
                    realmRoles.stream()
                            .map(r -> (GrantedAuthority) new SimpleGrantedAuthority(
                                    "ROLE_" + r.toString().toUpperCase()))
                            .forEach(authorities::add);
                }
            }

            // 2. Client-level roles — check ALL clients in resource_access
            Map<String, Object> resourceAccess = jwt.getClaimAsMap("resource_access");
            if (resourceAccess != null) {
                resourceAccess.forEach((clientId, clientData) -> {
                    if (clientData instanceof Map<?, ?> clientMap) {
                        Object rolesObj = clientMap.get("roles");
                        if (rolesObj instanceof List<?> clientRoles) {
                            clientRoles.stream()
                                    .map(r -> (GrantedAuthority) new SimpleGrantedAuthority(
                                            "ROLE_" + r.toString().toUpperCase()))
                                    .forEach(authorities::add);
                        }
                    }
                });
            }

            return authorities;
        });
        return converter;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(
                "http://localhost:3000",
                "https://finmates.com",
                "https://www.finmates.com"
        ));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
