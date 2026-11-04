package com.raketmakhim.ledger.accounting.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.raketmakhim.ledger.accounting.domain.journal.EntryType;
import com.raketmakhim.ledger.accounting.domain.journal.Posting;

/**
 * What a caller may see of a journal entry. It is not a JournalEntry, because a customer does not see the bank's
 * own postings, so the postings here need not add up to zero.
 */
public record EntryView(UUID id, EntryType type, String reference, Instant createdAt, List<Posting> postings) {
}
