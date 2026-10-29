package com.raketmakhim.ledger.accounting.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.raketmakhim.ledger.TestcontainersConfiguration;
import com.raketmakhim.ledger.accounting.adapter.persistence.JpaAccountRepository;
import com.raketmakhim.ledger.accounting.application.DepositService;
import com.raketmakhim.ledger.accounting.application.IdGenerator;
import com.raketmakhim.ledger.accounting.application.TransferService;
import com.raketmakhim.ledger.accounting.domain.account.Account;
import com.raketmakhim.ledger.accounting.domain.account.AccountStatus;
import com.raketmakhim.ledger.accounting.domain.account.AccountType;
import com.raketmakhim.ledger.accounting.domain.account.InsufficientFundsException;
import com.raketmakhim.ledger.accounting.domain.journal.DuplicateReferenceException;
import com.raketmakhim.ledger.accounting.domain.money.Money;

/**
 * Many requests at once against a real Postgres. Whatever order they run in, no money may be created or lost.
 * Each request may succeed, run out of retries (ConcurrencyFailureException), or find too little money:
 * the tests check the books against the requests that succeeded.
 */
@Tag("integration")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ConcurrencyTest {

    private static final Currency GBP = Currency.getInstance("GBP");

    @Autowired
    JpaAccountRepository accounts;

    @Autowired
    TransferService transfers;

    @Autowired
    DepositService deposits;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    IdGenerator ids;

    private UUID newCustomer(String balance) {
        UUID id = ids.newId();
        accounts.save(new Account(id, GBP, AccountType.CUSTOMER, AccountStatus.ACTIVE, gbp(balance), 0, Instant.now()));
        return id;
    }

    private Money balance(UUID accountId) {
        return accounts.findById(accountId).orElseThrow().balance();
    }

    private static Money gbp(String amount) {
        return new Money(new BigDecimal(amount), GBP);
    }

    private record Outcome(int succeeded, int notEnoughMoney, int gaveUp, int duplicates) {
    }

    /** Runs every task at the same moment on its own thread, and counts how each one ended. */
    private static Outcome runTogether(List<Runnable> tasks) throws Exception {
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger succeeded = new AtomicInteger();
        AtomicInteger notEnoughMoney = new AtomicInteger();
        AtomicInteger gaveUp = new AtomicInteger();
        AtomicInteger duplicates = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (Runnable task : tasks) {
                futures.add(pool.submit((Callable<Void>) () -> {
                    go.await();
                    try {
                        task.run();
                        succeeded.incrementAndGet();
                    } catch (InsufficientFundsException e) {
                        notEnoughMoney.incrementAndGet();
                    } catch (ConcurrencyFailureException e) {
                        gaveUp.incrementAndGet();
                    } catch (DuplicateReferenceException e) {
                        duplicates.incrementAndGet();
                    }
                    return null;
                }));
            }
            go.countDown();
            for (Future<?> future : futures) {
                future.get(); // any other exception fails the test here
            }
        } finally {
            pool.shutdownNow();
        }
        return new Outcome(succeeded.get(), notEnoughMoney.get(), gaveUp.get(), duplicates.get());
    }

    @Test
    void manyDepositsIntoOneAccountAreAllCountedExactlyOnce() throws Exception {
        UUID customer = newCustomer("0");
        Money clearingBefore = accounts.findClearingAccount(GBP).orElseThrow().balance();
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String reference = "D-" + i;
            tasks.add(() -> deposits.deposit(customer, gbp("10"), reference));
        }

        Outcome outcome = runTogether(tasks);

        Money deposited = gbp("10").plus(gbp("0"));
        BigDecimal expected = deposited.amount().multiply(BigDecimal.valueOf(outcome.succeeded()));
        assertThat(outcome.succeeded()).isPositive();
        assertThat(balance(customer).amount()).isEqualByComparingTo(expected);
        assertThat(accounts.findClearingAccount(GBP).orElseThrow().balance().minus(clearingBefore).amount())
                .isEqualByComparingTo(expected.negate());
    }

    @Test
    void manyTransfersOutOfOneAccountCanNeverSpendMoreThanItHas() throws Exception {
        UUID from = newCustomer("100");
        UUID to = newCustomer("0");
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String reference = "S-" + i;
            tasks.add(() -> transfers.transfer(from, to, gbp("10"), reference));
        }

        Outcome outcome = runTogether(tasks);

        assertThat(outcome.succeeded()).isBetween(1, 10);
        assertThat(balance(from).amount()).isEqualByComparingTo(BigDecimal.valueOf(100 - 10L * outcome.succeeded()));
        assertThat(balance(to).amount()).isEqualByComparingTo(BigDecimal.valueOf(10L * outcome.succeeded()));
    }

    @Test
    void transfersInOppositeDirectionsFinishAndKeepTheTotal() {
        UUID a = newCustomer("1000");
        UUID b = newCustomer("1000");
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String reference = "O-" + i;
            tasks.add(() -> transfers.transfer(a, b, gbp("10"), reference));
            tasks.add(() -> transfers.transfer(b, a, gbp("5"), reference + "b"));
        }

        Outcome[] outcome = new Outcome[1];
        assertTimeoutPreemptively(Duration.ofSeconds(60), () -> outcome[0] = runTogether(tasks));

        assertThat(balance(a).plus(balance(b)).amount()).isEqualByComparingTo("2000");
        assertThat(outcome[0].succeeded()).isPositive();
        assertThat(entryCount("O-%")).isEqualTo(outcome[0].succeeded());
    }

    @Test
    void manyRequestsWithTheSameReferenceMoveMoneyAtMostOnce() throws Exception {
        UUID from = newCustomer("1000");
        UUID to = newCustomer("0");
        List<Runnable> tasks = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            tasks.add(() -> transfers.transfer(from, to, gbp("10"), "SAME-REF"));
        }

        Outcome outcome = runTogether(tasks);

        assertThat(outcome.succeeded()).isLessThanOrEqualTo(1);
        assertThat(outcome.succeeded() + outcome.duplicates() + outcome.gaveUp()).isEqualTo(10);
        assertThat(balance(to).amount()).isEqualByComparingTo(BigDecimal.valueOf(10L * outcome.succeeded()));
        assertThat(entryCount("SAME-REF")).isEqualTo(outcome.succeeded());
    }

    private int entryCount(String referencePattern) {
        return jdbc.queryForObject("SELECT count(*) FROM journal_entry WHERE reference LIKE ?", Integer.class, referencePattern);
    }
}
