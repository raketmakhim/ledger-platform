package com.raketmakhim.ledger.accounting.domain;

import java.util.UUID;

/**
 * One account's side of a journal entry: a signed amount applied to a single account.
 * Positive is a credit (money in), negative is a debit (money out). It cannot be zero.
 * A JournalEntry holds the postings that make up one transaction.
 */
public record Posting(UUID id, UUID accountId, Money amount) {

    public Posting {
        nullChecks(id, accountId, amount);
        nonZeroCheck(amount);
    }

    private static void nullChecks(UUID id, UUID accountId, Money amount){
        if (id == null || accountId == null || amount == null){
            throw new IllegalArgumentException("Id/AccountId/Amount cannot be null.");
        }
    }

    private static void nonZeroCheck(Money amount){
        if (amount.isZero()){
            throw new IllegalArgumentException("Posting amount cannot be zero.");
        }
    }
}
