package com.raketmakhim.ledger.accounting.adapter.web;

import java.net.URI;
import java.util.Currency;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.raketmakhim.ledger.accounting.application.TransferService;
import com.raketmakhim.ledger.accounting.domain.journal.JournalEntry;
import com.raketmakhim.ledger.accounting.domain.money.Money;

import jakarta.validation.Valid;

@RestController
class TransferController {

    private final TransferService transfers;

    TransferController(TransferService transfers) {
        this.transfers = transfers;
    }

    @PostMapping("/transfers")
    ResponseEntity<TransferResponse> transfer(@Valid @RequestBody TransferRequest request) {
        Money amount = new Money(request.amount(), Currency.getInstance(request.currency()));
        JournalEntry entry = transfers.transfer(request.from(), request.to(), amount, request.reference());
        return ResponseEntity.created(URI.create("/journal-entries/" + entry.id())).body(TransferResponse.from(entry));
    }
}
