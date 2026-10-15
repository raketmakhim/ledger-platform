package com.raketmakhim.ledger.accounting.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class JournalEntryTest {

    private static final Currency GBP = Currency.getInstance("GBP");
    private static final Currency USD = Currency.getInstance("USD");

    private static Posting posting(String amount, Currency currency) {
        return new Posting(UUID.randomUUID(), UUID.randomUUID(), new Money(new BigDecimal(amount), currency));
    }

    private static JournalEntry entry(List<Posting> postings) {
        return new JournalEntry(UUID.randomUUID(), EntryType.TRANSFER, "ref-1", Instant.now(), postings);
    }

    @Test
    void buildsWhenPostingsBalance() {
        assertThat(entry(List.of(posting("-50", GBP), posting("50", GBP)))).isNotNull();
    }

    @Test
    void buildsWithThreePostingsThatBalance() {
        assertThat(entry(List.of(posting("-100", GBP), posting("90", GBP), posting("10", GBP)))).isNotNull();
    }

    @Test
    void rejectsUnbalancedPostings() {
        assertThatThrownBy(() -> entry(List.of(posting("-50", GBP), posting("40", GBP))))
                .isInstanceOf(UnbalancedEntryException.class);
    }

    @Test
    void rejectsSinglePosting() {
        assertThatThrownBy(() -> entry(List.of(posting("50", GBP))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildsWhenEachCurrencyBalancesSeparately() {
        assertThat(entry(List.of(posting("-50", GBP), posting("50", GBP), posting("-30", USD), posting("30", USD))))
                .isNotNull();
    }

    @Test
    void rejectsPostingsThatOnlyBalanceAcrossCurrencies() {
        assertThatThrownBy(() -> entry(List.of(posting("-50", GBP), posting("50", USD))))
                .isInstanceOf(UnbalancedEntryException.class);
    }

    @Test
    void unbalancedExceptionCarriesCurrencyAndTotal() {
        assertThatThrownBy(() -> entry(List.of(posting("-50", GBP), posting("40", GBP))))
                .isInstanceOfSatisfying(UnbalancedEntryException.class, e -> {
                    assertThat(e.currency()).isEqualTo(GBP);
                    assertThat(e.total()).isEqualByComparingTo("-10");
                });
    }

    @Test
    void rejectsNullsAndBlanks() {
        List<Posting> ok = List.of(posting("-5", GBP), posting("5", GBP));
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        assertThatThrownBy(() -> new JournalEntry(null, EntryType.TRANSFER, "r", now, ok)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JournalEntry(id, null, "r", now, ok)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JournalEntry(id, EntryType.TRANSFER, null, now, ok)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JournalEntry(id, EntryType.TRANSFER, "r", null, ok)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JournalEntry(id, EntryType.TRANSFER, "r", now, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new JournalEntry(id, EntryType.TRANSFER, " ", now, ok)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void acceptsReferenceUpToTheLimitAndRejectsLonger() {
        List<Posting> ok = List.of(posting("-5", GBP), posting("5", GBP));
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();

        assertThat(new JournalEntry(id, EntryType.TRANSFER, "a".repeat(36), now, ok)).isNotNull();
        assertThatThrownBy(() -> new JournalEntry(id, EntryType.TRANSFER, "a".repeat(37), now, ok))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void postingsCannotBeChangedAfterBuilding() {
        List<Posting> mutable = new ArrayList<>(List.of(posting("-5", GBP), posting("5", GBP)));
        JournalEntry entry = entry(mutable);

        mutable.add(posting("99", GBP));

        assertThat(entry.postings()).hasSize(2);
        assertThatThrownBy(() -> entry.postings().add(posting("1", GBP)))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
