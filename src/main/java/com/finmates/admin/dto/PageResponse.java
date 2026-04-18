package com.finmates.admin.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Generic page response wrapper for moderation/audit endpoints.
 * Matches the JSON shape produced by fm-social paginated endpoints:
 * { "content": [...], "page": 0, "size": 20, "totalElements": N, "totalPages": N, "last": true }
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
    public static <T> PageResponse<T> from(Page<T> springPage) {
        return new PageResponse<>(
                springPage.getContent(),
                springPage.getNumber(),
                springPage.getSize(),
                springPage.getTotalElements(),
                springPage.getTotalPages(),
                springPage.isLast()
        );
    }
}
