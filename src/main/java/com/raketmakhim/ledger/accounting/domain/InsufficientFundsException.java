package com.raketmakhim.ledger.accounting.domain;

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
