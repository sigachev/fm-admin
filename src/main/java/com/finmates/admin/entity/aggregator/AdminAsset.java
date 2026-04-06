package com.finmates.admin.entity.aggregator;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * Read/Write mapping of the 'asset' table in the crypto_aggregator DB.
 * Tracks crypto tokens and their metadata.
 */
@Entity
@Table(name = "asset")
@Getter
@Setter
@NoArgsConstructor
public class AdminAsset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "symbol", unique = true, length = 20)
    private String symbol;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "is_active")
    private Boolean isActive = true;

    @Column(name = "rank")
    private Integer rank;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (isActive == null) {
            isActive = true;
        }
    }
}
