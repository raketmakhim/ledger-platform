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
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.raketmakhim.ledger.accounting.domain.account.Account;
import com.raketmakhim.ledger.accounting.domain.account.AccountNotActiveException;
import com.raketmakhim.ledger.accounting.domain.account.AccountNotFoundException;
import com.raketmakhim.ledger.accounting.domain.account.AccountStatus;
import com.raketmakhim.ledger.accounting.domain.account.AccountType;
import com.raketmakhim.ledger.accounting.domain.account.InsufficientFundsException;
import com.raketmakhim.ledger.accounting.domain.journal.EntryType;
import com.raketmakhim.ledger.accounting.domain.journal.JournalEntry;
import com.raketmakhim.ledger.accounting.domain.money.CurrencyMismatchException;
import com.raketmakhim.ledger.accounting.domain.money.Money;

class TransferServiceTest {

    private static final Currency GBP = Currency.getInstance("GBP");
    private static final Currency USD = Currency.getInstance("USD");
    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

    private static final UUID ALICE = new UUID(1, 1);
    private static final UUID BOB = new UUID(1, 2);
    private static final UUID CLEARING = new UUID(1, 3);

    private final Map<UUID, Account> accounts = new HashMap<>();
    private final List<JournalEntry> savedEntries = new ArrayList<>();
    private long nextId = 100;
    private TransferService service;

    @BeforeEach
    void setUp() {
        AccountRepository accountRepository = new AccountRepository() {
            public Optional<Account> findById(UUID id) {
                return Optional.ofNullable(accounts.get(id));
            }

            public Optional<Account> findClearingAccount(Currency currency) {
                return Optional.empty();
            }

            public void save(Account account) {
                accounts.put(account.id(), account);
            }
        };
        JournalEntryRepository entryRepository = savedEntries::add;
        IdGenerator ids = () -> new UUID(2, nextId++);
        service = new TransferService(accountRepository, entryRepository, ids, Clock.fixed(NOW, ZoneOffset.UTC));

        add(ALICE, GBP, AccountType.CUSTOMER, AccountStatus.ACTIVE, "100");
        add(BOB, GBP, AccountType.CUSTOMER, AccountStatus.ACTIVE, "0");
        add(CLEARING, GBP, AccountType.SYSTEM, AccountStatus.ACTIVE, "0");
    }

    private void add(UUID id, Currency currency, AccountType type, AccountStatus status, String balance) {
        accounts.put(id, new Account(id, currency, type, status, new Money(new BigDecimal(balance), currency), 0, NOW));
    }

    private static Money gbp(String amount) {
        return new Money(new BigDecimal(amount), GBP);
    }

    private void assertNothingChangedOrSaved() {
        assertThat(savedEntries).isEmpty();
        assertThat(accounts.get(ALICE).balance()).isEqualTo(gbp("100"));
        assertThat(accounts.get(BOB).balance()).isEqualTo(gbp("0"));
    }

    @Test
    void transferMovesMoneyBetweenCustomers() {
        JournalEntry entry = service.transfer(ALICE, BOB, gbp("40"), "T-1");

        assertThat(accounts.get(ALICE).balance()).isEqualTo(gbp("60"));
        assertThat(accounts.get(BOB).balance()).isEqualTo(gbp("40"));
        assertThat(savedEntries).containsExactly(entry);
    }

    @Test
    void transferRecordsATimedTransferEntryWithTwoPostings() {
        JournalEntry entry = service.transfer(ALICE, BOB, gbp("40"), "T-1");

        assertThat(entry.type()).isEqualTo(EntryType.TRANSFER);
        assertThat(entry.reference()).isEqualTo("T-1");
        assertThat(entry.createdAt()).isEqualTo(NOW);
        assertThat(entry.postings()).hasSize(2);
        assertThat(entry.postings()).anySatisfy(p -> {
            assertThat(p.accountId()).isEqualTo(ALICE);
            assertThat(p.amount()).isEqualTo(gbp("-40"));
        });
        assertThat(entry.postings()).anySatisfy(p -> {
            assertThat(p.accountId()).isEqualTo(BOB);
            assertThat(p.amount()).isEqualTo(gbp("40"));
        });
    }

    @Test
    void allowsTransferOfTheWholeBalance() {
        service.transfer(ALICE, BOB, gbp("100"), "T-1");

        assertThat(accounts.get(ALICE).balance().isZero()).isTrue();
    }

    static Stream<Money> badAmounts() {
        return Stream.of(gbp("0"), gbp("-5"), null);
    }

    @ParameterizedTest
    @MethodSource("badAmounts")
    void rejectsZeroNegativeAndNullAmounts(Money amount) {
        assertThatThrownBy(() -> service.transfer(ALICE, BOB, amount, "r")).isInstanceOf(IllegalArgumentException.class);
        assertNothingChangedOrSaved();
    }

    static Stream<Arguments> badAccountPairs() {
        return Stream.of(
                Arguments.of(null, BOB),
                Arguments.of(ALICE, null),
                Arguments.of(ALICE, ALICE),
                Arguments.of(CLEARING, BOB),
                Arguments.of(ALICE, CLEARING));
    }

    @ParameterizedTest
    @MethodSource("badAccountPairs")
    void rejectsNullSameAndSystemAccounts(UUID from, UUID to) {
        assertThatThrownBy(() -> service.transfer(from, to, gbp("10"), "r")).isInstanceOf(IllegalArgumentException.class);
        assertNothingChangedOrSaved();
    }

    @ParameterizedTest
    @MethodSource("customerIds")
    void rejectsFrozenSourceOrDestinationAndSavesNothing(UUID frozen) {
        add(frozen, GBP, AccountType.CUSTOMER, AccountStatus.FROZEN, accounts.get(frozen).balance().amount().toPlainString());

        assertThatThrownBy(() -> service.transfer(ALICE, BOB, gbp("10"), "r")).isInstanceOf(AccountNotActiveException.class);
        assertNothingChangedOrSaved();
    }

    static Stream<UUID> customerIds() {
        return Stream.of(ALICE, BOB);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    void rejectsNullAndBlankReferenceAndSavesNothing(String reference) {
        assertThatThrownBy(() -> service.transfer(ALICE, BOB, gbp("10"), reference)).isInstanceOf(IllegalArgumentException.class);
        assertNothingChangedOrSaved();
    }

    @Test
    void rejectsMissingSourceAndDestination() {
        UUID missing = new UUID(9, 9);

        assertThatThrownBy(() -> service.transfer(missing, BOB, gbp("10"), "r"))
                .isInstanceOfSatisfying(AccountNotFoundException.class,
                        e -> assertThat(e.accountId()).isEqualTo(missing));
        assertThatThrownBy(() -> service.transfer(ALICE, missing, gbp("10"), "r"))
                .isInstanceOf(AccountNotFoundException.class);
        assertNothingChangedOrSaved();
    }

    @Test
    void rejectsInsufficientFundsAndSavesNothing() {
        assertThatThrownBy(() -> service.transfer(ALICE, BOB, gbp("100.01"), "r"))
                .isInstanceOf(InsufficientFundsException.class);
        assertNothingChangedOrSaved();
    }

    @Test
    void transferCreatesNoMoneyAndDestroysNone() {
        Money before = accounts.get(ALICE).balance().plus(accounts.get(BOB).balance());

        service.transfer(ALICE, BOB, gbp("33.33"), "T-1");

        assertThat(accounts.get(ALICE).balance().plus(accounts.get(BOB).balance())).isEqualTo(before);
    }

    @Test
    void rejectsCurrencyThatDoesNotMatchTheAccountsAndSavesNothing() {
        assertThatThrownBy(() -> service.transfer(ALICE, BOB, new Money(BigDecimal.TEN, USD), "r"))
                .isInstanceOf(CurrencyMismatchException.class);
        assertNothingChangedOrSaved();
    }

    @Test
    void rejectsReferenceThatIsTooLongAndSavesNothing() {
        assertThatThrownBy(() -> service.transfer(ALICE, BOB, gbp("10"), "a".repeat(37)))
                .isInstanceOf(IllegalArgumentException.class);
        assertNothingChangedOrSaved();
    }
}
