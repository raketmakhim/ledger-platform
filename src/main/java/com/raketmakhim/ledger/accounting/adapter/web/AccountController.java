package com.raketmakhim.ledger.accounting.adapter.web;

import java.net.URI;
import java.util.Currency;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.raketmakhim.ledger.accounting.application.AccountService;
import com.raketmakhim.ledger.accounting.domain.account.Account;

import jakarta.validation.Valid;

// The Caller comes first in every method, so a request that does not say who is calling is refused before its body is read.
@RestController
@RequestMapping("/accounts")
class AccountController {

    private final AccountService accounts;

    AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @PostMapping
    ResponseEntity<AccountResponse> open(Caller caller, @Valid @RequestBody OpenAccountRequest request) {
        Account account = accounts.open(caller.id(), Currency.getInstance(request.currency()));
        return ResponseEntity.created(URI.create("/accounts/" + account.id())).body(AccountResponse.from(account));
    }

    @GetMapping("/{id}")
    AccountResponse get(Caller caller, @PathVariable UUID id) {
        return AccountResponse.from(accounts.get(caller.id(), id));
    }

    @GetMapping
    List<AccountResponse> list(Caller caller) {
        return accounts.list(caller.id()).stream().map(AccountResponse::from).toList();
    }
}
