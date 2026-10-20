package com.raketmakhim.ledger.accounting.domain.journal;

import java.util.UUID;

import com.raketmakhim.ledger.accounting.domain.Require;
import com.raketmakhim.ledger.accounting.domain.money.Money;

/**
 * One account's side of a journal entry: a signed amount applied to a single account.
 * Positive is a credit (money in), negative is a debit (money out). It cannot be zero.
 * A JournalEntry holds the postings that make up one transaction.
 */
public record Posting(UUID id, UUID accountId, Money amount) {

    public Posting {
        Require.notNull(id, "Id");
        Require.notNull(accountId, "AccountId");
        Require.notNull(amount, "Amount");
        nonZeroCheck(amount);
    }

    private static void nonZeroCheck(Money amount){
        if (amount.isZero()){
            throw new IllegalArgumentException("Posting amount cannot be zero.");
        }
    }
}
