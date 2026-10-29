package com.raketmakhim.ledger.accounting.adapter;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.transaction.annotation.Transactional;

/**
 * One transaction, run again from the start when another request changed an account it read (a stale version,
 * a deadlock, or a lock timeout). The retry must be the outer layer, so every attempt gets a fresh transaction:
 * Spring Retry's advice is ordered before the transaction's. TransactionBoundaryTest checks this.
 * It only works through the Spring proxy, so call the method from another bean, never from the same class.
 * When the attempts run out, the last ConcurrencyFailureException is thrown.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
@Retryable(
        retryFor = ConcurrencyFailureException.class,
        maxAttemptsExpression = "${ledger.retry.max-attempts:8}",
        backoff = @Backoff(delayExpression = "${ledger.retry.backoff-millis:10}", multiplier = 1.5, random = true))
@Transactional
@interface RetriedTransaction {
}
