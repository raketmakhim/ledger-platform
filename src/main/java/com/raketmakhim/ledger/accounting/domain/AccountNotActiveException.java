package com.raketmakhim.ledger.accounting.domain;

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
