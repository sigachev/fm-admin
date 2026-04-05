package com.finmates.admin.controller;

import com.finmates.admin.dto.AdminNewsDto;
import com.finmates.admin.dto.CreateNewsRequest;
import com.finmates.admin.service.AdminNewsService;
import jakarta.validation.Valid;
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

@RestController
@RequestMapping("/api/admin/news")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class AdminNewsController {

    private final AdminNewsService newsService;

    @GetMapping
    public Page<AdminNewsDto> listNews(@PageableDefault(size = 20) Pageable pageable) {
        return newsService.listNews(pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminNewsDto createNews(
            @Valid @RequestBody CreateNewsRequest req,
            @AuthenticationPrincipal Jwt jwt) {
        Long createdBy = extractUserId(jwt);
        return newsService.create(req, createdBy);
    }

    @PutMapping("/{id}")
    public AdminNewsDto updateNews(
            @PathVariable Long id,
            @RequestBody CreateNewsRequest req) {
        return newsService.update(id, req);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteNews(@PathVariable Long id) {
        newsService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** Extracts the internal user ID stored in the JWT 'sub' or a custom claim. */
    private Long extractUserId(Jwt jwt) {
        try {
            Object sub = jwt.getClaim("user_id");
            if (sub instanceof Number n) return n.longValue();
            // fallback: subject is the Keycloak UUID — store null if no numeric id claim
            return null;
        } catch (Exception e) {
            return null;
        }
    }
}
