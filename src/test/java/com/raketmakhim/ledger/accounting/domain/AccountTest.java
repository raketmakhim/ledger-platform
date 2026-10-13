package com.raketmakhim.ledger.accounting.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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

    static Stream<Arguments> accountsWithOneNullField() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Money zero = gbp("0");
        return Stream.of(
                Arguments.of("id", (Executable) () -> new Account(null, GBP, AccountType.CUSTOMER, AccountStatus.ACTIVE, zero, 0, now)),
                Arguments.of("currency", (Executable) () -> new Account(id, null, AccountType.CUSTOMER, AccountStatus.ACTIVE, zero, 0, now)),
                Arguments.of("type", (Executable) () -> new Account(id, GBP, null, AccountStatus.ACTIVE, zero, 0, now)),
                Arguments.of("status", (Executable) () -> new Account(id, GBP, AccountType.CUSTOMER, null, zero, 0, now)),
                Arguments.of("balance", (Executable) () -> new Account(id, GBP, AccountType.CUSTOMER, AccountStatus.ACTIVE, null, 0, now)),
                Arguments.of("createdAt", (Executable) () -> new Account(id, GBP, AccountType.CUSTOMER, AccountStatus.ACTIVE, zero, 0, null)));
    }

    @ParameterizedTest(name = "{0} null is rejected")
    @MethodSource("accountsWithOneNullField")
    void rejectsNullFields(String field, Executable construct) {
        assertThatThrownBy(construct::execute).isInstanceOf(IllegalArgumentException.class);
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
