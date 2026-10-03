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

Run the tests:

```bash
./mvnw test
```

Run the app locally with a throwaway PostgreSQL container (started automatically by Testcontainers):

```bash
./mvnw spring-boot:test-run
```

On Windows PowerShell, use `.\mvnw` instead of `./mvnw`.

The health endpoint is available at http://localhost:8080/actuator/health.

## Project Structure

```
src/main/java/com/raketmakhim/ledger   Application code
src/main/resources                     Configuration (application.yaml) and Flyway migrations (db/migration)
src/test/java/com/raketmakhim/ledger   Tests and Testcontainers setup
```

## Status

Early development. The project skeleton is in place; no domain code or database migrations yet.

## License

TBD
