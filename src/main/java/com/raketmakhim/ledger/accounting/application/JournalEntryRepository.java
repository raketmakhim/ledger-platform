package com.raketmakhim.ledger.accounting.application;

import com.raketmakhim.ledger.accounting.domain.journal.JournalEntry;

/** Outbound port: how the application stores journal entries. The database adapter implements it. */
public interface JournalEntryRepository {

    /** Throws DuplicateReferenceException if an entry of the same type and reference is already stored. */
    void save(JournalEntry entry);
}
