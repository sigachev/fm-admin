package com.finmates.admin.repository.crypto;

import com.finmates.admin.entity.crypto.AdminPortfolio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;

public interface AdminPortfolioRepository extends JpaRepository<AdminPortfolio, Long> {

    Page<AdminPortfolio> findByUserId(Long userId, Pageable pageable);

    List<AdminPortfolio> findByUserId(Long userId);

    long countByCreatedAtAfter(OffsetDateTime since);
}
