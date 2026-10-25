package com.raketmakhim.ledger.accounting.application;

import java.util.function.Supplier;

/**
 * Outbound port: runs a piece of work as one database transaction, so everything it saves commits together or not at all.
 * <p>
 * If another request changes an account the work has read, the work is run again from the start, up to a limit,
 * so it must be safe to repeat: do all reading inside it. When the limit is reached, ConcurrentUpdateException is thrown.
 */
public interface TransactionRunner {

    <T> T inTransaction(Supplier<T> work);
}
