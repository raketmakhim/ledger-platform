package com.raketmakhim.ledger.accounting.domain;

import java.util.UUID;

public class AccountNotFoundException extends DomainException {

    private final UUID accountId;

    public AccountNotFoundException(UUID accountId){
        super("Account not found: " + accountId);
        this.accountId = accountId;
    }

    public UUID accountId(){
        return accountId;
    }
}
