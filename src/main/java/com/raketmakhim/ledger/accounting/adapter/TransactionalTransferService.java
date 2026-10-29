package com.raketmakhim.ledger.accounting.adapter;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.raketmakhim.ledger.accounting.application.AccountRepository;
import com.raketmakhim.ledger.accounting.application.IdGenerator;
import com.raketmakhim.ledger.accounting.application.JournalEntryRepository;
import com.raketmakhim.ledger.accounting.application.TransferService;
import com.raketmakhim.ledger.accounting.domain.journal.JournalEntry;
import com.raketmakhim.ledger.accounting.domain.money.Money;

/** TransferService with its transaction and retry added, so the application package stays free of Spring. */
@Service
public class TransactionalTransferService extends TransferService {

    public TransactionalTransferService(AccountRepository accounts, JournalEntryRepository entries, IdGenerator ids,
                                        Clock clock) {
        super(accounts, entries, ids, clock);
    }

    @Override
    @RetriedTransaction
    public JournalEntry transfer(UUID callerId, UUID fromId, UUID toId, Money amount, String reference) {
        return super.transfer(callerId, fromId, toId, amount, reference);
    }
}
