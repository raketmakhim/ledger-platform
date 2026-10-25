package com.raketmakhim.ledger.accounting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.raketmakhim.ledger.accounting.domain.account.Account;
import com.raketmakhim.ledger.accounting.domain.account.AccountNotActiveException;
import com.raketmakhim.ledger.accounting.domain.account.AccountNotFoundException;
import com.raketmakhim.ledger.accounting.domain.account.AccountStatus;
import com.raketmakhim.ledger.accounting.domain.account.AccountType;
import com.raketmakhim.ledger.accounting.domain.journal.EntryType;
import com.raketmakhim.ledger.accounting.domain.journal.JournalEntry;
import com.raketmakhim.ledger.accounting.domain.money.CurrencyMismatchException;
import com.raketmakhim.ledger.accounting.domain.money.Money;
import com.raketmakhim.ledger.accounting.domain.money.UnsupportedCurrencyException;

class DepositServiceTest {

    private static final Currency GBP = Currency.getInstance("GBP");
    private static final Currency USD = Currency.getInstance("USD");
    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

    private static final UUID CUSTOMER_ID = new UUID(1, 1);
    private static final UUID CLEARING_ID = new UUID(1, 2);

    private final Map<UUID, Account> accounts = new HashMap<>();
    private final List<JournalEntry> savedEntries = new ArrayList<>();
    private long nextId = 100;
    private final DirectTransactionRunner transactions = new DirectTransactionRunner();
    private DepositService service;

    @BeforeEach
    void setUp() {
        AccountRepository accountRepository = new AccountRepository() {
            public Optional<Account> findById(UUID id) {
                return Optional.ofNullable(accounts.get(id));
            }

            public Optional<Account> findClearingAccount(Currency currency) {
                return accounts.values().stream()
                        .filter(a -> a.type() == AccountType.SYSTEM && a.currency().equals(currency))
                        .findFirst();
            }

            public void save(Account account) {
                accounts.put(account.id(), account);
            }
        };
        JournalEntryRepository entryRepository = savedEntries::add;
        IdGenerator ids = () -> new UUID(2, nextId++);
        service = new DepositService(accountRepository, entryRepository, ids, Clock.fixed(NOW, ZoneOffset.UTC),
                transactions);

        add(CUSTOMER_ID, GBP, AccountType.CUSTOMER, AccountStatus.ACTIVE);
        add(CLEARING_ID, GBP, AccountType.SYSTEM, AccountStatus.ACTIVE);
    }

    private void add(UUID id, Currency currency, AccountType type, AccountStatus status) {
        accounts.put(id, new Account(id, currency, type, status, new Money(BigDecimal.ZERO, currency), 0, NOW));
    }

    private static Money gbp(String amount) {
        return new Money(new BigDecimal(amount), GBP);
    }

    private void assertNothingChangedOrSaved() {
        assertThat(savedEntries).isEmpty();
        assertThat(accounts.get(CUSTOMER_ID).balance().isZero()).isTrue();
        assertThat(accounts.get(CLEARING_ID).balance().isZero()).isTrue();
    }

    @Test
    void depositMovesMoneyFromClearingToCustomer() {
        JournalEntry entry = service.deposit(CUSTOMER_ID, gbp("100"), "FPS-1");

        assertThat(accounts.get(CUSTOMER_ID).balance()).isEqualTo(gbp("100"));
        assertThat(accounts.get(CLEARING_ID).balance()).isEqualTo(gbp("-100"));
        assertThat(savedEntries).containsExactly(entry);
    }

    @Test
    void depositRunsInsideOneTransaction() {
        service.deposit(CUSTOMER_ID, gbp("100"), "FPS-1");

        assertThat(transactions.runs()).isEqualTo(1);
    }

    @Test
    void depositRecordsATimedDepositEntryWithTwoPostings() {
        JournalEntry entry = service.deposit(CUSTOMER_ID, gbp("100"), "FPS-1");

        assertThat(entry.type()).isEqualTo(EntryType.DEPOSIT);
        assertThat(entry.reference()).isEqualTo("FPS-1");
        assertThat(entry.createdAt()).isEqualTo(NOW);
        assertThat(entry.postings()).hasSize(2);
        assertThat(entry.postings()).anySatisfy(p -> {
            assertThat(p.accountId()).isEqualTo(CLEARING_ID);
            assertThat(p.amount()).isEqualTo(gbp("-100"));
        });
        assertThat(entry.postings()).anySatisfy(p -> {
            assertThat(p.accountId()).isEqualTo(CUSTOMER_ID);
            assertThat(p.amount()).isEqualTo(gbp("100"));
        });
    }

    @Test
    void rejectsZeroNegativeAndNullAmounts() {
        assertThatThrownBy(() -> service.deposit(CUSTOMER_ID, gbp("0"), "r")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.deposit(CUSTOMER_ID, gbp("-5"), "r")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.deposit(CUSTOMER_ID, null, "r")).isInstanceOf(IllegalArgumentException.class);
        assertNothingChangedOrSaved();
    }

    @Test
    void rejectsMissingAccount() {
        UUID missing = new UUID(9, 9);

        assertThatThrownBy(() -> service.deposit(missing, gbp("10"), "r"))
                .isInstanceOfSatisfying(AccountNotFoundException.class,
                        e -> assertThat(e.accountId()).isEqualTo(missing));
        assertNothingChangedOrSaved();
    }

    @Test
    void rejectsDepositIntoSystemAccount() {
        assertThatThrownBy(() -> service.deposit(CLEARING_ID, gbp("10"), "r")).isInstanceOf(IllegalArgumentException.class);
        assertNothingChangedOrSaved();
    }

    @Test
    void rejectsFrozenAccountAndSavesNothing() {
        add(CUSTOMER_ID, GBP, AccountType.CUSTOMER, AccountStatus.FROZEN);

        assertThatThrownBy(() -> service.deposit(CUSTOMER_ID, gbp("10"), "r")).isInstanceOf(AccountNotActiveException.class);
        assertNothingChangedOrSaved();
    }

    @Test
    void rejectsCurrencyThatDoesNotMatchTheAccountAndSavesNothing() {
        add(new UUID(1, 3), USD, AccountType.SYSTEM, AccountStatus.ACTIVE);

        assertThatThrownBy(() -> service.deposit(CUSTOMER_ID, new Money(BigDecimal.TEN, USD), "r"))
                .isInstanceOf(CurrencyMismatchException.class);
        assertNothingChangedOrSaved();
    }

    @Test
    void rejectsCurrencyWithNoClearingAccount() {
        accounts.remove(CLEARING_ID);

        assertThatThrownBy(() -> service.deposit(CUSTOMER_ID, gbp("10"), "r"))
                .isInstanceOfSatisfying(UnsupportedCurrencyException.class,
                        e -> assertThat(e.currency()).isEqualTo(GBP));
        assertThat(savedEntries).isEmpty();
    }

    @Test
    void rejectsReferenceThatIsTooLongAndSavesNothing() {
        assertThatThrownBy(() -> service.deposit(CUSTOMER_ID, gbp("10"), "a".repeat(37)))
                .isInstanceOf(IllegalArgumentException.class);
        assertNothingChangedOrSaved();
    }
}
