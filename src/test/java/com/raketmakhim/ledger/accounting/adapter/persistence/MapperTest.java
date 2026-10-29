package com.raketmakhim.ledger.accounting.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.raketmakhim.ledger.accounting.domain.account.Account;
import com.raketmakhim.ledger.accounting.domain.account.AccountStatus;
import com.raketmakhim.ledger.accounting.domain.account.AccountType;
import com.raketmakhim.ledger.accounting.domain.journal.EntryType;
import com.raketmakhim.ledger.accounting.domain.journal.JournalEntry;
import com.raketmakhim.ledger.accounting.domain.journal.Posting;
import com.raketmakhim.ledger.accounting.domain.money.Money;

class MapperTest {

    private static final Currency GBP = Currency.getInstance("GBP");
    private static final Currency JPY = Currency.getInstance("JPY");
    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

    private static final UUID OWNER = new UUID(5, 5);

    @Test
    void accountSurvivesTheRoundTrip() {
        Account account = new Account(new UUID(1, 1), OWNER, GBP, AccountType.CUSTOMER, AccountStatus.FROZEN,
                new Money(new BigDecimal("12.34"), GBP), 7, NOW);

        assertThat(AccountMapper.toDomain(AccountMapper.toEntity(account))).isEqualTo(account);
    }

    @Test
    void anAccountWithoutAnOwnerSurvivesTheRoundTrip() {
        Account clearing = new Account(new UUID(1, 2), null, GBP, AccountType.SYSTEM, AccountStatus.ACTIVE,
                new Money(new BigDecimal("-5.00"), GBP), 0, NOW);

        assertThat(AccountMapper.toDomain(AccountMapper.toEntity(clearing))).isEqualTo(clearing);
    }

    @Test
    void accountEntityHoldsTheColumnValues() {
        Account account = new Account(new UUID(1, 1), OWNER, GBP, AccountType.CUSTOMER, AccountStatus.ACTIVE,
                new Money(new BigDecimal("5.00"), GBP), 3, NOW);

        AccountEntity entity = AccountMapper.toEntity(account);

        assertThat(entity.getOwnerId()).isEqualTo(OWNER);
        assertThat(entity.getId()).isEqualTo(account.id());
        assertThat(entity.getCurrency()).isEqualTo("GBP");
        assertThat(entity.getType()).isEqualTo(AccountType.CUSTOMER);
        assertThat(entity.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(entity.getBalance()).isEqualByComparingTo("5");
        assertThat(entity.getVersion()).isEqualTo(3);
        assertThat(entity.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void balanceReadFromFourDecimalPlacesGetsTheCurrencyScale() {
        // Postgres returns NUMERIC(24,4), so 5 yen comes back as 5.0000
        AccountEntity row = new AccountEntity(new UUID(1, 1), OWNER, "JPY", AccountType.CUSTOMER, AccountStatus.ACTIVE,
                new BigDecimal("5.0000"), 0, NOW);

        assertThat(AccountMapper.toDomain(row).balance()).isEqualTo(new Money(new BigDecimal("5"), JPY));
    }

    @Test
    void journalEntryBecomesAnEntityWithItsPostings() {
        Posting debit = new Posting(new UUID(3, 1), new UUID(1, 1), new Money(new BigDecimal("-40"), GBP));
        Posting credit = new Posting(new UUID(3, 2), new UUID(1, 2), new Money(new BigDecimal("40"), GBP));
        JournalEntry entry = new JournalEntry(new UUID(2, 1), EntryType.TRANSFER, "T-1", NOW, List.of(debit, credit));

        JournalEntryEntity entity = JournalEntryMapper.toEntity(entry);

        assertThat(entity.getId()).isEqualTo(entry.id());
        assertThat(entity.getType()).isEqualTo(EntryType.TRANSFER);
        assertThat(entity.getReference()).isEqualTo("T-1");
        assertThat(entity.getCreatedAt()).isEqualTo(NOW);
        assertThat(entity.getPostings()).hasSize(2);
        assertThat(entity.getPostings().get(0).getId()).isEqualTo(debit.id());
        assertThat(entity.getPostings().get(0).getAccountId()).isEqualTo(debit.accountId());
        assertThat(entity.getPostings().get(0).getAmount()).isEqualByComparingTo("-40");
        assertThat(entity.getPostings().get(0).getCurrency()).isEqualTo("GBP");
        assertThat(entity.getPostings().get(1).getAmount()).isEqualByComparingTo("40");
    }
}
