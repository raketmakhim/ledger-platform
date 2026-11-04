package com.raketmakhim.ledger.accounting.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.math.BigDecimal;
import java.time.Instant;
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
import com.raketmakhim.ledger.accounting.domain.account.AccountStatus;
import com.raketmakhim.ledger.accounting.domain.account.AccountType;
import com.raketmakhim.ledger.accounting.domain.journal.EntryType;
import com.raketmakhim.ledger.accounting.domain.journal.JournalEntry;
import com.raketmakhim.ledger.accounting.domain.journal.JournalEntryNotFoundException;
import com.raketmakhim.ledger.accounting.domain.journal.Posting;
import com.raketmakhim.ledger.accounting.domain.money.Money;

/**
 * Who may read a journal entry, and what they see of it: anyone who owns one of its accounts, and every posting
 * except those on the bank's own accounts.
 */
class JournalEntryServiceTest {

    private static final Currency GBP = Currency.getInstance("GBP");
    private static final Instant NOW = Instant.parse("2026-10-15T12:00:00Z");

    private static final UUID ALICE = new UUID(1, 1);
    private static final UUID BOB = new UUID(1, 2);
    private static final UUID CLEARING = new UUID(1, 3);

    private static final UUID ALICE_OWNER = new UUID(7, 1);
    private static final UUID BOB_OWNER = new UUID(7, 2);
    private static final UUID STRANGER = new UUID(7, 9);

    private static final UUID TRANSFER_ID = new UUID(2, 1);
    private static final UUID DEPOSIT_ID = new UUID(2, 2);

    private final Map<UUID, Account> accounts = new HashMap<>();
    private final Map<UUID, JournalEntry> entries = new HashMap<>();
    // anything the service asks the repositories to write
    private final List<String> writes = new ArrayList<>();
    private JournalEntryService service;

    @BeforeEach
    void setUp() {
        AccountRepository accountRepository = new AccountRepository() {
            public Optional<Account> findById(UUID id) {
                return Optional.ofNullable(accounts.get(id));
            }

            public Optional<Account> findClearingAccount(Currency currency) {
                return Optional.empty();
            }

            public List<Account> findByOwner(UUID ownerId) {
                return List.of();
            }

            public void save(Account account) {
                writes.add("account " + account.id());
            }
        };
        JournalEntryRepository entryRepository = new JournalEntryRepository() {
            public void save(JournalEntry entry) {
                writes.add("entry " + entry.id());
            }

            public Optional<JournalEntry> findById(UUID id) {
                return Optional.ofNullable(entries.get(id));
            }
        };
        service = new JournalEntryService(entryRepository, accountRepository);

        add(ALICE, ALICE_OWNER, AccountType.CUSTOMER);
        add(BOB, BOB_OWNER, AccountType.CUSTOMER);
        add(CLEARING, null, AccountType.SYSTEM);
        // Alice sends Bob 40 (debit first), and the bank deposits 100 into Alice's account
        entries.put(TRANSFER_ID, entry(TRANSFER_ID, EntryType.TRANSFER, "T-1", posting(1, ALICE, "-40"), posting(2, BOB, "40")));
        entries.put(DEPOSIT_ID, entry(DEPOSIT_ID, EntryType.DEPOSIT, "D-1", posting(3, CLEARING, "-100"), posting(4, ALICE, "100")));
    }

    private void add(UUID id, UUID owner, AccountType type) {
        accounts.put(id, new Account(id, owner, GBP, type, AccountStatus.ACTIVE, new Money(BigDecimal.ZERO, GBP), 0, NOW));
    }

    private static Posting posting(long n, UUID account, String amount) {
        return new Posting(new UUID(3, n), account, new Money(new BigDecimal(amount), GBP));
    }

    private static JournalEntry entry(UUID id, EntryType type, String reference, Posting... postings) {
        return new JournalEntry(id, type, reference, NOW, List.of(postings));
    }

    private static List<UUID> accountsIn(EntryView view) {
        return view.postings().stream().map(Posting::accountId).toList();
    }

    // --- what the caller sees ---

    @Test
    void theSenderSeesBothPostingsOfATransfer() {
        EntryView view = service.get(ALICE_OWNER, TRANSFER_ID);

        assertThat(view.postings()).containsExactlyElementsOf(entries.get(TRANSFER_ID).postings());
    }

    @Test
    void theOwnerOfTheDestinationSeesBothPostingsIncludingTheSource() {
        EntryView view = service.get(BOB_OWNER, TRANSFER_ID);

        assertThat(accountsIn(view)).containsExactly(ALICE, BOB);
    }

    @Test
    void aDepositShowsTheCustomerOnlyTheirOwnPosting() {
        EntryView view = service.get(ALICE_OWNER, DEPOSIT_ID);

        assertThat(accountsIn(view)).containsExactly(ALICE);
        assertThat(view.postings().get(0).amount()).isEqualTo(new Money(new BigDecimal("100"), GBP));
    }

    @Test
    void theBanksAccountIsHiddenWhateverThePostingOrder() {
        entries.put(DEPOSIT_ID, entry(DEPOSIT_ID, EntryType.DEPOSIT, "D-1", posting(4, ALICE, "100"), posting(3, CLEARING, "-100")));

        assertThat(accountsIn(service.get(ALICE_OWNER, DEPOSIT_ID))).containsExactly(ALICE);
    }

    @Test
    void theViewCarriesTheEntrysIdTypeReferenceAndTime() {
        EntryView view = service.get(ALICE_OWNER, TRANSFER_ID);

        assertThat(view.id()).isEqualTo(TRANSFER_ID);
        assertThat(view.type()).isEqualTo(EntryType.TRANSFER);
        assertThat(view.reference()).isEqualTo("T-1");
        assertThat(view.createdAt()).isEqualTo(NOW);
    }

    @Test
    void postingsKeepTheOrderTheyWereMadeIn() {
        assertThat(service.get(ALICE_OWNER, TRANSFER_ID).postings().get(0).amount())
                .isEqualTo(new Money(new BigDecimal("-40"), GBP));
    }

    // --- who may read ---

    @Test
    void aCallerWhoOwnsNeitherAccountOfATransferGetsNotFound() {
        assertThatThrownBy(() -> service.get(STRANGER, TRANSFER_ID))
                .isInstanceOfSatisfying(JournalEntryNotFoundException.class,
                        e -> assertThat(e.entryId()).isEqualTo(TRANSFER_ID));
    }

    @Test
    void aCallerWhoOwnsNoAccountOfADepositGetsNotFound() {
        assertThatThrownBy(() -> service.get(BOB_OWNER, DEPOSIT_ID)).isInstanceOf(JournalEntryNotFoundException.class);
    }

    @Test
    void anUnknownEntryGetsNotFound() {
        UUID unknown = new UUID(9, 9);

        assertThatThrownBy(() -> service.get(ALICE_OWNER, unknown))
                .isInstanceOfSatisfying(JournalEntryNotFoundException.class,
                        e -> assertThat(e.entryId()).isEqualTo(unknown));
    }

    @Test
    void someoneElsesEntryLooksExactlyLikeAMissingOne() {
        UUID unknown = new UUID(9, 9);

        Throwable notOwned = catchThrowable(() -> service.get(STRANGER, TRANSFER_ID));
        Throwable doesNotExist = catchThrowable(() -> service.get(STRANGER, unknown));

        assertThat(notOwned).isInstanceOf(JournalEntryNotFoundException.class);
        assertThat(doesNotExist).isInstanceOf(JournalEntryNotFoundException.class);
        assertThat(notOwned.getMessage()).isEqualTo("Journal entry not found: " + TRANSFER_ID);
        assertThat(doesNotExist.getMessage()).isEqualTo("Journal entry not found: " + unknown);
    }

    @Test
    void rejectsAMissingCallerOrEntryId() {
        assertThatThrownBy(() -> service.get(null, TRANSFER_ID)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.get(ALICE_OWNER, null)).isInstanceOf(IllegalArgumentException.class);
    }

    // --- reading is only reading ---

    @Test
    void readingChangesNothing() {
        service.get(ALICE_OWNER, TRANSFER_ID);
        service.get(ALICE_OWNER, DEPOSIT_ID);
        assertThatThrownBy(() -> service.get(STRANGER, TRANSFER_ID)).isInstanceOf(JournalEntryNotFoundException.class);

        assertThat(writes).isEmpty();
    }
}
