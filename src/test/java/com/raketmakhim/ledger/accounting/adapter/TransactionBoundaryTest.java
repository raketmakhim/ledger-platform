package com.raketmakhim.ledger.accounting.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatcher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import com.raketmakhim.ledger.TestcontainersConfiguration;
import com.raketmakhim.ledger.accounting.adapter.persistence.JpaAccountRepository;
import com.raketmakhim.ledger.accounting.adapter.persistence.JpaJournalEntryRepository;
import com.raketmakhim.ledger.accounting.application.DepositService;
import com.raketmakhim.ledger.accounting.application.IdGenerator;
import com.raketmakhim.ledger.accounting.application.JournalEntryRepository;
import com.raketmakhim.ledger.accounting.domain.account.Account;
import com.raketmakhim.ledger.accounting.domain.account.AccountStatus;
import com.raketmakhim.ledger.accounting.domain.account.AccountType;
import com.raketmakhim.ledger.accounting.domain.journal.JournalEntry;
import com.raketmakhim.ledger.accounting.domain.money.Money;

/**
 * The @Transactional and @Retryable wrappers, against a real Postgres. A test repository in front of the real one
 * lets a test make the last save of a deposit fail, and a spy on the transaction manager counts commits and
 * rollbacks. The key check is the order: each retry must be a fresh transaction (a rollback, then a commit),
 * not a repeat inside one.
 */
@Tag("integration")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TransactionBoundaryTest {

    private static final Currency GBP = Currency.getInstance("GBP");

    // The repositories' own @Transactional methods join the outer transaction and still call commit on the manager,
    // so only count the calls that belong to a transaction that was really started.
    private static final ArgumentMatcher<TransactionStatus> NEW_TRANSACTION = TransactionStatus::isNewTransaction;

    /** Saves go to the real repository, after a hook that a test can set to throw. */
    static class HookedEntries implements JournalEntryRepository {
        private final JpaJournalEntryRepository real;
        volatile Consumer<JournalEntry> beforeSave = entry -> { };
        volatile int saves;

        HookedEntries(JpaJournalEntryRepository real) {
            this.real = real;
        }

        @Override
        public void save(JournalEntry entry) {
            saves++;
            beforeSave.accept(entry);
            real.save(entry);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Hooks {
        @Bean
        @Primary
        HookedEntries hookedEntries(JpaJournalEntryRepository real) {
            return new HookedEntries(real);
        }
    }

    @Autowired
    DepositService deposits;

    @Autowired
    JpaAccountRepository accounts;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    IdGenerator ids;

    @Autowired
    HookedEntries hooked;

    @MockitoSpyBean
    PlatformTransactionManager transactionManager;

    @AfterEach
    void removeHook() {
        hooked.beforeSave = entry -> { };
        hooked.saves = 0;
    }

    private UUID newCustomer() {
        UUID id = ids.newId();
        accounts.save(new Account(id, GBP, AccountType.CUSTOMER, AccountStatus.ACTIVE, gbp("0"), 0, Instant.now()));
        return id;
    }

    private Money balance(UUID accountId) {
        return accounts.findById(accountId).orElseThrow().balance();
    }

    private static Money gbp(String amount) {
        return new Money(new BigDecimal(amount), GBP);
    }

    private int entryCount(String reference) {
        return jdbc.queryForObject("SELECT count(*) FROM journal_entry WHERE reference = ?", Integer.class, reference);
    }

    @Test
    void aDepositThatFailsHalfwayChangesNothing() {
        UUID customer = newCustomer();
        Money clearingBefore = accounts.findClearingAccount(GBP).orElseThrow().balance();
        hooked.beforeSave = entry -> {
            throw new IllegalStateException("the database went away");
        };

        assertThatThrownBy(() -> deposits.deposit(customer, gbp("100"), "TB-FAIL"))
                .isInstanceOf(IllegalStateException.class);

        assertThat(balance(customer)).isEqualTo(gbp("0"));
        assertThat(accounts.findClearingAccount(GBP).orElseThrow().balance()).isEqualTo(clearingBefore);
        assertThat(entryCount("TB-FAIL")).isZero();
    }

    @Test
    void aClashIsRetriedInAFreshTransaction() {
        UUID customer = newCustomer();
        hooked.beforeSave = entry -> {
            if (hooked.saves == 1) {
                throw new OptimisticLockingFailureException("clash");
            }
        };
        clearInvocations(transactionManager);

        deposits.deposit(customer, gbp("100"), "TB-RETRY");

        assertThat(balance(customer)).isEqualTo(gbp("100")); // applied once, not twice
        assertThat(entryCount("TB-RETRY")).isEqualTo(1);
        verify(transactionManager, times(1)).rollback(argThat(NEW_TRANSACTION)); // the first attempt
        verify(transactionManager, times(1)).commit(argThat(NEW_TRANSACTION));   // the second attempt, in its own transaction
    }

    @Test
    void givesUpAfterTheConfiguredAttemptsAndChangesNothing() {
        UUID customer = newCustomer();
        hooked.beforeSave = entry -> {
            throw new OptimisticLockingFailureException("clash");
        };
        clearInvocations(transactionManager);

        assertThatThrownBy(() -> deposits.deposit(customer, gbp("100"), "TB-GIVEUP"))
                .isInstanceOf(ConcurrencyFailureException.class);

        assertThat(hooked.saves).isEqualTo(8); // ledger.retry.max-attempts
        verify(transactionManager, times(8)).rollback(argThat(NEW_TRANSACTION));
        assertThat(balance(customer)).isEqualTo(gbp("0"));
        assertThat(entryCount("TB-GIVEUP")).isZero();
    }
}
