package com.raketmakhim.ledger.accounting.domain;

import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

public record Account(UUID id, Currency currency, AccountType type, AccountStatus status,
                      Money balance, long version, Instant createdAt) {

    public Account {
        nullChecks(id, currency, type, status, balance, createdAt);
        currencyCheck(currency, balance);
        overdraftCheck(type, balance);
    }

    /**
     * Returns a new Account with the posting's signed amount added to the balance.
     * This account is left unchanged. Throws IllegalArgumentException if the posting
     * is for another account or currency, the account is not ACTIVE, or a customer
     * balance would go negative.
     * <p>
     * version is not changed here: the persistence layer increments it on save.
     */
    public Account applyPosting(Posting posting){
        if (!id.equals(posting.accountId())){
            throw new IllegalArgumentException("Posting is for a different account.");
        }
        if (status != AccountStatus.ACTIVE){
            throw new IllegalArgumentException("Account is not active: " + status);
        }
        currencyCheck(currency, posting.amount());
        Money newBalance = balance.plus(posting.amount());
        if (type == AccountType.CUSTOMER && newBalance.isNegative()){
            throw new IllegalArgumentException("Insufficient funds: balance " + balance.amount()
                    + ", posting " + posting.amount().amount());
        }
        return new Account(id, currency, type, status, newBalance, version, createdAt);
    }

    private static void nullChecks(UUID id, Currency currency, AccountType type, AccountStatus status,
                                   Money balance, Instant createdAt){
        if (id == null || currency == null || type == null || status == null || balance == null || createdAt == null){
            throw new IllegalArgumentException("Id/Currency/Type/Status/Balance/CreatedAt cannot be null.");
        }
    }

    private static void currencyCheck(Currency currency, Money money){
        if (!currency.equals(money.currency())){
            throw new IllegalArgumentException("Currency mismatch: account is " + currency.getCurrencyCode()
                    + ", amount is " + money.currency().getCurrencyCode());
        }
    }

    private static void overdraftCheck(AccountType type, Money balance){
        if (type == AccountType.CUSTOMER && balance.isNegative()){
            throw new IllegalArgumentException("Invalid account: a customer account cannot be created with a negative balance: " + balance.amount());
        }
    }
}
