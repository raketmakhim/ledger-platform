package com.raketmakhim.ledger.accounting.domain.journal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record JournalEntry(UUID id, EntryType type, String reference, Instant createdAt, List<Posting> postings) {

    public JournalEntry {
        nullChecks(id, type, reference, createdAt, postings);
        blankCheck(reference);
        referenceLengthCheck(reference);
        postings = List.copyOf(postings);
        minimumPostingsCheck(postings);
        balanceCheck(postings);
    }

    private static void nullChecks(UUID id, EntryType type, String reference, Instant createdAt, List<Posting> postings){
        if (id == null || type == null || reference == null || createdAt == null || postings == null){
            throw new IllegalArgumentException("Id/Type/Reference/CreatedAt/Postings cannot be null.");
        }
    }

    private static void blankCheck(String reference){
        if (reference.isBlank()){
            throw new IllegalArgumentException("Reference cannot be blank.");
        }
    }

    // ISO 20022 ids are at most 35 characters, and a UETR is 36. V1__init.sql has a matching VARCHAR(36).
    private static final int MAX_REFERENCE_LENGTH = 36;

    private static void referenceLengthCheck(String reference){
        if (reference.length() > MAX_REFERENCE_LENGTH){
            throw new IllegalArgumentException("Reference is longer than " + MAX_REFERENCE_LENGTH + " characters.");
        }
    }

    private static void minimumPostingsCheck(List<Posting> postings){
        if (postings.size() < 2){
            throw new IllegalArgumentException("A journal entry needs at least 2 postings.");
        }
    }

    private static void balanceCheck(List<Posting> postings){
        Map<Currency, BigDecimal> totals = new HashMap<>();
        for (Posting posting : postings){
            totals.merge(posting.amount().currency(), posting.amount().amount(), BigDecimal::add);
        }
        totals.forEach((currency, total) -> {
            if (total.signum() != 0){
                throw new UnbalancedEntryException(currency, total);
            }
        });
    }
}
