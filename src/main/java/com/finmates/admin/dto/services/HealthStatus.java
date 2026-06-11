package com.finmates.admin.dto.services;

/**
 * Honest-degradation health states for the services dashboard.
 *
 * <ul>
 *   <li>{@link #UP} — probe returned status "UP" (Actuator/Quarkus) or PING succeeded.</li>
 *   <li>{@link #DOWN} — probe returned a definite negative (e.g. 503 with status "DOWN").
 *       Surfaced as such; do not filter.</li>
 *   <li>{@link #UNKNOWN} — probe could not be evaluated: 401, timeout, connection
 *       refused, unparseable body. Do NOT degrade to DOWN — we cannot tell.</li>
 * </ul>
 */
public enum HealthStatus {
    UP,
    DOWN,
    UNKNOWN
}
