package com.raketmakhim.ledger.accounting.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class UuidV7IdGeneratorTest {

    private final UuidV7IdGenerator generator = new UuidV7IdGenerator();

    @Test
    void makesVersion7Ids() {
        UUID id = generator.newId();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void makesUniqueIds() {
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            ids.add(generator.newId());
        }

        assertThat(ids).hasSize(1000);
    }

    @Test
    void idsSortInTheOrderTheyWereMade() {
        UUID first = generator.newId();
        UUID second = generator.newId();

        // the leading 48 bits are a millisecond timestamp, so a later id never has a smaller timestamp
        assertThat(second.getMostSignificantBits() >>> 16).isGreaterThanOrEqualTo(first.getMostSignificantBits() >>> 16);
    }
}
