package com.raketmakhim.ledger.accounting.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import com.raketmakhim.ledger.accounting.domain.Require;
import com.raketmakhim.ledger.accounting.domain.account.Account;
import com.raketmakhim.ledger.accounting.domain.account.AccountNotFoundException;
import com.raketmakhim.ledger.accounting.domain.journal.EntryType;
import com.raketmakhim.ledger.accounting.domain.journal.JournalEntry;
import com.raketmakhim.ledger.accounting.domain.journal.Posting;
import com.raketmakhim.ledger.accounting.domain.money.Money;

/**
 * Moves money from one of the caller's customer accounts to any other customer account.
 * Both accounts are updated and everything is saved only after every rule has passed.
 * It must run in one transaction, which is run again if another request changes the same accounts:
 * the adapter package wraps it for that (TransactionalTransferService). It reads the accounts itself,
 * so the whole method is safe to repeat.
 */
public class TransferService {

    private final AccountRepository accounts;
    private final JournalEntryRepository entries;
    private final IdGenerator ids;
    private final Clock clock;

    public TransferService(AccountRepository accounts, JournalEntryRepository entries, IdGenerator ids, Clock clock){
        this.accounts = accounts;
        this.entries = entries;
        this.ids = ids;
        this.clock = clock;
    }

    /**
     * The source account must belong to the caller. If it does not (or does not exist, or is the bank's own),
     * it is reported as not found, so a caller cannot tell which ids exist.
     */
    public JournalEntry transfer(UUID callerId, UUID fromId, UUID toId, Money amount, String reference){
        Checks.positiveAmount(amount, "Transfer");
        Require.notNull(callerId, "CallerId");
        Require.notNull(fromId, "FromId");
        Require.notNull(toId, "ToId");
        if (fromId.equals(toId)){
            throw new IllegalArgumentException("Cannot transfer to the same account.");
        }
        Account from = accounts.getById(fromId);
        // only customer accounts have an owner, so owning the source also makes it a customer account
        if (!callerId.equals(from.ownerId())){
            throw new AccountNotFoundException(fromId);
        }
        Account to = accounts.getById(toId);
        Checks.customerAccount(to, "Transfer");

        Posting fromPosting = new Posting(ids.newId(), from.id(), amount.negate());
        Posting toPosting = new Posting(ids.newId(), to.id(), amount);
        JournalEntry entry = new JournalEntry(ids.newId(), EntryType.TRANSFER, reference, clock.instant(),
                List.of(fromPosting, toPosting));

        Account updatedFrom = from.applyPosting(fromPosting);
        Account updatedTo = to.applyPosting(toPosting);

        accounts.save(updatedFrom);
        accounts.save(updatedTo);
        entries.save(entry);
        return entry;
    }
}
