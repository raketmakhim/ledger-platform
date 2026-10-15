package com.raketmakhim.ledger.accounting.domain;

/** A business rule was broken. The API turns each subclass into its own problem type. */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message){
        super(message);
    }
}
