package com.raketmakhim.ledger.accounting.adapter.web;

import jakarta.validation.constraints.NotBlank;

/** The JSON body of POST /accounts. The owner is the caller, so it is not part of the body. */
record OpenAccountRequest(@NotBlank String currency) {
}
