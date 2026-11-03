package com.raketmakhim.ledger.accounting.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.raketmakhim.ledger.accounting.domain.journal.EntryType;
import com.raketmakhim.ledger.accounting.domain.journal.JournalEntry;
import com.raketmakhim.ledger.accounting.domain.journal.Posting;
import com.raketmakhim.ledger.accounting.domain.money.Money;

class IdempotencyRecordTest {

    private static final Currency GBP = Currency.getInstance("GBP");
    private static final Instant CREATED = Instant.parse("2026-10-15T12:00:00Z");
    private static final Duration WINDOW = Duration.ofHours(24);

    private static final IdempotencyRecord RECORD = new IdempotencyRecord(new UUID(7, 1), "key", "f", entry(), CREATED);

    private static JournalEntry entry() {
        return new JournalEntry(new UUID(2, 1), EntryType.TRANSFER, "T-1", CREATED, List.of(
                new Posting(new UUID(3, 1), new UUID(1, 1), new Money(new BigDecimal("-1"), GBP)),
                new Posting(new UUID(3, 2), new UUID(1, 2), new Money(new BigDecimal("1"), GBP))));
    }

    @Test
    void isValidWhileYoungerThanTheWindow() {
        assertThat(RECORD.hasExpired(CREATED, WINDOW)).isFalse();
        assertThat(RECORD.hasExpired(CREATED.plus(WINDOW).minusNanos(1), WINDOW)).isFalse();
    }

    @Test
    void hasExpiredExactlyAtTheWindowAndAfter() {
        assertThat(RECORD.hasExpired(CREATED.plus(WINDOW), WINDOW)).isTrue();
        assertThat(RECORD.hasExpired(CREATED.plus(WINDOW).plusSeconds(1), WINDOW)).isTrue();
    }

    @Test
    void usesTheWindowItIsGiven() {
        assertThat(RECORD.hasExpired(CREATED.plus(Duration.ofMinutes(31)), Duration.ofMinutes(30))).isTrue();
        assertThat(RECORD.hasExpired(CREATED.plus(Duration.ofMinutes(31)), WINDOW)).isFalse();
    }
}
