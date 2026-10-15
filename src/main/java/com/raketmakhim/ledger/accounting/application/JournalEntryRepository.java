package com.raketmakhim.ledger.accounting.application;

import com.raketmakhim.ledger.accounting.domain.journal.JournalEntry;

/** Outbound port: how the application stores journal entries. The database adapter implements it. */
public interface JournalEntryRepository {

    void save(JournalEntry entry);
}
