package com.finmates.admin.controller;

import com.finmates.admin.dto.*;
import com.finmates.admin.service.AdminKeycloakService;
import com.finmates.admin.service.AdminModerationService;
import com.finmates.admin.service.AdminUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService userService;
    private final AdminKeycloakService keycloakService;
    private final AdminModerationService moderationService;

    @GetMapping
    public Page<AdminUserDto> listUsers(
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return userService.listUsers(search, pageable);
    }

    @GetMapping("/{id}")
    public AdminUserDetailDto getUser(@PathVariable Long id) {
        return userService.getUser(id);
    }

    @PutMapping("/{id}/enabled")
    public AdminUserDto toggleEnabled(
            @PathVariable Long id,
            @RequestBody Map<String, Boolean> body) {
        boolean enabled = Boolean.TRUE.equals(body.get("enabled"));
        return userService.toggleEnabled(id, enabled);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/password")
    public ResponseEntity<Map<String, String>> changePassword(
            @PathVariable Long id,
            @RequestBody ChangePasswordRequest request) {
        // Load user to get keycloakId
        AdminUserDetailDto user = userService.getUser(id);
        String keycloakId = user.getKeycloakId();

        if (keycloakId == null || keycloakId.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "User has no Keycloak ID (registration incomplete)"));
        }

        // Change password via Keycloak
        keycloakService.changeUserPassword(keycloakId, request.getNewPassword());

        return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
    }

    // ── Ban management ────────────────────────────────────────────────────────

    @PostMapping("/{id}/ban")
    @ResponseStatus(HttpStatus.CREATED)
    public UserBanResponse banUser(
            @PathVariable Long id,
            @RequestBody BanUserRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return moderationService.banUser(id, request, jwt);
    }

    @PostMapping("/{id}/unban")
    public UserBanResponse unbanUser(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        return moderationService.unbanUser(id, jwt);
    }

    @GetMapping("/{id}/ban-history")
    public List<UserBanResponse> banHistory(@PathVariable Long id) {
        return moderationService.getBanHistory(id);
    }

    @GetMapping("/{id}/ban-status")
    public BanStatusResponse banStatus(@PathVariable Long id) {
        return moderationService.getBanStatus(id);
    }
}
