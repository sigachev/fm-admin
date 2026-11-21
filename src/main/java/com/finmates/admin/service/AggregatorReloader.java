package com.finmates.admin.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Tells the aggregator to re-read tokens and feeds ({@code POST /internal/v1/discovery/reload-assets}).
 *
 * <ul>
 *   <li><b>After commit.</b> Inside a transaction the request is deferred until the commit: the aggregator reads the
 *       database itself, and a reload fired mid-transaction would see the old rows.</li>
 *   <li><b>Debounced.</b> Requests within {@code aggregator.reload-debounce-ms} (default 2 s) are coalesced into ONE
 *       reload. "Enable 100 tickers" sends 100 PATCHes; without this each would make the exchange socket reconnect
 *       with a new subscription, 100 times in a few seconds.</li>
 *   <li><b>Best-effort.</b> A failure is logged, never thrown - the admin's database change already succeeded.</li>
 * </ul>
 */
@Slf4j
@Component
public class AggregatorReloader {

    private final AggregatorClient aggregatorClient;
    private final long debounceMs;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "aggregator-reload");
        t.setDaemon(true);
        return t;
    });
    private final Object lock = new Object();
    private final Set<String> pendingReasons = new LinkedHashSet<>();
    private boolean scheduled;

    public AggregatorReloader(AggregatorClient aggregatorClient,
                              @Value("${aggregator.reload-debounce-ms:2000}") long debounceMs) {
        this.aggregatorClient = aggregatorClient;
        this.debounceMs = debounceMs;
    }

    public void reloadAfterCommit(String reason) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    request(reason);
                }
            });
        } else {
            request(reason);
        }
    }

    private void request(String reason) {
        synchronized (lock) {
            if (pendingReasons.size() < 20) {
                pendingReasons.add(reason);
            }
            if (scheduled) {
                return;
            }
            scheduled = true;
        }
        scheduler.schedule(this::fire, debounceMs, TimeUnit.MILLISECONDS);
    }

    private void fire() {
        String reasons;
        synchronized (lock) {
            reasons = String.join(", ", pendingReasons);
            pendingReasons.clear();
            scheduled = false;
        }
        try {
            aggregatorClient.reloadAssets();
            log.info("Aggregator reloaded ({})", reasons);
        } catch (Exception e) {
            log.warn("Aggregator reload failed after {}: {} - change applies on the next reload or restart",
                    reasons, e.getMessage());
        }
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdown();
    }
}
