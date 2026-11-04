# Ledger Platform

A payments and ledger platform for recording and tracking financial transactions.

## Tech Stack

- Java 21
- Spring Boot 3.5.16 (Web, Data JPA, Validation, Actuator, AOP) with Spring Retry
- PostgreSQL with Flyway migrations
- Testcontainers for integration tests, ArchUnit for the layer rules, JaCoCo for coverage
- CycloneDX SBOM scanned by Trivy in CI, and Renovate for dependency updates
- Maven

## Prerequisites

- JDK 21
- Python 3 (for the `task` commands)
- Docker (required for integration tests and for running locally via Testcontainers)

## Getting Started

Run the unit tests (no Docker needed). This also checks the coverage rule, lists any uncovered lines, and writes a report to `target/site/jacoco/index.html`:

```bash
./task test
```

Run the integration tests (needs Docker):

```bash
./task integration
```

Run the app locally with a throwaway PostgreSQL container (started automatically by Testcontainers):

```bash
./mvnw spring-boot:test-run
```

On Windows PowerShell, use `.\task` and `.\mvnw.cmd` instead of `./task` and `./mvnw`. The health endpoint is at http://localhost:8080/actuator/health.

To try the API, start Postgres and the app with `./task up`, load the demo accounts, and run the Postman collection in [docs/postman](docs/postman). The steps are in "Trying the API" in [docs/commands.md](docs/commands.md), which also has every other command (single tests, coverage, Docker, build).

## Project Structure

```
src/main/java/com/raketmakhim/ledger/accounting/domain       Plain-Java domain: Money, Posting, JournalEntry, Account
src/main/java/com/raketmakhim/ledger/accounting/application  Use cases (transfer, deposit, accounts, reading entries) and the ports they need
src/main/java/com/raketmakhim/ledger/accounting/adapter      Implementations of the ports: web (REST API), persistence (JPA), transactions and retry
src/main/resources                                           Configuration (application.yaml) and Flyway migrations (db/migration)
src/test/java/com/raketmakhim/ledger                         Tests, ArchUnit rules and Testcontainers setup
docs/                                                        overview.md, commands.md, decisions/, roadmap.md, standards.md, todo.md, postman/
```

The code follows a hexagonal layout, grouped by feature. The `domain` package must not depend on Spring or JPA, and a test enforces that. The `application` package is kept free of them by convention only.

## Status

Early development. The ledger core, the Postgres persistence and a REST API are in place and tested: open and read accounts, transfer money (with idempotency keys, optimistic locking and retry), and read back a journal entry. Callers are identified by an `X-Customer-Id` header that anyone can fake, so the API must not be exposed publicly until real login (roadmap item 6) replaces it. See [docs/overview.md](docs/overview.md) for a tour of the code, [docs/roadmap.md](docs/roadmap.md) for what's next and [docs/decisions/README.md](docs/decisions/README.md) for why things are the way they are.

## License

TBD
