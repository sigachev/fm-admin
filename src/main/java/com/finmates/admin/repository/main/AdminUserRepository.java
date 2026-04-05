package com.finmates.admin.repository.main;

import com.finmates.admin.entity.main.AdminUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.time.OffsetDateTime;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {

    Page<AdminUser> findByUsernameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String username, String email, Pageable pageable);

    long countByCreatedAtAfter(OffsetDateTime since);
}
