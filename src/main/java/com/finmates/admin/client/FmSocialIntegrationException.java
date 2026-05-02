package com.finmates.admin.client;

import org.springframework.http.HttpStatusCode;
import org.springframework.web.server.ResponseStatusException;

/**
 * Thrown by {@link FmSocialClient} when an upstream fm-social call returns a
 * 4xx/5xx. Extends {@link ResponseStatusException} so Spring MVC maps it to
 * the same status code on the outbound admin response, making frontend toasts
 * actionable instead of generic.
 *
 * <p>The message is the parsed upstream error body (or a fallback) — never the
 * inbound {@code X-Internal-Secret} value.
 */
public class FmSocialIntegrationException extends ResponseStatusException {

    public FmSocialIntegrationException(HttpStatusCode upstreamStatus, String message) {
        super(upstreamStatus, "fm-social: " + message);
    }
}
