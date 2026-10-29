package com.raketmakhim.ledger.accounting.application;

import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.raketmakhim.ledger.accounting.domain.account.Account;
import com.raketmakhim.ledger.accounting.domain.account.AccountNotFoundException;

/** Outbound port: how the application finds and stores accounts. The database adapter implements it. */
public interface AccountRepository {

    Optional<Account> findById(UUID id);

    default Account getById(UUID id) {
        return findById(id).orElseThrow(() -> new AccountNotFoundException(id));
    }

    /** The bank's SYSTEM clearing account for a currency. */
    Optional<Account> findClearingAccount(Currency currency);

    /** The accounts that belong to a customer, oldest first. */
    List<Account> findByOwner(UUID ownerId);

    void save(Account account);
}
