package com.raketmakhim.ledger.accounting.domain.account;

import com.raketmakhim.ledger.accounting.domain.DomainException;

/** A transfer names the same account as its source and its destination. */
public class SameAccountException extends DomainException {

    public SameAccountException(){
        super("Cannot transfer to the same account.");
    }
}
