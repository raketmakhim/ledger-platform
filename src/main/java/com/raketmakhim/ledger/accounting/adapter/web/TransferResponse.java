package com.raketmakhim.ledger.accounting.adapter.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.raketmakhim.ledger.accounting.domain.journal.EntryType;
import com.raketmakhim.ledger.accounting.domain.journal.JournalEntry;

/** The JSON body returned for a transfer. Amounts are strings, so a client never reads money as a float. */
record TransferResponse(UUID id, EntryType type, String reference, Instant createdAt, List<PostingResponse> postings) {

    record PostingResponse(UUID id, UUID accountId, String amount, String currency) {
    }

    static TransferResponse from(JournalEntry entry) {
        List<PostingResponse> postings = entry.postings().stream()
                .map(p -> new PostingResponse(p.id(), p.accountId(), p.amount().amount().toPlainString(),
                        p.amount().currency().getCurrencyCode()))
                .toList();
        return new TransferResponse(entry.id(), entry.type(), entry.reference(), entry.createdAt(), postings);
    }
}
