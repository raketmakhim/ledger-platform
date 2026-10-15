package com.raketmakhim.ledger.accounting.domain.journal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.raketmakhim.ledger.accounting.domain.money.Money;

class PostingTest {

    private static final Currency GBP = Currency.getInstance("GBP");
    private static final Money FIVE = new Money(new BigDecimal("5"), GBP);

    @Test
    void acceptsCreditAndDebit() {
        assertThat(new Posting(UUID.randomUUID(), UUID.randomUUID(), FIVE).amount()).isEqualTo(FIVE);
        assertThat(new Posting(UUID.randomUUID(), UUID.randomUUID(), FIVE.negate()).amount()).isEqualTo(FIVE.negate());
    }

    @Test
    void rejectsNulls() {
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> new Posting(null, id, FIVE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Posting(id, null, FIVE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Posting(id, id, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsZeroAmount() {
        Money zero = new Money(BigDecimal.ZERO, GBP);

        assertThatThrownBy(() -> new Posting(UUID.randomUUID(), UUID.randomUUID(), zero))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
