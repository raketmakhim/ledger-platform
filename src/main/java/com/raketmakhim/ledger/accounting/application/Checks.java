package com.raketmakhim.ledger.accounting.application;

import com.raketmakhim.ledger.accounting.domain.Require;
import com.raketmakhim.ledger.accounting.domain.account.Account;
import com.raketmakhim.ledger.accounting.domain.account.AccountType;
import com.raketmakhim.ledger.accounting.domain.money.Money;

/** Input rules shared by the services. {@code operation} is only used in the error message, e.g. "Deposit". */
final class Checks {

    private Checks(){
    }

    static void positiveAmount(Money amount, String operation){
        Require.notNull(amount, "Amount");
        if (!amount.isPositive()){
            throw new IllegalArgumentException(operation + " amount must be positive: " + amount.amount());
        }
    }

    // V1__init.sql has a matching VARCHAR(64)
    private static final int MAX_KEY_LENGTH = 64;

    static void idempotencyKey(String key){
        Require.notNull(key, "Idempotency key");
        if (key.isBlank() || key.length() > MAX_KEY_LENGTH){
            throw new IllegalArgumentException("Idempotency key must be 1 to " + MAX_KEY_LENGTH + " characters.");
        }
    }

    static void customerAccount(Account account, String operation){
        if (account.type() != AccountType.CUSTOMER){
            throw new IllegalArgumentException(operation + " needs a customer account, not " + account.type() + ".");
        }
    }
}
