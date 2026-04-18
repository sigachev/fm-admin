package com.finmates.admin.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finmates.admin.dto.CommentContentResponse;
import com.finmates.admin.dto.PostContentResponse;
import com.finmates.admin.dto.ReportDetailResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
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
     */
    public ReportDetailResponse resolveReport(Long reportId, String status,
                                               String resolutionAction, String notes,
                                               Long adminUserId) {
        String url = socialUrl + "/api/internal/reports/" + reportId + "/resolve";

        Map<String, Object> body = Map.of(
                "status", status,
                "resolutionAction", resolutionAction != null ? resolutionAction : "",
                "notes", notes != null ? notes : ""
        );

        HttpHeaders headers = headersWithSecret();
        headers.set("X-Admin-User-Id", String.valueOf(adminUserId));
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<ReportDetailResponse> response = restTemplate.exchange(
                url, HttpMethod.PUT, entity, ReportDetailResponse.class);
        return response.getBody();
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
}
