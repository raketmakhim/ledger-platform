package com.raketmakhim.ledger.accounting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.raketmakhim.ledger.accounting.domain.account.Account;
import com.raketmakhim.ledger.accounting.domain.account.AccountNotFoundException;
import com.raketmakhim.ledger.accounting.domain.account.AccountStatus;
import com.raketmakhim.ledger.accounting.domain.account.AccountType;
import com.raketmakhim.ledger.accounting.domain.money.Money;
import com.raketmakhim.ledger.accounting.domain.money.UnsupportedCurrencyException;

class AccountServiceTest {

    private static final Currency GBP = Currency.getInstance("GBP");
    private static final Currency JPY = Currency.getInstance("JPY");
    private static final Currency CHF = Currency.getInstance("CHF");
    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

    private static final UUID ALICE_OWNER = new UUID(7, 1);
    private static final UUID BOB_OWNER = new UUID(7, 2);

    // in the order they were saved, like a list sorted by creation
    private final Map<UUID, Account> accounts = new LinkedHashMap<>();
    private long nextId = 100;
    private AccountService service;

    @BeforeEach
    void setUp() {
        AccountRepository repository = new AccountRepository() {
            public Optional<Account> findById(UUID id) {
                return Optional.ofNullable(accounts.get(id));
            }

            // GBP and JPY are the currencies this bank supports
            public Optional<Account> findClearingAccount(Currency currency) {
                if (currency.equals(GBP) || currency.equals(JPY)) {
                    return Optional.of(new Account(new UUID(0, 1), null, currency, AccountType.SYSTEM, AccountStatus.ACTIVE,
                            new Money(BigDecimal.ZERO, currency), 0, NOW));
                }
                return Optional.empty();
            }

            public List<Account> findByOwner(UUID ownerId) {
                return accounts.values().stream().filter(a -> ownerId.equals(a.ownerId())).toList();
            }

            public void save(Account account) {
                accounts.put(account.id(), account);
            }
        };
        IdGenerator ids = () -> new UUID(2, nextId++);
        service = new AccountService(repository, ids, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    // --- opening an account ---

    @Test
    void opensAnActiveCustomerAccountWithNothingInIt() {
        Account account = service.open(ALICE_OWNER, GBP);

        assertThat(account.ownerId()).isEqualTo(ALICE_OWNER);
        assertThat(account.currency()).isEqualTo(GBP);
        assertThat(account.type()).isEqualTo(AccountType.CUSTOMER);
        assertThat(account.status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(account.balance()).isEqualTo(new Money(BigDecimal.ZERO, GBP));
        assertThat(account.version()).isZero();
        assertThat(account.createdAt()).isEqualTo(NOW);
    }

    @Test
    void savesTheNewAccountUnderANewId() {
        Account account = service.open(ALICE_OWNER, GBP);

        assertThat(account.id()).isEqualTo(new UUID(2, 100));
        assertThat(accounts).containsEntry(account.id(), account);
    }

    @Test
    void theBalanceUsesTheScaleOfTheCurrency() {
        assertThat(service.open(ALICE_OWNER, GBP).balance().amount().scale()).isEqualTo(2);
        assertThat(service.open(ALICE_OWNER, JPY).balance().amount().scale()).isZero();
    }

    @Test
    void anOwnerCanOpenSeveralAccountsEvenInTheSameCurrency() {
        Account first = service.open(ALICE_OWNER, GBP);
        Account second = service.open(ALICE_OWNER, GBP);

        assertThat(first.id()).isNotEqualTo(second.id());
        assertThat(accounts).hasSize(2);
    }

    @Test
    void refusesACurrencyTheBankDoesNotSupportAndSavesNothing() {
        assertThatThrownBy(() -> service.open(ALICE_OWNER, CHF))
                .isInstanceOfSatisfying(UnsupportedCurrencyException.class,
                        e -> assertThat(e.currency()).isEqualTo(CHF));
        assertThat(accounts).isEmpty();
    }

    @Test
    void refusesAMissingOwnerOrCurrencyAndSavesNothing() {
        assertThatThrownBy(() -> service.open(null, GBP)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.open(ALICE_OWNER, null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(accounts).isEmpty();
    }

    // --- looking at one account ---

    @Test
    void ownersCanSeeTheirOwnAccount() {
        Account opened = service.open(ALICE_OWNER, GBP);

        assertThat(service.get(ALICE_OWNER, opened.id())).isEqualTo(opened);
    }

    @Test
    void someoneElsesAccountIsNotFound() {
        Account opened = service.open(ALICE_OWNER, GBP);

        assertThatThrownBy(() -> service.get(BOB_OWNER, opened.id()))
                .isInstanceOfSatisfying(AccountNotFoundException.class,
                        e -> assertThat(e.accountId()).isEqualTo(opened.id()));
    }

    @Test
    void anUnknownAccountIsNotFound() {
        UUID missing = new UUID(9, 9);

        assertThatThrownBy(() -> service.get(ALICE_OWNER, missing))
                .isInstanceOfSatisfying(AccountNotFoundException.class,
                        e -> assertThat(e.accountId()).isEqualTo(missing));
    }

    @Test
    void theBanksOwnAccountIsNotFoundForAnyone() {
        UUID clearing = new UUID(0, 1);
        accounts.put(clearing, new Account(clearing, null, GBP, AccountType.SYSTEM, AccountStatus.ACTIVE,
                new Money(BigDecimal.ZERO, GBP), 0, NOW));

        assertThatThrownBy(() -> service.get(ALICE_OWNER, clearing)).isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void refusesAMissingCallerOrAccountId() {
        assertThatThrownBy(() -> service.get(null, new UUID(9, 9))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.get(ALICE_OWNER, null)).isInstanceOf(IllegalArgumentException.class);
    }

    // --- listing ---

    @Test
    void listsOnlyTheCallersAccountsInTheOrderTheyWereOpened() {
        Account first = service.open(ALICE_OWNER, GBP);
        service.open(BOB_OWNER, GBP);
        Account third = service.open(ALICE_OWNER, JPY);

        assertThat(service.list(ALICE_OWNER)).containsExactly(first, third);
    }

    @Test
    void aCallerWithNoAccountsGetsAnEmptyList() {
        service.open(BOB_OWNER, GBP);

        assertThat(service.list(ALICE_OWNER)).isEmpty();
    }

    @Test
    void refusesAMissingCallerWhenListing() {
        assertThatThrownBy(() -> service.list(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
