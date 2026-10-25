package com.raketmakhim.ledger.accounting.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import com.raketmakhim.ledger.accounting.application.ConcurrentUpdateException;

class SpringTransactionRunnerTest {

    /** A transaction manager that only counts, so the retry logic is tested without a database. */
    private static class CountingTransactionManager implements PlatformTransactionManager {
        int commits;
        int rollbacks;
        int commitsThatFail;

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {
            if (commitsThatFail > 0) {
                commitsThatFail--;
                throw new ConcurrencyFailureException("someone else saved first");
            }
            commits++;
        }

        @Override
        public void rollback(TransactionStatus status) {
            rollbacks++;
        }
    }

    private final CountingTransactionManager manager = new CountingTransactionManager();
    private final SpringTransactionRunner runner = new SpringTransactionRunner(manager, 3, 0);

    @Test
    void returnsTheResultAndCommitsOnce() {
        String result = runner.inTransaction(() -> "done");

        assertThat(result).isEqualTo("done");
        assertThat(manager.commits).isEqualTo(1);
        assertThat(manager.rollbacks).isZero();
    }

    @Test
    void runsTheWorkAgainWhenItFailsBecauseOfAClash() {
        AtomicInteger runs = new AtomicInteger();

        String result = runner.inTransaction(() -> {
            if (runs.incrementAndGet() < 3) {
                throw new ConcurrencyFailureException("clash");
            }
            return "done";
        });

        assertThat(result).isEqualTo("done");
        assertThat(runs).hasValue(3);
        assertThat(manager.rollbacks).isEqualTo(2);
        assertThat(manager.commits).isEqualTo(1);
    }

    @Test
    void runsTheWorkAgainWhenTheCommitFailsBecauseOfAClash() {
        manager.commitsThatFail = 1;
        AtomicInteger runs = new AtomicInteger();

        runner.inTransaction(runs::incrementAndGet);

        assertThat(runs).hasValue(2);
        assertThat(manager.commits).isEqualTo(1);
    }

    @Test
    void givesUpAfterTheMaximumNumberOfAttempts() {
        AtomicInteger runs = new AtomicInteger();

        assertThatThrownBy(() -> runner.inTransaction(() -> {
            runs.incrementAndGet();
            throw new ConcurrencyFailureException("clash");
        }))
                .isInstanceOf(ConcurrentUpdateException.class)
                .hasMessageContaining("3 attempts")
                .hasCauseInstanceOf(ConcurrencyFailureException.class);
        assertThat(runs).hasValue(3);
        assertThat(manager.commits).isZero();
    }

    @Test
    void doesNotRunAgainForOtherErrors() {
        AtomicInteger runs = new AtomicInteger();

        assertThatThrownBy(() -> runner.inTransaction(() -> {
            runs.incrementAndGet();
            throw new IllegalStateException("a real bug");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(runs).hasValue(1);
        assertThat(manager.rollbacks).isEqualTo(1);
    }

    @Test
    void keepsTheInterruptFlagIfInterruptedWhileWaiting() {
        AtomicInteger runs = new AtomicInteger();
        Thread.currentThread().interrupt();
        try {
            runner.inTransaction(() -> {
                if (runs.incrementAndGet() == 1) {
                    throw new ConcurrencyFailureException("clash");
                }
                return "done";
            });

            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            assertThat(runs).hasValue(2);
        } finally {
            Thread.interrupted(); // clear the flag so other tests are not affected
        }
    }
}
