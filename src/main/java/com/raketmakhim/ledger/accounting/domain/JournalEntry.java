package com.raketmakhim.ledger.accounting.domain;

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
