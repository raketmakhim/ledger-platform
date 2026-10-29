package com.raketmakhim.ledger.accounting.adapter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.raketmakhim.ledger.TestcontainersConfiguration;
import com.raketmakhim.ledger.accounting.application.IdGenerator;
import com.raketmakhim.ledger.accounting.domain.account.Account;
import com.raketmakhim.ledger.accounting.domain.account.AccountStatus;
import com.raketmakhim.ledger.accounting.domain.account.AccountType;
import com.raketmakhim.ledger.accounting.domain.journal.DuplicateReferenceException;
import com.raketmakhim.ledger.accounting.domain.journal.EntryType;
import com.raketmakhim.ledger.accounting.domain.journal.JournalEntry;
import com.raketmakhim.ledger.accounting.domain.journal.Posting;
import com.raketmakhim.ledger.accounting.domain.money.Money;

@Tag("integration")
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class JpaRepositoriesTest {

    private static final Currency GBP = Currency.getInstance("GBP");
    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");
    private static final UUID OWNER = new UUID(7, 7);

    @Autowired
    JpaAccountRepository accounts;

    @Autowired
    JpaJournalEntryRepository entries;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    IdGenerator ids;

    private Account newCustomer(String balance) {
        return newCustomer(OWNER, balance);
    }

    private Account newCustomer(UUID owner, String balance) {
        return new Account(ids.newId(), owner, GBP, AccountType.CUSTOMER, AccountStatus.ACTIVE,
                new Money(new BigDecimal(balance), GBP), 0, NOW);
    }

    private static Money gbp(String amount) {
        return new Money(new BigDecimal(amount), GBP);
    }

    @Test
    void savesAndReloadsANewAccount() {
        Account account = newCustomer("12.34");

        accounts.save(account);

        assertThat(accounts.findById(account.id())).contains(account);
    }

    @Test
    void findingAnUnknownAccountGivesNothing() {
        assertThat(accounts.findById(ids.newId())).isEmpty();
    }

    @Test
    void savingAnExistingAccountUpdatesItAndRaisesTheVersion() {
        Account account = newCustomer("100");
        accounts.save(account);

        Account loaded = accounts.findById(account.id()).orElseThrow();
        accounts.save(loaded.applyPosting(new Posting(ids.newId(), account.id(), gbp("-40"))));

        Account reloaded = accounts.findById(account.id()).orElseThrow();
        assertThat(reloaded.balance()).isEqualTo(gbp("60"));
        assertThat(reloaded.version()).isEqualTo(1);
    }

    @Test
    void savingAnOutOfDateAccountFailsInsteadOfOverwriting() {
        Account account = newCustomer("100");
        accounts.save(account);
        Account first = accounts.findById(account.id()).orElseThrow();
        Account second = accounts.findById(account.id()).orElseThrow();
        accounts.save(first.applyPosting(new Posting(ids.newId(), account.id(), gbp("-10"))));

        assertThatThrownBy(() -> accounts.save(second.applyPosting(new Posting(ids.newId(), account.id(), gbp("-20")))))
                .isInstanceOf(OptimisticLockingFailureException.class);
        assertThat(accounts.findById(account.id()).orElseThrow().balance()).isEqualTo(gbp("90"));
    }

    @Test
    void findsTheClearingAccountForACurrency() {
        Account clearing = accounts.findClearingAccount(GBP).orElseThrow();

        assertThat(clearing.id()).isEqualTo(UUID.fromString("00000000-0000-7000-8000-000000000001"));
        assertThat(clearing.type()).isEqualTo(AccountType.SYSTEM);
        assertThat(clearing.currency()).isEqualTo(GBP);
    }

    @Test
    void thereIsNoClearingAccountForACurrencyWeDoNotSupport() {
        assertThat(accounts.findClearingAccount(Currency.getInstance("CHF"))).isEmpty();
    }

    @Test
    void savesAJournalEntryWithItsPostings() {
        Account from = newCustomer("100");
        Account to = newCustomer("0");
        accounts.save(from);
        accounts.save(to);
        Posting debit = new Posting(ids.newId(), from.id(), gbp("-40"));
        Posting credit = new Posting(ids.newId(), to.id(), gbp("40"));
        JournalEntry entry = new JournalEntry(ids.newId(), EntryType.TRANSFER, "T-1", NOW, List.of(debit, credit));

        entries.save(entry);

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM journal_entry WHERE id = ?", entry.id());
        assertThat(row).containsEntry("type", "TRANSFER").containsEntry("reference", "T-1");
        List<Map<String, Object>> postings = jdbc.queryForList(
                "SELECT * FROM posting WHERE entry_id = ? ORDER BY amount", entry.id());
        assertThat(postings).hasSize(2);
        assertThat(postings.get(0)).containsEntry("account_id", from.id()).containsEntry("currency", "GBP");
        assertThat((BigDecimal) postings.get(0).get("amount")).isEqualByComparingTo("-40");
        assertThat(postings.get(1)).containsEntry("account_id", to.id());
        assertThat((BigDecimal) postings.get(1).get("amount")).isEqualByComparingTo("40");
    }

    @Test
    void findsAnOwnersAccountsOldestFirstAndNoOneElses() {
        UUID owner = ids.newId();
        Account first = newCustomer(owner, "1");
        Account second = newCustomer(owner, "2");
        Account someoneElses = newCustomer(ids.newId(), "3");
        accounts.save(first);
        accounts.save(second);
        accounts.save(someoneElses);

        assertThat(accounts.findByOwner(owner)).containsExactly(first, second);
        assertThat(accounts.findByOwner(ids.newId())).isEmpty();
    }

    @Test
    void savesTheOwnerOfAnAccount() {
        Account account = newCustomer("5");
        accounts.save(account);

        assertThat(accounts.findById(account.id()).orElseThrow().ownerId()).isEqualTo(OWNER);
    }

    @Test
    void theBanksClearingAccountsHaveNoOwner() {
        assertThat(accounts.findClearingAccount(GBP).orElseThrow().ownerId()).isNull();
    }

    @Test
    void theDatabaseRefusesACustomerAccountWithoutAnOwner() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO account (id, currency, type, status) VALUES (?, 'GBP', 'CUSTOMER', 'ACTIVE')", ids.newId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void theDatabaseRefusesABankAccountWithAnOwner() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO account (id, owner_id, currency, type, status) VALUES (?, ?, 'CHF', 'SYSTEM', 'ACTIVE')",
                ids.newId(), OWNER))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private JournalEntry entry(EntryType type, String reference, Account from, Account to) {
        return new JournalEntry(ids.newId(), type, reference, NOW, List.of(
                new Posting(ids.newId(), from.id(), gbp("-1")), new Posting(ids.newId(), to.id(), gbp("1"))));
    }

    private int count(String sql, Object arg) {
        return jdbc.queryForObject(sql, Integer.class, arg);
    }

    @Test
    void aSecondEntryWithTheSameTypeAndReferenceIsRefusedAndLeavesNothingBehind() {
        Account from = newCustomer("100");
        Account to = newCustomer("0");
        accounts.save(from);
        accounts.save(to);
        entries.save(entry(EntryType.TRANSFER, "DUP-1", from, to));
        JournalEntry repeated = entry(EntryType.TRANSFER, "DUP-1", from, to);

        assertThatThrownBy(() -> entries.save(repeated))
                .isInstanceOf(DuplicateReferenceException.class)
                .hasMessageContaining("DUP-1");

        assertThat(count("SELECT count(*) FROM journal_entry WHERE reference = ?", "DUP-1")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM posting WHERE entry_id = ?", repeated.id())).isZero();
    }

    @Test
    void theSameReferenceIsAllowedForADifferentEntryType() {
        Account from = newCustomer("100");
        Account to = newCustomer("0");
        accounts.save(from);
        accounts.save(to);

        entries.save(entry(EntryType.TRANSFER, "DUP-2", from, to));
        entries.save(entry(EntryType.DEPOSIT, "DUP-2", from, to));

        assertThat(count("SELECT count(*) FROM journal_entry WHERE reference = ?", "DUP-2")).isEqualTo(2);
    }
}
