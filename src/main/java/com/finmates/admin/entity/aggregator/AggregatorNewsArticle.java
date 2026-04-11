package com.finmates.admin.entity.aggregator;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Maps to news_article table in the crypto_data DB.
 * Used to mirror admin-created news so it appears in the public news feed.
 */
@Entity
@Table(name = "news_article",
       uniqueConstraints = @UniqueConstraint(columnNames = {"source_id", "external_id"}))
@Getter
@Setter
@NoArgsConstructor
public class AggregatorNewsArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    @Column(name = "external_id", nullable = false, length = 500)
    private String externalId;

    @Column(name = "title", nullable = false, length = 1000)
    private String title;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "url", length = 1000)
    private String url;

    @Column(name = "image_url", length = 1000)
    private String imageUrl;

    @Column(name = "author", length = 200)
    private String author;

    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "fetched_at", nullable = false)
    private Instant fetchedAt;

    @Column(name = "asset_symbols", length = 200)
    private String assetSymbols;

    @Column(name = "is_important", nullable = false)
    private boolean isImportant;

    @PrePersist
    void prePersist() {
        if (fetchedAt == null) fetchedAt = Instant.now();
    }
}
