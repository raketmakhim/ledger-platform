package com.raketmakhim.ledger.accounting.domain;

import java.util.Currency;

/** No clearing account exists for the currency, so the bank does not handle it. */
public class UnsupportedCurrencyException extends DomainException {

    private final Currency currency;

    public UnsupportedCurrencyException(Currency currency){
        super("Unsupported currency: " + currency.getCurrencyCode());
        this.currency = currency;
    }

    public Currency currency(){
        return currency;
    }
}
