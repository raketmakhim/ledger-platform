package com.raketmakhim.ledger.accounting.adapter.persistence;

import java.util.List;

import com.raketmakhim.ledger.accounting.domain.journal.JournalEntry;
import com.raketmakhim.ledger.accounting.domain.journal.Posting;

/** Converts a domain JournalEntry to its database rows. Entries are only written so far, so there is no way back yet. */
final class JournalEntryMapper {

    private JournalEntryMapper() {
    }

    static JournalEntryEntity toEntity(JournalEntry entry) {
        List<PostingEntity> postings = entry.postings().stream().map(JournalEntryMapper::toEntity).toList();
        return new JournalEntryEntity(entry.id(), entry.type(), entry.reference(), entry.createdAt(), postings);
    }

    private static PostingEntity toEntity(Posting posting) {
        return new PostingEntity(posting.id(), posting.accountId(), posting.amount().amount(),
                posting.amount().currency().getCurrencyCode());
    }
}
