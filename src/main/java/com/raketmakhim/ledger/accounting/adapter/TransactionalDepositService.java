package com.raketmakhim.ledger.accounting.adapter;

import java.time.Clock;
import java.util.UUID;

import com.raketmakhim.ledger.accounting.application.AccountRepository;
import com.raketmakhim.ledger.accounting.application.DepositService;
import com.raketmakhim.ledger.accounting.application.IdGenerator;
import com.raketmakhim.ledger.accounting.application.JournalEntryRepository;
import com.raketmakhim.ledger.accounting.domain.journal.JournalEntry;
import com.raketmakhim.ledger.accounting.domain.money.Money;

/** DepositService with its transaction and retry added, so the application package stays free of Spring. */
public class TransactionalDepositService extends DepositService {

    public TransactionalDepositService(AccountRepository accounts, JournalEntryRepository entries, IdGenerator ids,
                                       Clock clock) {
        super(accounts, entries, ids, clock);
    }

    @Override
    @RetriedTransaction
    public JournalEntry deposit(UUID accountId, Money amount, String reference) {
        return super.deposit(accountId, amount, reference);
    }
}
