package com.raketmakhim.ledger.accounting.domain.account;

import com.raketmakhim.ledger.accounting.domain.DomainException;

public class AccountNotActiveException extends DomainException {

    private final AccountStatus status;

    public AccountNotActiveException(AccountStatus status){
        super("Account is not active: " + status);
        this.status = status;
    }

    public AccountStatus status(){
        return status;
    }
}
