package com.raketmakhim.ledger.accounting.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import com.raketmakhim.ledger.accounting.domain.Require;
import com.raketmakhim.ledger.accounting.domain.account.Account;
import com.raketmakhim.ledger.accounting.domain.account.AccountNotFoundException;
import com.raketmakhim.ledger.accounting.domain.account.AccountStatus;
import com.raketmakhim.ledger.accounting.domain.account.AccountType;
import com.raketmakhim.ledger.accounting.domain.money.Money;
import com.raketmakhim.ledger.accounting.domain.money.UnsupportedCurrencyException;

/**
 * Opens customer accounts and shows them to their owner. The caller is always a plain id: who that is
 * (a header today, a login later) is decided in the web layer.
 * Someone else's account is reported as not found, the same as a missing one, so ids cannot be probed.
 */
public class AccountService {

    private final AccountRepository accounts;
    private final IdGenerator ids;
    private final Clock clock;

    public AccountService(AccountRepository accounts, IdGenerator ids, Clock clock){
        this.accounts = accounts;
        this.ids = ids;
        this.clock = clock;
    }

    /** A new, empty, active account. Only currencies the bank has a clearing account for are allowed. */
    public Account open(UUID ownerId, Currency currency){
        Require.notNull(ownerId, "OwnerId");
        Require.notNull(currency, "Currency");
        accounts.findClearingAccount(currency).orElseThrow(() -> new UnsupportedCurrencyException(currency));
        Account account = new Account(ids.newId(), ownerId, currency, AccountType.CUSTOMER, AccountStatus.ACTIVE,
                new Money(BigDecimal.ZERO, currency), 0, clock.instant());
        accounts.save(account);
        return account;
    }

    public Account get(UUID callerId, UUID accountId){
        Require.notNull(callerId, "CallerId");
        Require.notNull(accountId, "AccountId");
        Account account = accounts.getById(accountId);
        if (!callerId.equals(account.ownerId())){
            throw new AccountNotFoundException(accountId);
        }
        return account;
    }

    public List<Account> list(UUID callerId){
        Require.notNull(callerId, "CallerId");
        return accounts.findByOwner(callerId);
    }
}
