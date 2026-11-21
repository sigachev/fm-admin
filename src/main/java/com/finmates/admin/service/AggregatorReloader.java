package com.finmates.admin.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Tells the aggregator to re-read tokens and feeds ({@code POST /internal/v1/discovery/reload-assets}).
 *
 * <p>When called inside a transaction, the reload is deferred until AFTER COMMIT: the aggregator reads the database
 * itself, and a reload fired mid-transaction would see the old rows (the previous code reloaded before commit, so a
 * change sometimes only applied on the next reload). Best-effort: a failure is logged, never thrown, because the
 * admin's database change has already succeeded.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AggregatorReloader {

    private final AggregatorClient aggregatorClient;

    public void reloadAfterCommit(String reason) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    reloadNow(reason);
                }
            });
        } else {
            reloadNow(reason);
        }
    }

    private void reloadNow(String reason) {
        try {
            aggregatorClient.reloadAssets();
            log.info("Aggregator reloaded ({})", reason);
        } catch (Exception e) {
            log.warn("Aggregator reload failed after {}: {} - change applies on the next reload or restart",
                    reason, e.getMessage());
        }
    }
}
