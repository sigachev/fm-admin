package com.finmates.admin.services.registry;

/**
 * How a service's liveness is probed by the services dashboard.
 *
 * <ul>
 *   <li>{@link #ACTUATOR} — Spring Boot {@code /actuator/health}, JSON {@code {"status":"UP"}}.</li>
 *   <li>{@link #KEYCLOAK_Q} — Quarkus {@code /q/health}, JSON {@code {"status":"UP"}}.</li>
 *   <li>{@link #REDIS_PING} — TCP PING to redis (lightweight; no full client needed).</li>
 *   <li>{@link #NONE} — no remote probe; entry rendered health-only as UNKNOWN.</li>
 * </ul>
 */
public enum HealthType {
    ACTUATOR,
    KEYCLOAK_Q,
    REDIS_PING,
    NONE
}
