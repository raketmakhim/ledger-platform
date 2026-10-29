package com.raketmakhim.ledger.accounting.adapter;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

import com.raketmakhim.ledger.accounting.application.AccountRepository;
import com.raketmakhim.ledger.accounting.application.AccountService;
import com.raketmakhim.ledger.accounting.application.IdGenerator;

/** Creates the beans that can't carry annotations (the JDK clock, and the plain AccountService). */
@Configuration
@EnableRetry
class LedgerConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    // one save, which has its own transaction, so no wrapper is needed
    @Bean
    AccountService accountService(AccountRepository accounts, IdGenerator ids, Clock clock) {
        return new AccountService(accounts, ids, clock);
    }
}
