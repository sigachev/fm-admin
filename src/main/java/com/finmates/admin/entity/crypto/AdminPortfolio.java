package com.finmates.admin.entity.crypto;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Read mapping of the 'portfolios' table in the crypto DB.
 * type and provider are stored as VARCHAR in the DB (EnumType.STRING in finmates-crypto).
 */
@Entity
@Table(name = "portfolios")
@Getter
@Setter
@NoArgsConstructor
public class AdminPortfolio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "username", length = 100)
    private String username;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    /** VIRTUAL | MANUAL | SYNCED — stored as String in DB */
    @Column(name = "type", nullable = false, length = 30)
    private String type;

    /** MANUAL | BINANCE | etc. — nullable for non-SYNCED portfolios */
    @Column(name = "provider", length = 30)
    private String provider;

    // cash_balance / initial_balance dropped in V10 shared-wallet refactor —
    // cash now lives on users.virtual_wallet_balance / users.starting_capital (main DB).

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic;

    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
