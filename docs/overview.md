# Code overview

A map of the code as it stands today: what each part does, how the parts fit together, and what isn't built yet. For why things are the way they are, see [decisions](decisions/README.md). For what comes next, see the [roadmap](roadmap.md).

## What exists

A ledger core: money, accounts, journal entries and the rules that keep them consistent, plus two use cases (deposit and transfer). All of it is plain Java with unit tests, and the first database migration. There is no API and no database code yet, so nothing is saved anywhere.

## Layout

The code is hexagonal (ports and adapters), grouped by feature. Dependencies point inwards: `domain` knows nothing about the other layers, and `application` knows only `domain`.

```
com.raketmakhim.ledger
  accounting
    domain        business rules, plain Java (no Spring, no JPA)
    application   use cases, plus interfaces ("ports") for what they need from outside
    adapter       implementations of the ports: UuidV7IdGenerator, and persistence/ (the database code). The REST API is not built yet
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

Nothing is saved unless every rule passes. The services themselves are plain Java. In the running app they are wrapped (see Concurrency below) so the whole method runs in one transaction and the saves commit together.

`TransferService.transfer(fromId, toId, amount, reference)` moves money between two customer accounts. It follows the same steps, with no clearing account: reject a bad amount, null ids or the same account on both sides, load both accounts (`AccountNotFoundException` if missing), reject non-customer accounts, build a negative posting for the source and a positive one for the destination, build a `TRANSFER` entry, call `applyPosting` on both (which catches a frozen account, a currency mismatch and insufficient funds), then save. Concurrent transfers are handled by the version check and retry, not by locking (see Concurrency below).

Two small helpers keep the services short. `Checks` (application, package-private) holds the rules both services share: the amount must be positive and the account must be a customer account. `Require.notNull` (domain) is the null check used by every domain object. It throws `IllegalArgumentException`, so the API has one exception type to map to a 400.

The services depend on interfaces, not implementations:

- `AccountRepository`: find an account, `getById` (find it or throw `AccountNotFoundException`), find the clearing account for a currency, save.
- `JournalEntryRepository`: save an entry.
- `IdGenerator`: make a new id. `adapter/UuidV7IdGenerator` is the real one (UUIDv7, from `java-uuid-generator`).
- `java.time.Clock`: the current time, so tests can fix it.

## Database (`db/migration/V1__init.sql`)

- `account`: id, owner id, currency, type, status, balance, version, created at. A customer account has an owner and the bank's own account has none (a `CHECK`), and `owner_id` is indexed. A `CHECK` stops a customer balance going negative, and a partial unique index allows one `SYSTEM` account per currency.
- `journal_entry`: id, type, reference, created at. `type` is limited to the three entry types, and `(type, reference)` is unique, so a repeated request cannot move money twice. `JpaJournalEntryRepository` flushes on save and turns a violation of that constraint (found by its name) into `DuplicateReferenceException`, a 409 in the API.
- `posting`: id, entry, account, signed amount, currency. Both foreign keys are indexed.
- Amounts are `NUMERIC(24,4)` and times are `TIMESTAMPTZ`.
- The migration also seeds a clearing account for GBP, EUR, USD and JPY.

Some rules exist in both the code and the database (no negative customer balance, the reference length, the entry types). The code gives clear errors and the database is the backstop. If you change one, change the other.

## Tests

- **Unit tests** (no Docker): one test class per domain class, plus `DepositServiceTest` and `TransferServiceTest`, which use small in-memory fakes of the repositories instead of a database.
- **Integration test** (`@Tag("integration")`, needs Docker): starts a real Postgres with Testcontainers, runs the migration, and checks the seed accounts and the unique index.
- **Architecture test:** the `domain` package must not depend on Spring or JPA.
- **Coverage:** JaCoCo writes a report to `target/site/jacoco/index.html`. The app's `main` class is excluded with `@ExcludeFromJacocoGeneratedReport`.

## CI

GitHub Actions runs two jobs on every pull request and every push to `main`: `unit-tests` (also prints the coverage summary) and `integration-tests`.

A separate workflow, `security.yml`, generates the SBOM (a CycloneDX list of the libraries the app ships) and has Trivy scan it. It fails on any HIGH or CRITICAL finding, and also runs every Monday. Findings we have accepted are in `.trivyignore.yaml`, each with a reason and an expiry date. Renovate (`renovate.json`) proposes dependency updates. See [decisions/tooling.md](decisions/tooling.md).

## Running it

- Tests: see the [README](../README.md).
- The app: `.\mvnw spring-boot:test-run` starts it with a throwaway Postgres. Alternatively, `docker compose up` starts a Postgres with a saved volume on port 5432, and `application.yaml` points at it. If you change `V1__init.sql` after it has run against that volume, Flyway will fail on a checksum mismatch, so remove the volume first (`docker compose down -v`).

## Database adapter (`accounting/adapter/persistence`)

The JPA code that implements the repository ports. Nothing outside this package sees a JPA class.

- `AccountEntity`, `JournalEntryEntity`, `PostingEntity`: one per table, package-private. `AccountEntity.version` is a JPA `@Version`.
- `AccountMapper`, `JournalEntryMapper`: hand-written conversion between entities and domain objects. Entries are only written so far, so `JournalEntryMapper` has no way back.
- `JpaAccountRepository`: find by id, find the clearing account for a currency, save. Saving an existing account checks its version, so a stale copy fails with an optimistic-locking exception and never overwrites a newer balance.
- `JpaJournalEntryRepository`: saves an entry and its postings.

Each `save` has its own `@Transactional`, so it joins the service's transaction (and works on its own in tests).

The mappers are unit-tested. The repositories need a real database, so `JpaRepositoriesTest` is an integration test, and the two `Jpa*` classes are excluded from the unit coverage rule in `pom.xml`.

## Concurrency

- **Transaction and retry:** `TransactionalTransferService` and `TransactionalDepositService` (adapter) extend the plain services and add `@RetriedTransaction` to the one public method. That annotation (`RetriedTransaction`) is `@Transactional` plus `@Retryable`, and `LedgerConfig` creates the wrappers as the beans. The core stays free of Spring.
- **Optimistic locking:** accounts are read without locks. `AccountEntity.version` makes a stale save fail, and the whole method runs again in a new transaction (8 attempts, random growing pause, both set by `ledger.retry.*` in `application.yaml`). When the attempts run out the last `ConcurrencyFailureException` is thrown, and the API turns it into a 409.
- **Order matters:** the retry must be the outer layer, so each attempt gets a fresh transaction. Spring Retry's advice is ordered before the transaction's by default, and `TransactionBoundaryTest` checks it. The wrappers only work when called from another bean: a call from inside the same class skips the proxy.
- **Deadlocks:** `hibernate.order_updates` (in `application.yaml`) sends updates in primary-key order, and a deadlock loser is retried like any other clash.
- **Tests:** `TransactionBoundaryTest` (integration) covers the wrappers: a deposit that fails halfway changes nothing, a clash is retried in a fresh transaction (one rollback, then one commit), and the retries stop at the configured number. `ConcurrencyTest` (integration) fires many requests at once against Postgres and checks the books: many deposits into one account, overspending and opposite-direction transfers. Removing `@Version` makes it fail.
- **Limit:** one clearing account per currency is a hot row, so heavy deposit load can run out of retries. See [todo](todo.md).

## API (`accounting/adapter/web`)

- `POST /accounts` (open an empty account in GBP, EUR, USD or JPY), `GET /accounts/{id}`, `GET /accounts` (the caller's own, oldest first), and `POST /transfers`. Health is at `/actuator/health`, with `/liveness` and `/readiness` (readiness includes the database).
- **Who is calling:** the `X-Customer-Id` header, read in one place (`CallerArgumentResolver`) into a `Caller`. Controllers pass only its id to the services, so real login can replace the header without touching them. Missing or invalid header is a 401. Anyone can fake the header, so this is not security yet.
- **Ownership:** `AccountService` shows an account only to its owner, and `TransferService` takes money only from the caller's accounts. Anything else is "not found", the same as a missing account, so ids cannot be probed. A transfer's destination can be anyone's.
- **Errors:** every failure is a `ProblemDetail` with a fixed `urn:ledger:problem:<slug>` type (see `ApiExceptionHandler`).
- The Postman collection in `docs/postman` covers all of it.

## Not built yet

Row locking (only if retries become common), reversals, withdrawals, the REST API, idempotency, locking, security and events. See the [roadmap](roadmap.md).
