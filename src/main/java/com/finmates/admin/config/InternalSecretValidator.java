package com.finmates.admin.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

/**
 * Fail-fast startup validator for {@code fm-admin.internal-secret}.
 *
 * <p>Mirrors fm-social's {@code InternalSecretValidator}. The shared secret is the
 * sole authentication mechanism for outbound calls into fm-social's
 * {@code /api/internal/**} endpoints (sent as {@code X-Internal-Secret} by
 * {@code FmSocialClient}). If the value is missing or equals the dev fallback in
 * a non-dev profile, the application refuses to start instead of letting every
 * outbound call silently 401.
 *
 * <p>Never logs the secret value — only its length.
 */
@Slf4j
@Component
public class InternalSecretValidator {

    static final String DEV_FALLBACK = "dev-local-secret";
    private static final Set<String> NON_DEV_PROFILES = Set.of("k8s", "prod", "production", "aws");

    @Value("${fm-admin.internal-secret:}")
    private String internalSecret;

    private final Environment environment;

    public InternalSecretValidator(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        String[] activeProfiles = environment.getActiveProfiles();
        String profileLabel = activeProfiles.length == 0 ? "default" : String.join(",", activeProfiles);
        boolean isNonDev = Arrays.stream(activeProfiles).anyMatch(NON_DEV_PROFILES::contains);

        boolean blank = internalSecret == null || internalSecret.isBlank();
        boolean isDevFallback = DEV_FALLBACK.equals(internalSecret);

        if (isNonDev && (blank || isDevFallback)) {
            String reason = blank
                    ? "fm-admin.internal-secret is not set (env var INTERNAL_SHARED_SECRET missing?)"
                    : "fm-admin.internal-secret equals the known dev fallback '" + DEV_FALLBACK
                            + "' — refusing to start in profile=" + profileLabel;
            log.error("[InternalSecretValidator] STARTUP REFUSED: {}", reason);
            throw new IllegalStateException(reason);
        }

        if (blank) {
            log.warn("[InternalSecretValidator] fm-admin.internal-secret is blank (profile={}). "
                    + "All FmSocialClient calls will 401.", profileLabel);
            return;
        }

        log.info("[InternalSecretValidator] Internal shared secret configured (length={}, profile={})",
                internalSecret.length(), profileLabel);
    }
}
