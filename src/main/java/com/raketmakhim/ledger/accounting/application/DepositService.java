package com.raketmakhim.ledger.accounting.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import com.raketmakhim.ledger.accounting.domain.Account;
import com.raketmakhim.ledger.accounting.domain.AccountNotFoundException;
import com.raketmakhim.ledger.accounting.domain.AccountType;
import com.raketmakhim.ledger.accounting.domain.EntryType;
import com.raketmakhim.ledger.accounting.domain.JournalEntry;
import com.raketmakhim.ledger.accounting.domain.Money;
import com.raketmakhim.ledger.accounting.domain.Posting;
import com.raketmakhim.ledger.accounting.domain.UnsupportedCurrencyException;

/**
 * Puts money into a customer account, funded through the bank's clearing account for that currency.
 * Both accounts are updated and everything is saved only after every rule has passed.
 * Putting the saves in one database transaction is the database adapter's job.
 */
public class DepositService {

    private final AccountRepository accounts;
    private final JournalEntryRepository entries;
    private final IdGenerator ids;
    private final Clock clock;

    public DepositService(AccountRepository accounts, JournalEntryRepository entries, IdGenerator ids, Clock clock){
        this.accounts = accounts;
        this.entries = entries;
        this.ids = ids;
        this.clock = clock;
    }

    public JournalEntry deposit(UUID accountId, Money amount, String reference){
        positiveAmountCheck(amount);
        Account customer = accounts.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
        customerAccountCheck(customer);
        Account clearing = accounts.findClearingAccount(amount.currency()).orElseThrow(
                () -> new UnsupportedCurrencyException(amount.currency()));

        Posting clearingPosting = new Posting(ids.newId(), clearing.id(), amount.negate());
        Posting customerPosting = new Posting(ids.newId(), customer.id(), amount);
        JournalEntry entry = new JournalEntry(ids.newId(), EntryType.DEPOSIT, reference, clock.instant(),
                List.of(clearingPosting, customerPosting));

        Account updatedClearing = clearing.applyPosting(clearingPosting);
        Account updatedCustomer = customer.applyPosting(customerPosting);

        accounts.save(updatedClearing);
        accounts.save(updatedCustomer);
        entries.save(entry);
        return entry;
    }

    private static void positiveAmountCheck(Money amount){
        if (amount == null){
            throw new IllegalArgumentException("Amount cannot be null.");
        }
        if (!amount.isPositive()){
            throw new IllegalArgumentException("Deposit amount must be positive: " + amount.amount());
        }
    }

    private static void customerAccountCheck(Account account){
        if (account.type() != AccountType.CUSTOMER){
            throw new IllegalArgumentException("Deposits go to customer accounts, not " + account.type() + ".");
        }
    }
}
