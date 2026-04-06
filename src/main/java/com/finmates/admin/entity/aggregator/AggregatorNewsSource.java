package com.finmates.admin.entity.aggregator;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "news_source")
@Getter
@Setter
@NoArgsConstructor
public class AggregatorNewsSource {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "rss_url", nullable = false, length = 500, unique = true)
    private String rssUrl;

    @Column(name = "website_url", length = 500)
    private String websiteUrl;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @Column(name = "tier", nullable = false)
    private int tier;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
