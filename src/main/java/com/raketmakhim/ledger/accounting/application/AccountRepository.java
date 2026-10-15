package com.raketmakhim.ledger.accounting.application;

import java.util.Currency;
import java.util.Optional;
import java.util.UUID;

import com.raketmakhim.ledger.accounting.domain.account.Account;

/** Outbound port: how the application finds and stores accounts. The database adapter implements it. */
public interface AccountRepository {

    Optional<Account> findById(UUID id);

    /** The bank's SYSTEM clearing account for a currency. */
    Optional<Account> findClearingAccount(Currency currency);

    void save(Account account);
}
