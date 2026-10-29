package com.raketmakhim.ledger.accounting.adapter.web;

/** The request does not say who is calling. */
class UnauthenticatedException extends RuntimeException {

    UnauthenticatedException(String message) {
        super(message);
    }
}
