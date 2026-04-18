package com.finmates.admin.dto;

/**
 * Request body for POST /api/admin/content/posts|comments/{id}/remove.
 */
public record RemoveContentRequest(String reason) {}
