package com.raketmakhim.ledger.accounting.adapter;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.raketmakhim.ledger.accounting.application.ConcurrentUpdateException;
import com.raketmakhim.ledger.accounting.application.TransactionRunner;

/**
 * Runs work in a Spring transaction (optimistic locking). When another request changes an account the work
 * has read, the commit fails with a ConcurrencyFailureException (a stale version, a deadlock, or a lock timeout),
 * and the whole piece of work is run again from the start, after a short random pause.
 */
@Component
public class SpringTransactionRunner implements TransactionRunner {

    private final TransactionTemplate transaction;
    private final int maxAttempts;
    private final long backoffMillis;

    public SpringTransactionRunner(
            PlatformTransactionManager transactionManager,
            @Value("${ledger.retry.max-attempts:8}") int maxAttempts,
            @Value("${ledger.retry.backoff-millis:10}") long backoffMillis) {
        this.transaction = new TransactionTemplate(transactionManager);
        this.maxAttempts = maxAttempts;
        this.backoffMillis = backoffMillis;
    }

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        for (int attempt = 1; ; attempt++) {
            try {
                return transaction.execute(status -> work.get());
            } catch (ConcurrencyFailureException e) {
                if (attempt == maxAttempts) {
                    throw new ConcurrentUpdateException(attempt, e);
                }
                pause(attempt);
            }
        }
    }

    // random (at least 1 ms), and longer each time, so requests that clashed do not retry in step and clash again
    private void pause(int attempt) {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(1, backoffMillis * attempt + 2));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
