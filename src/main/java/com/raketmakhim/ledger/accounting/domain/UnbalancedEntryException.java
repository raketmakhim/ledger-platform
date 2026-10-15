package com.raketmakhim.ledger.accounting.domain;

import java.math.BigDecimal;
import java.util.Currency;

public class UnbalancedEntryException extends DomainException {

    private final Currency currency;
    private final BigDecimal total;

    public UnbalancedEntryException(Currency currency, BigDecimal total){
        super("Postings do not balance for " + currency.getCurrencyCode() + ": " + total);
        this.currency = currency;
        this.total = total;
    }

    public Currency currency(){
        return currency;
    }

    public BigDecimal total(){
        return total;
    }
}
