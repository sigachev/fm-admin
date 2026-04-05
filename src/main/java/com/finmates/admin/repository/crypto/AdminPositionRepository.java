package com.finmates.admin.repository.crypto;

import com.finmates.admin.entity.crypto.AdminPosition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdminPositionRepository extends JpaRepository<AdminPosition, Long> {

    List<AdminPosition> findByPortfolioId(Long portfolioId);

    Page<AdminPosition> findBySymbol(String symbol, Pageable pageable);

    Page<AdminPosition> findByUserId(Long userId, Pageable pageable);

    void deleteByPortfolioId(Long portfolioId);

    Optional<AdminPosition> findByPortfolioIdAndSymbol(Long portfolioId, String symbol);
}
