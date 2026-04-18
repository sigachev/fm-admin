package com.finmates.admin.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Minimal DTO matching the JSON shape returned by fm-social
 * PUT /api/internal/posts/{id}/remove|restore.
 */
@Getter
@Setter
@NoArgsConstructor
public class PostContentResponse {
    private Long id;
    private String content;
    private String status;
    private Long authorId;
    private String authorUsername;
    private String createdAt;
    private String updatedAt;
}
