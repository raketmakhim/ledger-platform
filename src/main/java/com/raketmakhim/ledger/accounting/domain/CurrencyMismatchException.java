package com.raketmakhim.ledger.accounting.domain;

import java.util.Currency;

public class CurrencyMismatchException extends DomainException {

    private final Currency expected;
    private final Currency actual;

    public CurrencyMismatchException(Currency expected, Currency actual){
        super("Currency mismatch: " + expected.getCurrencyCode() + " vs " + actual.getCurrencyCode());
        this.expected = expected;
        this.actual = actual;
    }

    public Currency expected(){
        return expected;
    }

    public Currency actual(){
        return actual;
    }
}
