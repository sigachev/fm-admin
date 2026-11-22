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

    long countByDeletedAtIsNotNull();

    long countByDeletedAtIsNull();

    /**
     * Users who logged in within the last {@code days} days. {@code users.last_login} (written by finmates-main) is
     * not mapped on {@link AdminUser} on purpose: it is a timestamp WITHOUT time zone and mapping it would make
     * k8s schema validation ({@code ddl-auto=validate}) and dev {@code update} touch a column this service doesn't own.
     */
    @org.springframework.data.jpa.repository.Query(value = """
            SELECT COUNT(*) FROM users
            WHERE deleted_at IS NULL AND last_login >= now() - make_interval(days => :days)""", nativeQuery = true)
    long countLoggedInWithinDays(@org.springframework.data.repository.query.Param("days") int days);

    java.util.Optional<AdminUser> findByKeycloakId(String keycloakId);
}
