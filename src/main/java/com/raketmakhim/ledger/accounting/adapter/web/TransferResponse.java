package com.raketmakhim.ledger.accounting.adapter.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.raketmakhim.ledger.accounting.application.EntryView;
import com.raketmakhim.ledger.accounting.domain.journal.EntryType;
import com.raketmakhim.ledger.accounting.domain.journal.JournalEntry;
import com.raketmakhim.ledger.accounting.domain.journal.Posting;

/** The JSON body returned for a journal entry, when it is created and when it is read. Amounts are strings, so a client never reads money as a float. */
record TransferResponse(UUID id, EntryType type, String reference, Instant createdAt, List<PostingResponse> postings) {

    record PostingResponse(UUID id, UUID accountId, String amount, String currency) {
    }

    static TransferResponse from(JournalEntry entry) {
        return of(entry.id(), entry.type(), entry.reference(), entry.createdAt(), entry.postings());
    }

    static TransferResponse from(EntryView view) {
        return of(view.id(), view.type(), view.reference(), view.createdAt(), view.postings());
    }

    private static TransferResponse of(UUID id, EntryType type, String reference, Instant createdAt,
                                       List<Posting> postings) {
        List<PostingResponse> responses = postings.stream()
                .map(p -> new PostingResponse(p.id(), p.accountId(), p.amount().amount().toPlainString(),
                        p.amount().currency().getCurrencyCode()))
                .toList();
        return new TransferResponse(id, type, reference, createdAt, responses);
    }
}
