package com.raketmakhim.ledger.accounting.adapter;

import java.time.Clock;

import org.springframework.stereotype.Service;

import com.raketmakhim.ledger.accounting.application.AccountRepository;
import com.raketmakhim.ledger.accounting.application.IdGenerator;
import com.raketmakhim.ledger.accounting.application.IdempotencyRepository;
import com.raketmakhim.ledger.accounting.application.JournalEntryRepository;
import com.raketmakhim.ledger.accounting.application.TransferCommand;
import com.raketmakhim.ledger.accounting.application.TransferService;
import com.raketmakhim.ledger.accounting.domain.journal.JournalEntry;

/** TransferService with its transaction and retry added, so the application package stays free of Spring. */
@Service
public class TransactionalTransferService extends TransferService {

    public TransactionalTransferService(AccountRepository accounts, JournalEntryRepository entries,
                                        IdempotencyRepository keys, IdGenerator ids, Clock clock) {
        super(accounts, entries, keys, ids, clock);
    }

    @Override
    @RetriedTransaction
    public JournalEntry transfer(TransferCommand command) {
        return super.transfer(command);
    }
}
