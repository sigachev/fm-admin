package com.finmates.admin.dto.services;

/**
 * Headline counts for the cluster-wide DB-connection panel.
 *
 * <p>{@code externalTotal} is the highest-value figure on the dashboard — it is
 * the count of Postgres connections coming from outside the {@code 10.244.0.0/16}
 * pod CIDR, which in practice is laptops with running local dev stacks or open
 * DBeaver/IntelliJ sessions. Driving this number toward zero is the operational
 * lever for cluster connection pressure.
 */
public record DbConnectionsSummaryDto(
        int total,
        int podTotal,
        int externalTotal
) {
}
