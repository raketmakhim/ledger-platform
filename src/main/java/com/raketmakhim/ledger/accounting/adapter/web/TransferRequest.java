package com.raketmakhim.ledger.accounting.adapter.web;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** The JSON body of POST /transfers. Only the shape is checked here: the business rules are in TransferService. */
record TransferRequest(
        @NotNull UUID from,
        @NotNull UUID to,
        @NotNull BigDecimal amount,
        @NotBlank String currency,
        @NotBlank String reference) {
}
