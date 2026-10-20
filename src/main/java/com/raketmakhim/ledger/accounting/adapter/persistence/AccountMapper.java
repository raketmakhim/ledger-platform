package com.raketmakhim.ledger.accounting.adapter.persistence;

import java.util.Currency;

import com.raketmakhim.ledger.accounting.domain.account.Account;
import com.raketmakhim.ledger.accounting.domain.money.Money;

/** Converts between the domain Account and its database row. Written by hand: see decisions/architecture.md. */
final class AccountMapper {

    private AccountMapper() {
    }

    static AccountEntity toEntity(Account account) {
        return new AccountEntity(account.id(), account.currency().getCurrencyCode(), account.type(), account.status(),
                account.balance().amount(), account.version(), account.createdAt());
    }

    static Account toDomain(AccountEntity entity) {
        Currency currency = Currency.getInstance(entity.getCurrency());
        return new Account(entity.getId(), currency, entity.getType(), entity.getStatus(),
                new Money(entity.getBalance(), currency), entity.getVersion(), entity.getCreatedAt());
    }
}
