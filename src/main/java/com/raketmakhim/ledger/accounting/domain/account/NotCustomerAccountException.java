package com.raketmakhim.ledger.accounting.domain.account;

import com.raketmakhim.ledger.accounting.domain.DomainException;

/** A deposit or transfer names one of the bank's own accounts where a customer account is needed. */
public class NotCustomerAccountException extends DomainException {

    public NotCustomerAccountException(String operation, AccountType type){
        super(operation + " needs a customer account, not " + type + ".");
    }
}
