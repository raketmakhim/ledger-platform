package com.raketmakhim.ledger.accounting.application;

import java.util.Optional;
import java.util.UUID;

/** Outbound port: how the application remembers which keys it has already seen. The database adapter implements it. */
public interface IdempotencyRepository {

    Optional<IdempotencyRecord> find(UUID callerId, String key);

    /**
     * Claims the key. If another request claimed it a moment ago, the adapter throws a concurrency failure,
     * so the whole transfer runs again and finds the record.
     */
    void save(IdempotencyRecord record);
}
