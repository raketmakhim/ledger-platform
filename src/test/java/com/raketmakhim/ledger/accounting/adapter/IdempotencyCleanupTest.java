package com.raketmakhim.ledger.accounting.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.raketmakhim.ledger.accounting.application.IdempotencyRecord;
import com.raketmakhim.ledger.accounting.application.IdempotencyRepository;

class IdempotencyCleanupTest {

    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

    private final List<Instant> cutoffs = new ArrayList<>();

    private final IdempotencyRepository keys = new IdempotencyRepository() {
        public Optional<IdempotencyRecord> find(UUID callerId, String key) {
            return Optional.empty();
        }

        public void save(IdempotencyRecord record) {
        }

        public void release(UUID callerId, String key) {
        }

        public int deleteExpired(Instant cutoff) {
            cutoffs.add(cutoff);
            return 3;
        }
    };

    @Test
    void deletesRecordsAsOldAsTheWindowOrOlder() {
        new IdempotencyCleanup(keys, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofHours(24)).run();

        assertThat(cutoffs).containsExactly(NOW.minus(Duration.ofHours(24)));
    }

    @Test
    void usesTheConfiguredWindow() {
        new IdempotencyCleanup(keys, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(30)).run();

        assertThat(cutoffs).containsExactly(NOW.minus(Duration.ofMinutes(30)));
    }

    @Test
    void canRunAgainAndAgain() {
        IdempotencyCleanup cleanup = new IdempotencyCleanup(keys, Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofHours(24));

        cleanup.run();
        cleanup.run();

        assertThat(cutoffs).hasSize(2);
    }
}
