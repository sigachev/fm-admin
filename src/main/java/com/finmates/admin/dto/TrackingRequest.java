package com.finmates.admin.dto;

/** Body of {@code PATCH /api/admin/tokens/{symbol}/tracking}. */
public record TrackingRequest(Boolean tracked) {
}
