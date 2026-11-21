package com.finmates.admin.monitoring;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * One cached value with a time-to-live, for the Services dashboard endpoints. Replaces two hand-rolled copies
 * (in ServicesStatusService and ClusterAlertsService).
 *
 * <p>Not a stampede guard: two callers that miss at the same moment both build. That is fine for a 10-30 s
 * admin dashboard cache and keeps it lock-free.
 */
final class TtlCache<T> {

    private record Entry<T>(T value, Instant expiresAt) { }

    private final Duration ttl;
    private final AtomicReference<Entry<T>> entry = new AtomicReference<>();

    TtlCache(Duration ttl) {
        this.ttl = ttl;
    }

    T get(Supplier<T> build) {
        Entry<T> e = entry.get();
        if (e != null && Instant.now().isBefore(e.expiresAt())) {
            return e.value();
        }
        T value = build.get();
        entry.set(new Entry<>(value, Instant.now().plus(ttl)));
        return value;
    }
}
