package com.raketmakhim.ledger.accounting.application;

/** Other requests kept changing the same accounts, so the work gave up after retrying. The caller can try again later. */
public class ConcurrentUpdateException extends RuntimeException {

    public ConcurrentUpdateException(int attempts, Throwable cause) {
        super("Gave up after " + attempts + " attempts because the accounts kept changing.", cause);
    }
}
