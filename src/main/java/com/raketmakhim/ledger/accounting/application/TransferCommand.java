package com.raketmakhim.ledger.accounting.application;

import java.util.UUID;

import com.raketmakhim.ledger.accounting.domain.money.Money;

/**
 * One request to move money. The key identifies the caller's attempt: sending the same key again is a retry
 * and returns the first result, a new key is a new payment.
 */
public record TransferCommand(UUID callerId, String key, UUID fromId, UUID toId, Money amount, String reference) {

    /**
     * What the request asks for, without who sent it or under which key. A retry must match the first attempt
     * on this. The amount has a fixed scale per currency, so "10" and "10.00" give the same text.
     */
    String fingerprint() {
        return fromId + "|" + toId + "|" + amount.amount().toPlainString() + "|" + amount.currency().getCurrencyCode()
                + "|" + reference;
    }
}
