package com.finmates.admin.repository.aggregator;

import com.finmates.admin.entity.aggregator.AdminAsset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface AdminAssetRepository extends JpaRepository<AdminAsset, Long> {

    /**
     * Search assets by symbol or name (case-insensitive)
     */
    Page<AdminAsset> findBySymbolContainingIgnoreCaseOrNameContainingIgnoreCase(
            String symbol,
            String name,
            Pageable pageable
    );

    /**
     * Find asset by symbol (exact match, case-insensitive)
     */
    Optional<AdminAsset> findBySymbolIgnoreCase(String symbol);

    /**
     * Count active assets
     */
    long countByIsActiveTrue();
}
