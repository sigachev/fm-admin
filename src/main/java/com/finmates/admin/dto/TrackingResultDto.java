package com.finmates.admin.dto;

import java.util.List;

/**
 * Result of turning tracking on/off.
 *
 * @param token            the token after the change, with its recomputed status
 * @param autoEnabledFeeds exchanges switched on automatically because tracking was turned on with no feed enabled
 *                         (empty when feeds already existed, or when tracking was turned off)
 */
public record TrackingResultDto(AdminTokenDto token, List<String> autoEnabledFeeds) {
}
