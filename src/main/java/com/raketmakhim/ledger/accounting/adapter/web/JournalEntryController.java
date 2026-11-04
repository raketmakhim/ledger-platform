package com.raketmakhim.ledger.accounting.adapter.web;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.raketmakhim.ledger.accounting.application.JournalEntryService;

@RestController
class JournalEntryController {

    private final JournalEntryService entries;

    JournalEntryController(JournalEntryService entries) {
        this.entries = entries;
    }

    // the same body as POST /transfers, so a client reads an entry the way it was created
    @GetMapping("/journal-entries/{id}")
    TransferResponse get(Caller caller, @PathVariable UUID id) {
        return TransferResponse.from(entries.get(caller.id(), id));
    }
}
