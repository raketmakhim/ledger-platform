package com.raketmakhim.ledger.accounting.application;

import java.time.Clock;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.raketmakhim.ledger.accounting.domain.Require;
import com.raketmakhim.ledger.accounting.domain.account.Account;
import com.raketmakhim.ledger.accounting.domain.account.AccountNotFoundException;
import com.raketmakhim.ledger.accounting.domain.account.SameAccountException;
import com.raketmakhim.ledger.accounting.domain.journal.EntryType;
import com.raketmakhim.ledger.accounting.domain.journal.IdempotencyKeyReuseException;
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
    private final IdempotencyRepository keys;
    private final IdGenerator ids;
    private final Clock clock;
    private final Duration keyWindow;

    public TransferService(AccountRepository accounts, JournalEntryRepository entries, IdempotencyRepository keys,
                           IdGenerator ids, Clock clock, Duration keyWindow){
        this.accounts = accounts;
        this.entries = entries;
        this.keys = keys;
        this.ids = ids;
        this.clock = clock;
        this.keyWindow = keyWindow;
    }

    /**
     * The source account must belong to the caller. If it does not (or does not exist, or is the bank's own),
     * it is reported as not found, so a caller cannot tell which ids exist.
     * <p>
     * A key the caller has used before returns the first entry and moves nothing, or throws
     * IdempotencyKeyReuseException if this request asks for something different. Keys belong to the caller, so
     * nobody can reach another caller's result. A request that fails stores nothing, so a retry runs again.
     */
    public JournalEntry transfer(TransferCommand command){
        Checks.positiveAmount(command.amount(), "Transfer");
        Checks.idempotencyKey(command.key());
        UUID callerId = command.callerId();
        UUID fromId = command.fromId();
        UUID toId = command.toId();
        Money amount = command.amount();
        Require.notNull(callerId, "CallerId");
        Require.notNull(fromId, "FromId");
        Require.notNull(toId, "ToId");
        if (fromId.equals(toId)){
            throw new SameAccountException();
        }
        Optional<IdempotencyRecord> earlier = keys.find(callerId, command.key());
        if (earlier.isPresent()){
            if (earlier.get().hasExpired(clock.instant(), keyWindow)){
                // an old key means nothing now: free it so this request can claim it again
                keys.release(callerId, command.key());
            } else {
                if (!earlier.get().fingerprint().equals(command.fingerprint())){
                    throw new IdempotencyKeyReuseException(command.key());
                }
                return earlier.get().entry();
            }
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
        // Postgres keeps microseconds, so cut here: a replay read back from the database must equal the first answer
        JournalEntry entry = new JournalEntry(ids.newId(), EntryType.TRANSFER, command.reference(),
                clock.instant().truncatedTo(ChronoUnit.MICROS),
                List.of(fromPosting, toPosting));

        Account updatedFrom = from.applyPosting(fromPosting);
        Account updatedTo = to.applyPosting(toPosting);

        // claim the key first: a request with the same key at the same moment waits here for this one to finish
        keys.save(new IdempotencyRecord(callerId, command.key(), command.fingerprint(), entry, entry.createdAt()));
        accounts.save(updatedFrom);
        accounts.save(updatedTo);
        entries.save(entry);
        return entry;
    }
}
