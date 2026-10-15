package com.raketmakhim.ledger.accounting.domain;

import java.math.BigDecimal;
import java.util.Currency;

public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

    public Money {
        nullChecks(amount, currency);
        validCurrencyCheck(currency);
        decimalPlacesCheck(amount, currency);
        amount = amount.setScale(currency.getDefaultFractionDigits());
        maxAmountCheck(amount);
    }

    // NUMERIC(24,4) holds 20 digits before the decimal point
    private static final int MAX_INTEGER_DIGITS = 20;

    private static void maxAmountCheck(BigDecimal amount){
        if (amount.precision() - amount.scale() > MAX_INTEGER_DIGITS){
            throw new IllegalArgumentException("Amount too large: " + amount);
        }
    }

    public Money plus(Money other){
        sameCurrencyCheck(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money minus(Money other){
        sameCurrencyCheck(other);
        return new Money(amount.subtract(other.amount), currency);
    }

    public Money negate(){
        return new Money(amount.negate(), currency);
    }

    public boolean isZero(){
        return amount.signum() == 0;
    }

    public boolean isNegative(){
        return amount.signum() < 0;
    }

    public boolean isPositive(){
        return amount.signum() > 0;
    }

    @Override
    public int compareTo(Money other){
        sameCurrencyCheck(other);
        return amount.compareTo(other.amount);
    }

    private void sameCurrencyCheck(Money other){
        if (!currency.equals(other.currency)){
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }

    private static void decimalPlacesCheck(BigDecimal amount, Currency currency){
        if (amount.stripTrailingZeros().scale() > currency.getDefaultFractionDigits()){
            throw new IllegalArgumentException("Too many decimal places for " + currency.getCurrencyCode() + ": " + amount);
        }
    }

    private static void nullChecks(BigDecimal amount, Currency currency){
        if (amount == null || currency == null){
            throw new IllegalArgumentException("Amount/Currency cannot be null.");
        }
    }

    private static void validCurrencyCheck(Currency currency){
        if (currency.getDefaultFractionDigits() == -1){
            throw new IllegalArgumentException("Not a valid currency: " + currency.getCurrencyCode());
        }
    }
}
