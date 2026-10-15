# Decisions

## Maven over Gradle

Chose Maven: declarative, predictable, reproducible builds, and widely accepted in regulated (banking) settings. Gave up Gradle's build flexibility and incremental build speed.

## Postgres over Oracle

Chose Postgres. Both are fully ACID, so that wasn't the deciding factor. Reasons:

- `NUMERIC` stores money exactly.
- `SELECT ... FOR UPDATE` and `SKIP LOCKED` cover account locking and the outbox relay.
- Deferred constraint triggers can enforce balanced entries at commit.
- Transactional DDL: a failed Flyway migration rolls back cleanly. Oracle and MySQL commit DDL implicitly, so a half-applied migration can leave a mess.
- Debezium support: logical decoding makes change-data-capture outbox relaying straightforward.
- No licence fees, and a light Testcontainers image for tests.
- Common in fintechs, so a credible choice for that market.

Gave up: Oracle's RAC clustering, vendor support contracts, and fit with existing bank estates.

## Java 21

Chose Java 21: a long-term-support release, so it keeps getting security patches, which regulated environments want. Mature, with strong Spring Boot support. Banks are moving to 17+, so it is a realistic target. Virtual threads, records and pattern matching are useful extras.

Gave up: Java 25, the newer LTS (September 2025). Revisit once Spring Boot support is confirmed and the Boot 4 migration happens.

## Flyway over Liquibase or Hibernate auto-DDL

Chose Flyway: versioned plain-SQL migrations that are reviewed in PRs and applied in order. The schema is explicit and repeatable, which matters for a ledger. Hibernate auto-DDL can silently change tables and cannot express things like constraint triggers. Flyway also benefits from Postgres's transactional DDL, so a failed migration rolls back cleanly.

Gave up: Liquibase's database-agnostic changelogs and built-in rollbacks.

## Testcontainers over H2 or a shared dev DB

Chose Testcontainers: tests run against real Postgres, so locking, `NUMERIC` and constraint triggers behave as in production. H2 would hide Postgres-specific behaviour. A shared dev DB leaks state between runs and developers.

Gave up: some test speed, and Docker is required locally and in CI.

## Postgres 17 pinned, not latest

Chose to pin `postgres:17` in tests: a new major version cannot break CI unexpectedly. Upgrade deliberately.

Gave up: automatic upgrades. Revisit when 17 nears end of life.

## Spring Boot 3.5.x now, Boot 4 later

Chose Boot 3.5.16: the mature, widely deployed line, and the one most banks and tutorials use. Boot 4 (Spring Framework 7, Jakarta EE 11, Hibernate 7, Jackson 3, modularised starters) would add migration risk before any domain code exists.

Gave up: newest features. The Boot 4 migration is planned last (roadmap item 10), using OpenRewrite, with the breakages written up in the PR.

## Hexagonal architecture, grouped by feature

Chose hexagonal (ports and adapters), with packages grouped by feature (`ledger`, `transfer`), each split into `domain`, `application` and `adapter`. The `domain` package is plain Java with no Spring or JPA imports, so rules like "a journal entry must balance" are unit-testable without a database. REST, JPA and the Kafka outbox are adapters behind interfaces.

Gave up: simplicity. More files and interfaces than the default layered Spring layout (`controller`, `service`, `repository`). Considered and not chosen: layered, vertical slices, microservices. Revisit adding Spring Modulith if module boundaries need enforcing.

## UUIDv7 IDs, generated in the app

Chose UUIDv7 (RFC 9562) for entity IDs, generated in Java rather than by the database. The leading timestamp keeps B-tree inserts sequential, which avoids the index fragmentation of random UUIDv4. UUIDs are also safe to expose in URLs (no guessable `/accounts/1042`) and can be created by the domain before saving, which suits idempotency and outbox events. Postgres 17 has no built-in `uuidv7()`, so a small library generates them.

Gave up: `bigint` is smaller (8 bytes vs 16) and slightly faster. Revisit by moving to Postgres 18, which has a built-in `uuidv7()`.

## Sign convention

Postings are signed: positive = credit (money in), negative = debit (money out). An account's balance is the sum of its postings. Customer accounts are liabilities from the bank's perspective; system accounts such as clearing will usually be negative.

## Stored balance column

`account` keeps a `balance` and a `version`, updated in the same transaction as the postings. Postings stay the source of truth; a reconciliation job checks the column against them. The row can be locked for the funds check. `CHECK (balance >= 0)` applies to customer accounts only, since system accounts may go negative.

Gave up: the risk of the column drifting from the postings.

## VARCHAR + CHECK over Postgres ENUM

`type` and `status` are `VARCHAR` with `CHECK` constraints. Postgres `ENUM` types are painful to change later.

## TIMESTAMPTZ, always UTC

All times are `TIMESTAMPTZ`, so each is an exact moment with no timezone ambiguity.

## Account holds its balance and applies postings

`Account` holds a `Money` balance, and `applyPosting(posting)` returns a new `Account` with the balance changed, leaving the original untouched. It rejects postings for another account or currency, on a non-ACTIVE account, and ones that would overdraw a customer account ("Insufficient funds"). The constructor also rejects a customer account created with a negative balance, as a safety net. Keeping the balance in the domain means these rules are unit-testable without a database, and the database `balance` column is the stored copy of it.

Gave up: the account doing its own bookkeeping means the transfer service has to coordinate saving all the changed accounts together in one database transaction. `version` is not changed by `applyPosting`: the persistence layer increments it on save.

## Clearing accounts seeded for GBP, EUR, USD and JPY

There is one `SYSTEM` clearing account per currency, created by `V1__init.sql` with fixed ids, and a partial unique index (`account_system_currency_idx`) stops a second one for the same currency. Deposits are funded through them, so they go negative: each one's balance mirrors the customer money held in that currency. Banks and ERP systems (Oracle GL, Sage) generally set these accounts up deliberately in advance, and seeding avoids a create-on-first-use race and a typo quietly creating a new system account. JPY is included to exercise the zero-decimal currency path in the real database.

Gave up: supporting a new currency needs a new migration. The unique index also means one system account per currency, so a fee or suspense account later needs a `purpose` column and a changed index.

## java-uuid-generator for UUIDv7

Use a library to generate UUIDv7 ids, not our own code: ids are not where the learning is, and a time-ordered UUID done wrong (clock ordering, randomness) is an easy bug. Chose `com.fasterxml.uuid:java-uuid-generator` (5.2.0, last released December 2025, from the FasterXML/Jackson maintainers). The other candidate, `uuid-creator` (6.1.1), was last released April 2025. Both support UUIDv7. The dependency is added when the first code that creates ids is written, and ids are generated outside `domain` objects' own checks, so `domain` stays plain Java.

Gave up: nothing much. A small extra dependency. Revisit if Postgres 18's built-in `uuidv7()` is adopted.

## Open decisions

Not decided yet. Each has a leaning, so the next step is to confirm or change it.

1. **Persistence mapping:** ArchUnit keeps JPA out of `domain`, so separate JPA entities in the adapter need mapping to the domain objects. Hand-written mapper or MapStruct? Leaning: hand-written, since there are only a few classes.
2. **Reversals:** how a reversal entry links to its original. Leaning: a nullable `reverses_entry_id` column on `journal_entry`.
3. **Outbound payment limit:** ISO 20022 messages allow 18 total digits, narrower than our 20 integer digits. Decide the per-payment limit when building the Transfers API.

Later, tracked in the roadmap: locking strategy, idempotency key expiry, authentication.
