package com.raketmakhim.ledger.accounting.domain.account;

import com.raketmakhim.ledger.accounting.domain.DomainException;
import com.raketmakhim.ledger.accounting.domain.money.Money;

public class InsufficientFundsException extends DomainException {

    private final Money balance;
    private final Money requested;

    public InsufficientFundsException(Money balance, Money requested){
        super("Insufficient funds: balance " + balance.amount() + ", posting " + requested.amount());
        this.balance = balance;
        this.requested = requested;
    }

    public Money balance(){
        return balance;
    }

    public Money requested(){
        return requested;
    }
}
