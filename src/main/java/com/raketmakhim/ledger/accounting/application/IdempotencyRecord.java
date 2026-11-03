package com.raketmakhim.ledger.accounting.application;

import java.util.UUID;

import com.raketmakhim.ledger.accounting.domain.journal.JournalEntry;

/** The first result for a caller's key, kept so a retry can get the same answer. */
public record IdempotencyRecord(UUID callerId, String key, String fingerprint, JournalEntry entry) {
}
