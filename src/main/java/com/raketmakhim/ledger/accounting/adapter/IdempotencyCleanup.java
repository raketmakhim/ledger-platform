package com.raketmakhim.ledger.accounting.adapter;

import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.raketmakhim.ledger.accounting.application.IdempotencyRepository;

/**
 * Deletes idempotency records that no longer protect a retry. Postgres has no row expiry of its own.
 * The ledger is not touched: only the key records go. Safe to run any number of times, on any number of instances.
 */
@Component
public class IdempotencyCleanup {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanup.class);

    private final IdempotencyRepository keys;
    private final Clock clock;
    private final Duration window;

    public IdempotencyCleanup(IdempotencyRepository keys, Clock clock,
                              @Value("${ledger.idempotency.window}") Duration window) {
        this.keys = keys;
        this.clock = clock;
        this.window = window;
    }

    @Scheduled(fixedDelayString = "${ledger.idempotency.cleanup-interval}")
    public void run() {
        int deleted = keys.deleteExpired(clock.instant().minus(window));
        log.info("Deleted {} expired idempotency records", deleted);
    }
}
