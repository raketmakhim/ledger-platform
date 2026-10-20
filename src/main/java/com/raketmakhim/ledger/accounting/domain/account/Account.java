package com.raketmakhim.ledger.accounting.domain.account;

import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

import com.raketmakhim.ledger.accounting.domain.Require;
import com.raketmakhim.ledger.accounting.domain.journal.Posting;
import com.raketmakhim.ledger.accounting.domain.money.CurrencyMismatchException;
import com.raketmakhim.ledger.accounting.domain.money.Money;

public record Account(UUID id, Currency currency, AccountType type, AccountStatus status,
                      Money balance, long version, Instant createdAt) {

    public Account {
        Require.notNull(id, "Id");
        Require.notNull(currency, "Currency");
        Require.notNull(type, "Type");
        Require.notNull(status, "Status");
        Require.notNull(balance, "Balance");
        Require.notNull(createdAt, "CreatedAt");
        currencyCheck(currency, balance);
        overdraftCheck(type, balance);
    }

    /**
     * Returns a new Account with the posting's signed amount added to the balance.
     * This account is left unchanged. Throws IllegalArgumentException if the posting
     * is for another account, AccountNotActiveException if the account is not ACTIVE,
     * CurrencyMismatchException for another currency, and InsufficientFundsException
     * if a customer balance would go negative.
     * <p>
     * version is not changed here: the persistence layer increments it on save.
     */
    public Account applyPosting(Posting posting){
        if (!id.equals(posting.accountId())){
            throw new IllegalArgumentException("Posting is for a different account.");
        }
        if (status != AccountStatus.ACTIVE){
            throw new AccountNotActiveException(status);
        }
        currencyCheck(currency, posting.amount());
        Money newBalance = balance.plus(posting.amount());
        if (!type.allowsNegativeBalance() && newBalance.isNegative()){
            throw new InsufficientFundsException(balance, posting.amount());
        }
        return new Account(id, currency, type, status, newBalance, version, createdAt);
    }

    private static void currencyCheck(Currency currency, Money money){
        if (!currency.equals(money.currency())){
            throw new CurrencyMismatchException(currency, money.currency());
        }
    }

    private static void overdraftCheck(AccountType type, Money balance){
        if (!type.allowsNegativeBalance() && balance.isNegative()){
            throw new IllegalArgumentException("Invalid account: a customer account cannot be created with a negative balance: " + balance.amount());
        }
    }
}
