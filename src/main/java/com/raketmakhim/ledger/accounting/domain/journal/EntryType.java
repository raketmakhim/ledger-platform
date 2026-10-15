package com.raketmakhim.ledger.accounting.domain.journal;

public enum EntryType {
    DEPOSIT,
    TRANSFER,
    /** Undoes an earlier entry. Entries are never edited, so corrections are new entries. */
    REVERSAL
}
