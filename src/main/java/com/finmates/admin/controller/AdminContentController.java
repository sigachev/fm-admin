package com.finmates.admin.controller;

import com.finmates.admin.dto.CommentContentResponse;
import com.finmates.admin.dto.PostContentResponse;
import com.finmates.admin.dto.RemoveContentRequest;
import com.finmates.admin.service.AdminModerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

/**
 * Direct content moderation endpoints — remove/restore posts and comments
 * without going through the report queue.
 */
@RestController
@RequestMapping("/api/admin/content")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminContentController {

    private final AdminModerationService moderationService;

    @GetMapping("/posts/{id}/preview")
    public PostContentResponse getPostPreview(@PathVariable Long id) {
        return moderationService.getPostPreview(id);
    }

    @GetMapping("/comments/{id}/preview")
    public CommentContentResponse getCommentPreview(@PathVariable Long id) {
        return moderationService.getCommentPreview(id);
    }

    @PostMapping("/posts/{id}/remove")
    public PostContentResponse removePost(
            @PathVariable Long id,
            @RequestBody RemoveContentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return moderationService.removePost(id, request, jwt);
    }

    @PostMapping("/posts/{id}/restore")
    public PostContentResponse restorePost(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        return moderationService.restorePost(id, jwt);
    }

    @PostMapping("/comments/{id}/remove")
    public CommentContentResponse removeComment(
            @PathVariable Long id,
            @RequestBody RemoveContentRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return moderationService.removeComment(id, request, jwt);
    }

    @PostMapping("/comments/{id}/restore")
    public CommentContentResponse restoreComment(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        return moderationService.restoreComment(id, jwt);
    }
}
