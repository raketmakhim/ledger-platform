package com.raketmakhim.ledger.accounting.domain.account;

import java.util.UUID;

import com.raketmakhim.ledger.accounting.domain.DomainException;

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
