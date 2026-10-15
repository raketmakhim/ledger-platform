# Ledger Platform

A payments and ledger platform for recording and tracking financial transactions.

## Tech Stack

- Java 21
- Spring Boot 3.5.16 (Web, Data JPA, Validation, Actuator)
- PostgreSQL with Flyway migrations
- Testcontainers for integration tests
- Maven

## Prerequisites

- JDK 21
- Docker (required for tests and for running locally via Testcontainers)

## Getting Started

Run the unit tests (no Docker needed). This also writes a coverage report to `target/site/jacoco/index.html`:

```bash
./mvnw test -DexcludedGroups=integration
```

Run the integration tests (needs Docker):

```bash
./mvnw test -Dgroups=integration
```

Run everything:

```bash
./mvnw test
```

Tests that need Spring or a database must be tagged `@Tag("integration")`, or they run in the unit job.

Run the app locally with a throwaway PostgreSQL container (started automatically by Testcontainers):

```bash
./mvnw spring-boot:test-run
```

On Windows PowerShell, use `.\mvnw` instead of `./mvnw`.

The health endpoint is available at http://localhost:8080/actuator/health.

## Project Structure

```
src/main/java/com/raketmakhim/ledger/accounting/domain   Plain-Java domain: Money, Posting, JournalEntry, Account
src/main/resources                                       Configuration (application.yaml) and Flyway migrations (db/migration)
src/test/java/com/raketmakhim/ledger                     Tests, ArchUnit rules and Testcontainers setup
docs/                                                    decisions/, roadmap.md, todo.md
```

The code follows a hexagonal layout, grouped by feature. The `domain` package must not depend on Spring or JPA, and a test enforces that.

## Status

Early development. The domain model and the first database migration are in place and tested. There is no API or persistence layer yet. See [docs/roadmap.md](docs/roadmap.md) for what's next and [docs/decisions/README.md](docs/decisions/README.md) for why things are the way they are.

## License

TBD
