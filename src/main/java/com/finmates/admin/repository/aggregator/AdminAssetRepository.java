package com.finmates.admin.repository.aggregator;

import com.finmates.admin.entity.aggregator.AdminAsset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
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

    /**
     * Filter assets whose symbol is in the given collection (case-sensitive; symbols are stored uppercase)
     */
    Page<AdminAsset> findBySymbolIn(Collection<String> symbols, Pageable pageable);

    /**
     * Filter assets by symbol set AND search term (symbol or name contains search)
     */
    @Query("SELECT a FROM AdminAsset a WHERE a.symbol IN :symbols AND " +
           "(LOWER(a.symbol) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(a.name) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<AdminAsset> findBySymbolInAndSearchTerm(
            @Param("symbols") Collection<String> symbols,
            @Param("search") String search,
            Pageable pageable
    );

    /**
     * Find all assets that have a case-insensitive duplicate symbol (UPPER(symbol) appears more than once).
     * Returns all rows that are part of a duplicate group, ordered by UPPER(symbol) then id.
     */
    @Query(value = "SELECT * FROM asset WHERE UPPER(symbol) IN " +
                   "(SELECT UPPER(symbol) FROM asset GROUP BY UPPER(symbol) HAVING COUNT(*) > 1) " +
                   "ORDER BY UPPER(symbol), id",
           nativeQuery = true)
    List<AdminAsset> findDuplicatesBySymbol();
}
