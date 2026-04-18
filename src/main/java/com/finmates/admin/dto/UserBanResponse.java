package com.finmates.admin.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO matching the JSON shape returned by finmates-main
 * /api/internal/users/{id}/ban and /ban-history.
 */
@Getter
@Setter
@NoArgsConstructor
public class UserBanResponse {
    private Long id;
    private Long userId;
    private String banType;
    private String reason;
    private Long bannedBy;
    private String expiresAt;
    private boolean active;
    private String liftedAt;
    private Long liftedBy;
    private String createdAt;
}
