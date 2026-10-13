package com.raketmakhim.ledger.accounting.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class AccountTest {

    private static final Currency GBP = Currency.getInstance("GBP");
    private static final Currency USD = Currency.getInstance("USD");

    private static Money gbp(String amount) {
        return new Money(new BigDecimal(amount), GBP);
    }

    private static Account account(AccountType type, AccountStatus status, String balance) {
        return new Account(UUID.randomUUID(), GBP, type, status, gbp(balance), 0, Instant.now());
    }

    private static Posting posting(Account account, Money amount) {
        return new Posting(UUID.randomUUID(), account.id(), amount);
    }

    @Test
    void creditIncreasesBalance() {
        Account account = account(AccountType.CUSTOMER, AccountStatus.ACTIVE, "100");

        assertThat(account.applyPosting(posting(account, gbp("25"))).balance()).isEqualTo(gbp("125"));
    }

    @Test
    void debitDecreasesBalance() {
        Account account = account(AccountType.CUSTOMER, AccountStatus.ACTIVE, "100");

        assertThat(account.applyPosting(posting(account, gbp("-40"))).balance()).isEqualTo(gbp("60"));
    }

    @Test
    void applyLeavesOriginalAccountUnchanged() {
        Account account = account(AccountType.CUSTOMER, AccountStatus.ACTIVE, "100");

        account.applyPosting(posting(account, gbp("25")));

        assertThat(account.balance()).isEqualTo(gbp("100"));
    }

    @Test
    void customerCannotGoOverdrawn() {
        Account account = account(AccountType.CUSTOMER, AccountStatus.ACTIVE, "100");

        assertThatThrownBy(() -> account.applyPosting(posting(account, gbp("-100.01"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void customerCanBeEmptiedToZero() {
        Account account = account(AccountType.CUSTOMER, AccountStatus.ACTIVE, "100");

        assertThat(account.applyPosting(posting(account, gbp("-100"))).balance().isZero()).isTrue();
    }

    @Test
    void systemAccountCanGoNegative() {
        Account account = account(AccountType.SYSTEM, AccountStatus.ACTIVE, "0");

        assertThat(account.applyPosting(posting(account, gbp("-500"))).balance()).isEqualTo(gbp("-500"));
    }

    @Test
    void rejectsPostingsOnFrozenOrClosedAccounts() {
        Account frozen = account(AccountType.CUSTOMER, AccountStatus.FROZEN, "100");
        Account closed = account(AccountType.CUSTOMER, AccountStatus.CLOSED, "100");

        assertThatThrownBy(() -> frozen.applyPosting(posting(frozen, gbp("1")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> closed.applyPosting(posting(closed, gbp("1")))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPostingInOtherCurrency() {
        Account account = account(AccountType.CUSTOMER, AccountStatus.ACTIVE, "100");
        Money dollars = new Money(BigDecimal.ONE, USD);

        assertThatThrownBy(() -> account.applyPosting(posting(account, dollars))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPostingForAnotherAccount() {
        Account account = account(AccountType.CUSTOMER, AccountStatus.ACTIVE, "100");
        Posting other = new Posting(UUID.randomUUID(), UUID.randomUUID(), gbp("1"));

        assertThatThrownBy(() -> account.applyPosting(other)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsInvalidConstruction() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        assertThatThrownBy(() -> new Account(null, GBP, AccountType.CUSTOMER, AccountStatus.ACTIVE, gbp("0"), 0, now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Account(id, GBP, AccountType.CUSTOMER, AccountStatus.ACTIVE, new Money(BigDecimal.ZERO, USD), 0, now))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Account(id, GBP, AccountType.CUSTOMER, AccountStatus.ACTIVE, gbp("-1"), 0, now))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
