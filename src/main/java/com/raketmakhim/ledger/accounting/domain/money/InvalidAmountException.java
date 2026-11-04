package com.raketmakhim.ledger.accounting.domain.money;

import java.math.BigDecimal;

import com.raketmakhim.ledger.accounting.domain.DomainException;

/** The amount of a deposit or transfer is zero or negative. {@code operation} is only used in the message, e.g. "Deposit". */
public class InvalidAmountException extends DomainException {

    public InvalidAmountException(String operation, BigDecimal amount){
        super(operation + " amount must be positive: " + amount);
    }
}
