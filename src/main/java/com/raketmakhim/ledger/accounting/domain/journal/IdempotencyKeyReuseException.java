package com.raketmakhim.ledger.accounting.domain.journal;

import com.raketmakhim.ledger.accounting.domain.DomainException;

/** The caller used a key for an earlier request that asked for something else. */
public class IdempotencyKeyReuseException extends DomainException {

    public IdempotencyKeyReuseException(String key){
        super("Idempotency key " + key + " was already used for a different request.");
    }
}
