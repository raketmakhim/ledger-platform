package com.raketmakhim.ledger.accounting.application;

import java.util.UUID;

/** Outbound port: where new ids come from, so tests can use predictable ones. */
public interface IdGenerator {

    UUID newId();
}
