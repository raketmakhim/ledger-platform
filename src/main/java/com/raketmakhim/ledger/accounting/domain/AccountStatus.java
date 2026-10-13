package com.raketmakhim.ledger.accounting.domain;

public enum AccountStatus {
    ACTIVE,
    /** Temporarily blocked: accepts no postings, but can be made ACTIVE again. */
    FROZEN,
    /** Permanently shut: accepts no postings and is not reopened. */
    CLOSED
}
