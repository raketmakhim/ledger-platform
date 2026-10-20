package com.raketmakhim.ledger.accounting.domain;

/** Null check shared by the domain objects. Throws IllegalArgumentException, not NullPointerException, so callers handle one exception type. */
public final class Require {

    private Require(){
    }

    public static void notNull(Object value, String name){
        if (value == null){
            throw new IllegalArgumentException(name + " cannot be null.");
        }
    }
}
