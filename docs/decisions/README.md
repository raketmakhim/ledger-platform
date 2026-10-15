# Decisions

One short section per decision, with what we chose and what we gave up.

- [Tooling and build](tooling.md): Maven, Java 21, Spring Boot 3.5, Flyway, Testcontainers, Postgres 17.
- [Data model and database](data-model.md): Postgres, UUIDv7, sign convention, stored balance, clearing accounts, reversals.
- [Architecture and domain code](architecture.md): hexagonal layout, `Account`, exceptions, mappers, ports, time and ids.

## Open decisions

Not decided yet. Each has a leaning, so the next step is to confirm or change it.

1. **Outbound payment limit:** ISO 20022 messages allow 18 total digits, narrower than our 20 integer digits. Decide the per-payment limit when building the Transfers API.

Later, tracked in the roadmap: locking strategy, idempotency key expiry, authentication.
