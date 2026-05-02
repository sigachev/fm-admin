package com.finmates.admin.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finmates.admin.client.dto.ResolutionAction;
import com.finmates.admin.dto.CommentContentResponse;
import com.finmates.admin.dto.PostContentResponse;
import com.finmates.admin.dto.ReportDetailResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Internal REST client for fm-social (/api/internal/**).
 * Uses X-Internal-Secret header for authentication.
 * All methods throw RuntimeException on unexpected HTTP errors so callers
 * can handle or surface them as appropriate HTTP responses.
 */
@Slf4j
@Component
public class FmSocialClient {

    private final String socialUrl;
    private final String internalSecret;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public FmSocialClient(
            @Value("${fm-admin.fm-social.base-url:http://localhost:8091}") String socialUrl,
            @Value("${fm-admin.internal-secret:dev-local-secret}") String internalSecret,
            RestTemplate restTemplate,
            ObjectMapper objectMapper) {
        this.socialUrl = socialUrl;
        this.internalSecret = internalSecret;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        // One-time startup log. Length only — never the value.
        log.info("FmSocialClient initialized: baseUrl={}, internalSecretLength={}",
                socialUrl, internalSecret == null ? 0 : internalSecret.length());
    }

    // ── Reports ──────────────────────────────────────────────────────────────

    /**
     * List reports from fm-social with optional status filter.
     * Returns a raw Map representing the Page JSON for pass-through to admin UI.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> listReports(String status, String reason, int page, int size) {
        StringBuilder url = new StringBuilder(socialUrl)
                .append("/api/internal/reports?page=").append(page)
                .append("&size=").append(size);
        if (status != null && !status.isBlank()) {
            url.append("&status=").append(status);
        }
        if (reason != null && !reason.isBlank()) {
            url.append("&reason=").append(reason);
        }

        ResponseEntity<Map> response = restTemplate.exchange(
                url.toString(), HttpMethod.GET, entityWithSecret(), Map.class);
        return response.getBody();
    }

    public ReportDetailResponse getReport(Long id) {
        String url = socialUrl + "/api/internal/reports/" + id;
        ResponseEntity<ReportDetailResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, entityWithSecret(), ReportDetailResponse.class);
        return response.getBody();
    }

    /**
     * Resolve a report in fm-social.
     * Sends status + resolutionAction to the internal endpoint.
     * X-Admin-User-Id is passed so fm-social records who resolved it.
     *
     * <p>{@code resolutionAction} is typed as the {@link ResolutionAction} enum so
     * the wire value is always one of fm-social's accepted values
     * (CONTENT_REMOVED, USER_WARNED, USER_SUSPENDED, USER_BANNED, NO_ACTION).
     * Passing arbitrary strings used to cause silent 400s from Jackson.
     */
    public ReportDetailResponse resolveReport(Long reportId, String status,
                                               ResolutionAction resolutionAction, String notes,
                                               Long adminUserId) {
        String url = socialUrl + "/api/internal/reports/" + reportId + "/resolve";

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("status", status);
        body.put("resolutionAction", resolutionAction);
        body.put("notes", notes != null ? notes : "");

        HttpHeaders headers = headersWithSecret();
        headers.set("X-Admin-User-Id", String.valueOf(adminUserId));
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<ReportDetailResponse> response = restTemplate.exchange(
                    url, HttpMethod.PUT, entity, ReportDetailResponse.class);
            return response.getBody();
        } catch (HttpStatusCodeException e) {
            String responseBody = e.getResponseBodyAsString();
            String requestBodyJson;
            try {
                requestBodyJson = objectMapper.writeValueAsString(body);
            } catch (Exception ex) {
                requestBodyJson = body.toString();
            }
            log.error("fm-social resolveReport failed: status={} url={} requestBody={} responseBody={}",
                    e.getStatusCode(), url, requestBodyJson, responseBody);
            throw new FmSocialIntegrationException(e.getStatusCode(),
                    parseErrorMessage(responseBody, e.getStatusCode().toString()));
        }
    }

    // ── Posts ──────────────────────────────────────────────────────────────

    public PostContentResponse getPostPreview(Long postId) {
        String url = socialUrl + "/api/internal/posts/" + postId;
        ResponseEntity<PostContentResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, entityWithSecret(), PostContentResponse.class);
        return response.getBody();
    }

    public PostContentResponse removePost(Long postId, Long adminUserId, String reason) {
        String url = socialUrl + "/api/internal/posts/" + postId + "/remove"
                + (reason != null ? "?reason=" + encode(reason) : "");

        HttpHeaders headers = headersWithSecret();
        headers.set("X-Admin-User-Id", String.valueOf(adminUserId));
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<PostContentResponse> response = restTemplate.exchange(
                url, HttpMethod.PUT, entity, PostContentResponse.class);
        return response.getBody();
    }

    public PostContentResponse restorePost(Long postId, Long adminUserId) {
        String url = socialUrl + "/api/internal/posts/" + postId + "/restore";

        HttpHeaders headers = headersWithSecret();
        headers.set("X-Admin-User-Id", String.valueOf(adminUserId));
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<PostContentResponse> response = restTemplate.exchange(
                url, HttpMethod.PUT, entity, PostContentResponse.class);
        return response.getBody();
    }

    // ── Comments ─────────────────────────────────────────────────────────────

    public CommentContentResponse getCommentPreview(Long commentId) {
        String url = socialUrl + "/api/internal/comments/" + commentId;
        ResponseEntity<CommentContentResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, entityWithSecret(), CommentContentResponse.class);
        return response.getBody();
    }

    public CommentContentResponse removeComment(Long commentId, Long adminUserId, String reason) {
        String url = socialUrl + "/api/internal/comments/" + commentId + "/remove"
                + (reason != null ? "?reason=" + encode(reason) : "");

        HttpHeaders headers = headersWithSecret();
        headers.set("X-Admin-User-Id", String.valueOf(adminUserId));
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<CommentContentResponse> response = restTemplate.exchange(
                url, HttpMethod.PUT, entity, CommentContentResponse.class);
        return response.getBody();
    }

    public CommentContentResponse restoreComment(Long commentId, Long adminUserId) {
        String url = socialUrl + "/api/internal/comments/" + commentId + "/restore";

        HttpHeaders headers = headersWithSecret();
        headers.set("X-Admin-User-Id", String.valueOf(adminUserId));
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<CommentContentResponse> response = restTemplate.exchange(
                url, HttpMethod.PUT, entity, CommentContentResponse.class);
        return response.getBody();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private HttpEntity<Void> entityWithSecret() {
        return new HttpEntity<>(headersWithSecret());
    }

    private HttpHeaders headersWithSecret() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Internal-Secret", internalSecret);
        return headers;
    }

    private String encode(String value) {
        try {
            return java.net.URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    /**
     * Best-effort extract a human-readable message from an upstream JSON error body.
     * Falls back to the raw body or status text if the body isn't a recognised shape.
     */
    @SuppressWarnings("unchecked")
    private String parseErrorMessage(String responseBody, String statusFallback) {
        if (responseBody == null || responseBody.isBlank()) {
            return statusFallback;
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(responseBody, Map.class);
            for (String key : List.of("message", "error", "detail", "hint")) {
                Object value = parsed.get(key);
                if (value != null && !value.toString().isBlank()) {
                    return value.toString();
                }
            }
        } catch (Exception ignored) {
            // Body wasn't JSON — fall through.
        }
        // Truncate raw body so we don't blow up downstream toasts.
        return responseBody.length() > 300 ? responseBody.substring(0, 300) + "…" : responseBody;
    }
}
