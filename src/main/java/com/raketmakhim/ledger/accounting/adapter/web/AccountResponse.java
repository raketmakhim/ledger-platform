package com.raketmakhim.ledger.accounting.adapter.web;

import java.time.Instant;
import java.util.UUID;

import com.raketmakhim.ledger.accounting.domain.account.Account;

/** An account as clients see it. The balance is a string, so money is never read as a float. */
record AccountResponse(UUID id, String currency, String status, String balance, Instant createdAt) {

    static AccountResponse from(Account account) {
        return new AccountResponse(account.id(), account.currency().getCurrencyCode(), account.status().name(),
                account.balance().amount().toPlainString(), account.createdAt());
    }
}
