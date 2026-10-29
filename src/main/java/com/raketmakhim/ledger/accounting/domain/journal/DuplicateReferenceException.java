package com.raketmakhim.ledger.accounting.domain.journal;

import com.raketmakhim.ledger.accounting.domain.DomainException;

/** An entry of this type with this reference already exists, so a repeated request must not move money again. */
public class DuplicateReferenceException extends DomainException {

    public DuplicateReferenceException(EntryType type, String reference){
        super("A " + type + " with reference " + reference + " already exists.");
    }
}
