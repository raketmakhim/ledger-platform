package com.raketmakhim.ledger.accounting.domain;

public enum AccountType {
    CUSTOMER,
    /** The bank's own account, such as clearing. May have a negative balance. */
    SYSTEM
}
