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
