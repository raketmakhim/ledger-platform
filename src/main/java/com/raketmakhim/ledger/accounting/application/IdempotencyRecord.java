package com.raketmakhim.ledger.accounting.application;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.raketmakhim.ledger.accounting.domain.journal.JournalEntry;

/** The first result for a caller's key, kept so a retry can get the same answer. It stops counting after a while. */
public record IdempotencyRecord(UUID callerId, String key, String fingerprint, JournalEntry entry, Instant createdAt) {

    /** Valid while younger than the window, so a record exactly as old as the window has expired. */
    public boolean hasExpired(Instant now, Duration window){
        return !now.isBefore(createdAt.plus(window));
    }
}
