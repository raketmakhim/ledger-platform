package com.raketmakhim.ledger.accounting.application;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.raketmakhim.ledger.accounting.domain.Require;
import com.raketmakhim.ledger.accounting.domain.account.Account;
import com.raketmakhim.ledger.accounting.domain.account.AccountType;
import com.raketmakhim.ledger.accounting.domain.journal.JournalEntry;
import com.raketmakhim.ledger.accounting.domain.journal.JournalEntryNotFoundException;
import com.raketmakhim.ledger.accounting.domain.journal.Posting;

/**
 * Lets a customer read a journal entry. Only someone who owns one of its accounts may, and they see every posting
 * except those on the bank's own accounts. Anyone else is told the entry does not exist, as if the id were unknown.
 */
public class JournalEntryService {

    private final JournalEntryRepository entries;
    private final AccountRepository accounts;

    public JournalEntryService(JournalEntryRepository entries, AccountRepository accounts){
        this.entries = entries;
        this.accounts = accounts;
    }

    public EntryView get(UUID callerId, UUID entryId){
        Require.notNull(callerId, "CallerId");
        Require.notNull(entryId, "EntryId");
        JournalEntry entry = entries.findById(entryId).orElseThrow(() -> new JournalEntryNotFoundException(entryId));

        boolean owns = false;
        List<Posting> visible = new ArrayList<>();
        for (Posting posting : entry.postings()){
            Account account = accounts.getById(posting.accountId());
            if (callerId.equals(account.ownerId())){
                owns = true;
            }
            if (account.type() != AccountType.SYSTEM){
                visible.add(posting);
            }
        }
        if (!owns){
            throw new JournalEntryNotFoundException(entryId);
        }
        return new EntryView(entry.id(), entry.type(), entry.reference(), entry.createdAt(), visible);
    }
}
