package com.raketmakhim.ledger.accounting.adapter;

import java.util.UUID;

import org.springframework.stereotype.Component;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import com.raketmakhim.ledger.accounting.application.IdGenerator;

/** Makes time-ordered UUIDv7 ids (RFC 9562), so new rows are added at the end of the primary-key index. */
@Component
public class UuidV7IdGenerator implements IdGenerator {

    private final NoArgGenerator generator = Generators.timeBasedEpochGenerator();

    @Override
    public UUID newId() {
        return generator.generate();
    }
}
