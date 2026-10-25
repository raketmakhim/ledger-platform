package com.raketmakhim.ledger.accounting.application;

import java.util.function.Supplier;

/** A TransactionRunner for unit tests: just runs the work, and counts how many times it was asked to. */
class DirectTransactionRunner implements TransactionRunner {

    private int runs;

    @Override
    public <T> T inTransaction(Supplier<T> work) {
        runs++;
        return work.get();
    }

    int runs() {
        return runs;
    }
}
