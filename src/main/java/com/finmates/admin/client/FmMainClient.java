package com.finmates.admin.client;

import com.finmates.admin.dto.BanStatusResponse;
import com.finmates.admin.dto.UserBanResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Internal REST client for finmates-main (/api/internal/**).
 * Uses X-Internal-Secret header for authentication.
 */
@Slf4j
@Component
public class FmMainClient {

    private final String mainUrl;
    private final String internalSecret;
    private final RestTemplate restTemplate;

    public FmMainClient(
            @Value("${fm-admin.fm-main.base-url:http://localhost:8081}") String mainUrl,
            @Value("${fm-admin.internal-secret:dev-local-secret}") String internalSecret,
            RestTemplate restTemplate) {
        this.mainUrl = mainUrl;
        this.internalSecret = internalSecret;
        this.restTemplate = restTemplate;
    }

    // ── Ban Status ────────────────────────────────────────────────────────────

    public BanStatusResponse getBanStatus(Long userId) {
        String url = mainUrl + "/api/internal/users/" + userId + "/ban-status";
        ResponseEntity<BanStatusResponse> response = restTemplate.exchange(
                url, HttpMethod.GET, entityWithSecret(), BanStatusResponse.class);
        return response.getBody();
    }

    // ── Ban / Unban ───────────────────────────────────────────────────────────

    /**
     * Issue a ban via finmates-main internal API.
     * adminUserId is passed as bannedBy in the request body.
     */
    public UserBanResponse banUser(Long userId, String banType, String reason,
                                   Integer durationDays, Long adminUserId) {
        String url = mainUrl + "/api/internal/users/" + userId + "/ban";

        Map<String, Object> body;
        if (durationDays != null && "SUSPENSION".equalsIgnoreCase(banType)) {
            body = Map.of(
                    "banType", banType,
                    "reason", reason,
                    "durationHours", durationDays * 24,
                    "bannedBy", adminUserId
            );
        } else {
            body = Map.of(
                    "banType", banType,
                    "reason", reason,
                    "bannedBy", adminUserId
            );
        }

        HttpHeaders headers = headersWithSecret();
        headers.set("X-Admin-User-Id", String.valueOf(adminUserId));
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        ResponseEntity<UserBanResponse> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, UserBanResponse.class);
        return response.getBody();
    }

    public UserBanResponse unbanUser(Long userId, Long adminUserId) {
        String url = mainUrl + "/api/internal/users/" + userId + "/unban";

        HttpHeaders headers = headersWithSecret();
        headers.set("X-Admin-User-Id", String.valueOf(adminUserId));
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<UserBanResponse> response = restTemplate.exchange(
                url, HttpMethod.POST, entity, UserBanResponse.class);
        return response.getBody();
    }

    public List<UserBanResponse> getBanHistory(Long userId) {
        String url = mainUrl + "/api/internal/users/" + userId + "/ban-history";
        ResponseEntity<List<UserBanResponse>> response = restTemplate.exchange(
                url, HttpMethod.GET, entityWithSecret(),
                new ParameterizedTypeReference<List<UserBanResponse>>() {});
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
}
