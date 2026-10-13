package com.raketmakhim.ledger.accounting.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoneyTest {

    private static final Currency GBP = Currency.getInstance("GBP");

    @ParameterizedTest
    @CsvSource({
            "1.5,    GBP, 2",
            "1.50,   GBP, 2",
            "1.500,  GBP, 2",
            "1,      GBP, 2",
            "0,      GBP, 2",
            "-1.25,  GBP, 2",
            "5,      JPY, 0",
            "100,    JPY, 0"
    })
    void acceptsValidAmounts(String amount, String code, int expectedScale) {
        Money money = new Money(new BigDecimal(amount), Currency.getInstance(code));

        assertThat(money.amount().scale()).isEqualTo(expectedScale);
    }

    @ParameterizedTest
    @CsvSource({
            "1.505, GBP",
            "0.001, GBP",
            "5.5,   JPY",
            "0.1,   JPY"
    })
    void rejectsTooManyDecimals(String amount, String code) {
        BigDecimal value = new BigDecimal(amount);
        Currency currency = Currency.getInstance(code);

        assertThatThrownBy(() -> new Money(value, currency))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sameValueWithDifferentScaleIsEqual() {
        assertThat(new Money(new BigDecimal("1.0"), GBP))
                .isEqualTo(new Money(new BigDecimal("1.00"), GBP));
    }

    @Test
    void rejectsNullAmount() {
        assertThatThrownBy(() -> new Money(null, GBP))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNullCurrency() {
        assertThatThrownBy(() -> new Money(BigDecimal.ONE, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Money gbp(String amount) {
        return new Money(new BigDecimal(amount), GBP);
    }

    @Test
    void plusAddsAmounts() {
        assertThat(gbp("5.00").plus(gbp("2.50"))).isEqualTo(gbp("7.50"));
    }

    @Test
    void minusSubtractsAmountsAndCanGoNegative() {
        assertThat(gbp("2.50").minus(gbp("5.00"))).isEqualTo(gbp("-2.50"));
    }

    @Test
    void plusAndMinusRejectMixedCurrencies() {
        Money yen = new Money(BigDecimal.ONE, Currency.getInstance("JPY"));

        assertThatThrownBy(() -> gbp("1").plus(yen)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> gbp("1").minus(yen)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negateFlipsSign() {
        assertThat(gbp("5").negate()).isEqualTo(gbp("-5"));
        assertThat(gbp("-5").negate()).isEqualTo(gbp("5"));
    }

    @Test
    void zeroAndNegativeChecks() {
        assertThat(gbp("0").isZero()).isTrue();
        assertThat(gbp("0.01").isZero()).isFalse();
        assertThat(gbp("-0.01").isNegative()).isTrue();
        assertThat(gbp("0").isNegative()).isFalse();
        assertThat(gbp("0.01").isNegative()).isFalse();
    }

    @Test
    void compareToOrdersByAmount() {
        assertThat(gbp("1").compareTo(gbp("2"))).isNegative();
        assertThat(gbp("2").compareTo(gbp("1"))).isPositive();
        assertThat(gbp("1.0").compareTo(gbp("1.00"))).isZero();
    }

    @Test
    void compareToRejectsMixedCurrencies() {
        Money yen = new Money(BigDecimal.ONE, Currency.getInstance("JPY"));

        assertThatThrownBy(() -> gbp("1").compareTo(yen)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsPseudoCurrency() {
        Currency xxx = Currency.getInstance("XXX");

        assertThatThrownBy(() -> new Money(BigDecimal.ONE, xxx))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
