package com.raketmakhim.ledger.accounting.domain.journal;

import java.util.UUID;

import com.raketmakhim.ledger.accounting.domain.DomainException;

/** There is no such entry, or the caller owns none of its accounts. The two look the same on purpose. */
public class JournalEntryNotFoundException extends DomainException {

    private final UUID entryId;

    public JournalEntryNotFoundException(UUID entryId){
        super("Journal entry not found: " + entryId);
        this.entryId = entryId;
    }

    public UUID entryId(){
        return entryId;
    }
}
