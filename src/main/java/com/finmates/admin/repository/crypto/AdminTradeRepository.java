package com.finmates.admin.repository.crypto;

import com.finmates.admin.entity.crypto.AdminTrade;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;

public interface AdminTradeRepository extends JpaRepository<AdminTrade, Long> {

    Page<AdminTrade> findByPortfolioId(Long portfolioId, Pageable pageable);

    Page<AdminTrade> findByUserId(Long userId, Pageable pageable);

    Page<AdminTrade> findBySymbol(String symbol, Pageable pageable);

    Page<AdminTrade> findByPortfolioIdAndSymbol(Long portfolioId, String symbol, Pageable pageable);

    long countByOpenedAtAfter(OffsetDateTime since);
}
