package com.raketmakhim.ledger.accounting.adapter.persistence;

import java.util.Currency;
import java.util.List;

import com.raketmakhim.ledger.accounting.domain.journal.JournalEntry;
import com.raketmakhim.ledger.accounting.domain.journal.Posting;
import com.raketmakhim.ledger.accounting.domain.money.Money;

/** Converts between a domain JournalEntry and its database rows. */
final class JournalEntryMapper {

    private JournalEntryMapper() {
    }

    static JournalEntryEntity toEntity(JournalEntry entry) {
        List<PostingEntity> postings = entry.postings().stream().map(JournalEntryMapper::toEntity).toList();
        return new JournalEntryEntity(entry.id(), entry.type(), entry.reference(), entry.createdAt(), postings);
    }

    static JournalEntry toDomain(JournalEntryEntity entity) {
        List<Posting> postings = entity.getPostings().stream().map(JournalEntryMapper::toDomain).toList();
        return new JournalEntry(entity.getId(), entity.getType(), entity.getReference(), entity.getCreatedAt(), postings);
    }

    private static Posting toDomain(PostingEntity posting) {
        Currency currency = Currency.getInstance(posting.getCurrency());
        return new Posting(posting.getId(), posting.getAccountId(), new Money(posting.getAmount(), currency));
    }

    private static PostingEntity toEntity(Posting posting) {
        return new PostingEntity(posting.id(), posting.accountId(), posting.amount().amount(),
                posting.amount().currency().getCurrencyCode());
    }
}
