package com.raketmakhim.ledger.accounting.domain;

import java.util.UUID;

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
