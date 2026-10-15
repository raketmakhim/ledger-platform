# Code overview

A map of the code as it stands today: what each part does, how the parts fit together, and what isn't built yet. For why things are the way they are, see [decisions](decisions/README.md). For what comes next, see the [roadmap](roadmap.md).

## What exists

A ledger core: money, accounts, journal entries and the rules that keep them consistent, plus the first use case (a deposit). All of it is plain Java with unit tests, and the first database migration. There is no API and no database code yet, so nothing is saved anywhere.

## Layout

The code is hexagonal (ports and adapters), grouped by feature. Dependencies point inwards: `domain` knows nothing about the other layers, and `application` knows only `domain`.

```
com.raketmakhim.ledger
  accounting
    domain        business rules, plain Java (no Spring, no JPA)
    application   use cases, plus interfaces ("ports") for what they need from outside
  (adapters)      not built yet: database, REST API
```

An ArchUnit test fails the build if anything in `domain` imports Spring or JPA.

## Domain (`accounting/domain`)

Split into three packages by concept, plus the shared base exception:

```
domain/
  DomainException
  money/     Money, CurrencyMismatchException, UnsupportedCurrencyException
  account/   Account, AccountType, AccountStatus,
             AccountNotActiveException, AccountNotFoundException, InsufficientFundsException
  journal/   JournalEntry, Posting, EntryType, UnbalancedEntryException
```

`account` uses `journal` and `money`, `journal` uses `money`, and `money` uses nothing, so there are no cycles. Each enum and exception sits next to the class it belongs to.

| Class | What it is |
|---|---|
| `Money` | An amount plus a currency. Immutable. The scale is fixed by the currency (GBP 2, JPY 0), extra decimals are rejected rather than rounded, and amounts are capped at 20 digits before the decimal point. Compare with `compareTo`, never `equals` on the raw number. |
| `Posting` | One account's side of a transaction: an account id and a signed `Money`. Positive is a credit, negative is a debit. It can't be zero. |
| `JournalEntry` | One transaction: an id, an `EntryType`, a reference (at most 36 characters), a time and a list of postings. It refuses to build unless it has at least 2 postings and they sum to zero per currency. Its list of postings can't be changed afterwards. |
| `Account` | An id, a currency, a type, a status, a `Money` balance and a version. `applyPosting(posting)` returns a **new** account with the balance changed and leaves the original alone. |
| `AccountType` | `CUSTOMER` or `SYSTEM`. Only `SYSTEM` accounts (such as clearing) may have a negative balance. |
| `AccountStatus` | `ACTIVE`, `FROZEN` or `CLOSED`. Only `ACTIVE` accounts accept postings. |
| `EntryType` | `DEPOSIT`, `TRANSFER` or `REVERSAL`. |

Every domain object is immutable and checks its own rules in its constructor, so an invalid one can't exist.

### Errors

Rules the API must tell apart have their own exception, all under `DomainException`: `InsufficientFundsException`, `AccountNotActiveException`, `CurrencyMismatchException`, `UnbalancedEntryException`, `AccountNotFoundException` and `UnsupportedCurrencyException` (no clearing account for the currency). Each carries the details the API will need. Everything else (nulls, bad decimals and so on) throws `IllegalArgumentException`.

## Application (`accounting/application`)

`DepositService.deposit(accountId, amount, reference)` puts money into a customer account, funded through the bank's clearing account for that currency:

1. Reject an amount that isn't positive.
2. Load the customer account (or throw `AccountNotFoundException`) and reject non-customer accounts.
3. Load the clearing account for the currency (or throw `UnsupportedCurrencyException`).
4. Build two postings: clearing is negative, customer is positive.
5. Build the `JournalEntry`, which checks the postings balance.
6. Call `applyPosting` on both accounts, which catches a frozen account, a currency mismatch and so on.
7. Only now, save both accounts and the entry.

Nothing is saved unless every rule passes. Putting the saves in one database transaction will be the database adapter's job.

The service depends on interfaces, not implementations:

- `AccountRepository`: find an account, find the clearing account for a currency, save.
- `JournalEntryRepository`: save an entry.
- `IdGenerator`: make a new id (the real one will produce UUIDv7).
- `java.time.Clock`: the current time, so tests can fix it.

## Database (`db/migration/V1__init.sql`)

- `account`: id, currency, type, status, balance, version, created at. A `CHECK` stops a customer balance going negative, and a partial unique index allows one `SYSTEM` account per currency.
- `journal_entry`: id, type, reference, created at. `type` is limited to the three entry types.
- `posting`: id, entry, account, signed amount, currency. Both foreign keys are indexed.
- Amounts are `NUMERIC(24,4)` and times are `TIMESTAMPTZ`.
- The migration also seeds a clearing account for GBP, EUR, USD and JPY.

Some rules exist in both the code and the database (no negative customer balance, the reference length, the entry types). The code gives clear errors and the database is the backstop. If you change one, change the other.

## Tests

- **Unit tests** (no Docker): one test class per domain class, plus `DepositServiceTest`, which uses small in-memory fakes of the repositories instead of a database.
- **Integration test** (`@Tag("integration")`, needs Docker): starts a real Postgres with Testcontainers, runs the migration, and checks the seed accounts and the unique index.
- **Architecture test:** the `domain` package must not depend on Spring or JPA.
- **Coverage:** JaCoCo writes a report to `target/site/jacoco/index.html`. The app's `main` class is excluded with `@ExcludeFromJacocoGeneratedReport`.

## CI

GitHub Actions runs two jobs on every push: `unit-tests` (also prints the coverage summary) and `integration-tests`.

## Running it

- Tests: see the [README](../README.md).
- The app: `.\mvnw spring-boot:test-run` starts it with a throwaway Postgres. Alternatively, `docker compose up` starts a Postgres with a saved volume on port 5432, and `application.yaml` points at it. If you change `V1__init.sql` after it has run against that volume, Flyway will fail on a checksum mismatch, so remove the volume first (`docker compose down -v`).

## Not built yet

The real `IdGenerator`, the database adapter (JPA entities, mappers, repository implementations, the transaction), transfers and reversals, the REST API, idempotency, locking, security and events. See the [roadmap](roadmap.md).
