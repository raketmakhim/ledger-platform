package com.raketmakhim.ledger.accounting.adapter;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

import com.raketmakhim.ledger.accounting.application.AccountRepository;
import com.raketmakhim.ledger.accounting.application.AccountService;
import com.raketmakhim.ledger.accounting.application.DepositService;
import com.raketmakhim.ledger.accounting.application.IdGenerator;
import com.raketmakhim.ledger.accounting.application.JournalEntryRepository;
import com.raketmakhim.ledger.accounting.application.TransferService;

/** Creates the application services here, so the application package stays free of Spring. */
@Configuration
@EnableRetry
class LedgerConfig {

    @Bean
    IdGenerator idGenerator() {
        return new UuidV7IdGenerator();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    // one save, which has its own transaction, so no wrapper is needed
    @Bean
    AccountService accountService(AccountRepository accounts, IdGenerator ids, Clock clock) {
        return new AccountService(accounts, ids, clock);
    }

    @Bean
    TransferService transferService(AccountRepository accounts, JournalEntryRepository entries, IdGenerator ids,
                                    Clock clock) {
        return new TransactionalTransferService(accounts, entries, ids, clock);
    }

    @Bean
    DepositService depositService(AccountRepository accounts, JournalEntryRepository entries, IdGenerator ids,
                                  Clock clock) {
        return new TransactionalDepositService(accounts, entries, ids, clock);
    }
}
