# Todo

- [x] **Per-currency balance rule:** `JournalEntry` must refuse to build unless its postings sum to zero **per currency** (at least 2 postings). Backed by Fragment and Infor docs.
- [x] **Maximum amount check in `Money`:** fail in the domain with a clear message instead of at the database (`NUMERIC(24,4)` allows 20 digits before the decimal point). Same idea as commercetools' `MoneyOverflow` error.
- [x] **Reject huge exponents in `Money`:** the size check now runs before `setScale`, so `1E+999999999` is rejected at once instead of building a billion-digit number.

## For the database adapter

Found in review. None of these is a bug yet, because nothing is saved anywhere, but each becomes one once the adapter exists.

- [x] **One transaction around a deposit or transfer:** done. `TransactionalDepositService` and `TransactionalTransferService` (adapter) add `@Transactional` and `@Retryable` around the plain services, so the saves commit together or not at all. `TransactionBoundaryTest.aDepositThatFailsHalfwayChangesNothing` proves it.
- [x] **Duplicate reference (deposits and transfers):** done. `journal_entry` has `UNIQUE (type, reference)` (`journal_entry_type_reference_uq` in `V1__init.sql`), and `JpaJournalEntryRepository.save` flushes and turns that violation into `DuplicateReferenceException`, which the API returns as a 409 `duplicate-reference`. Tested at the repository, through the endpoint, and with 10 simultaneous requests. A repeated request is refused, not replayed, and `REF-1` and `ref-1` count as different. Callers must supply a trustworthy unique id (`TxId` or UETR), because `EndToEndId` can be a placeholder. Replaying the original response belongs to idempotency (roadmap item 3).
- [x] **Lost updates on balances:** done with optimistic locking, not `FOR UPDATE`: the `@Version` check fails a stale save and `@Retryable` on the wrapper services runs the whole transaction again (8 attempts, random growing pause), then the last `ConcurrencyFailureException` is thrown and the API returns 409. Updates are sent in primary-key order (`hibernate.order_updates`) so opposite transfers cannot deadlock, and a deadlock loser is retried too. `ConcurrencyTest` proves no money is created or lost, and fails if `@Version` is removed. Pessimistic locking stays an option if retries become common.
- [ ] **Hot clearing account:** every deposit in a currency updates that currency's one clearing account, so deposits in a currency run one at a time. Accept and document this for now. The sourced pattern (Modern Treasury, TigerBeetle) is to not lock hot accounts and not make deposits wait on their balance: apply their balance asynchronously, or keep cumulative debits and credits. Splitting one clearing account into several is not confirmed by any source found.
- [ ] **Database should enforce the domain:** `posting.currency` can disagree with the account's currency. Make `(id, currency)` unique on `account` and add a composite foreign key `(account_id, currency)` from `posting`. Add the deferred zero-sum constraint trigger (roadmap item 1, optional) so an unbalanced entry can't be stored.
- [ ] **Small test gap:** the unsupported-currency test checks that no entry was saved, but not that the customer account is unchanged.

## Transfers API

- [ ] **Split `IllegalArgumentException` into specific exceptions** (same account, non-positive amount, non-customer account), so each gets its own problem type. Do it before any client depends on telling them apart. `ApiExceptionHandler` marks the shortcut.
- [ ] **`Location` header points at nothing:** `POST /transfers` returns `/journal-entries/{id}`, and no such endpoint exists. Build it, or drop the header.
- [ ] **No authentication or ownership check:** anyone who can reach `POST /transfers` can move money out of any account. Do not expose it publicly until roadmap item 6 is done. Design: scope check in the controller, ownership check in `TransferService` with a caller id passed in, and an `owner` on `Account`.
- [ ] **Accounts endpoints:** a client cannot create or list accounts yet (`POST /accounts`, `GET /accounts`).
- [ ] **Deposit endpoint:** `DepositService` is a bean now (`LedgerConfig`), but nothing calls it over HTTP yet.
- [x] **Mockito warning:** done. The surefire `argLine` in `pom.xml` now loads Mockito as a Java agent at startup (the jar's path comes from `maven-dependency-plugin`), so the "self-attaching" warning is gone and a future JDK will not block it. The empty `argLine` property keeps it working when JaCoCo is skipped.

## Supply chain

- [x] **Install the Renovate GitHub App on the repository:** done. It opened pull request #1 "Configure Renovate", which adds a basic config. Do not merge it.
- [ ] **Get our `renovate.json` onto `main`:** until then Renovate runs with its basic config, not ours (3-day wait, grouped updates, no Boot 4 or Postgres 18). Once it is on `main`, Renovate closes pull request #1 by itself and opens the Dependency Dashboard issue. Its first update pull request will pin the GitHub Actions to commit hashes.
- [x] **Check the first run of `security.yml` on GitHub:** done. It passed on the pull request, with the pinned `trivy-action` and Trivy `v0.76.0`.
- [ ] **Remove the version overrides in `pom.xml`** (Jackson, Tomcat, PostgreSQL driver) once Spring Boot's own versions reach them. Renovate will not do this for us.
- [ ] **Look at `.trivyignore.yaml` before 2026-12-31:** the two `spring-webmvc` CVEs expire then and the build fails again. The real fix is the Boot 4 migration (roadmap item 10).
- [ ] **Scan the container image** once the app is built into one.

## Later

- [ ] **FX clearing account pattern:** unverified. Research before building FX (roadmap stretch).
