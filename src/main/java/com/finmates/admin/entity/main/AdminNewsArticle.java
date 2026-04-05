package com.finmates.admin.entity.main;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Maps to 'admin_news' table in the finmates (main) DB.
 * Created by V11__admin_news.sql migration.
 * Symbols stored as comma-separated string (e.g. "BTC,ETH").
 */
@Entity
@Table(name = "admin_news")
@Getter
@Setter
@NoArgsConstructor
public class AdminNewsArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "url", length = 1000)
    private String url;

    @Column(name = "source", nullable = false, length = 100)
    private String source = "FinMates";

    @Column(name = "is_breaking", nullable = false)
    private boolean isBreaking;

    /** Comma-separated symbol list, e.g. "BTC,ETH". Parsed to List<String> in the service layer. */
    @Column(name = "symbols", length = 500)
    private String symbols;

    @Column(name = "published_at", nullable = false)
    private OffsetDateTime publishedAt;

    /** FK to users.id — the admin who created this article. */
    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        if (publishedAt == null) publishedAt = now;
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
