# Roadmap

## 1. Ledger

**Status:** Domain objects done (`Money`, `Posting`, `JournalEntry`, `Account`). Left: the clearing and funding flow, UUIDv7 generation, and the optional database trigger.

**Tables** (in `V1__init.sql`)
- `account` (id, currency, type, status, balance, version, created_at). `type` is CUSTOMER or SYSTEM, and only SYSTEM accounts may go negative.
- `journal_entry` (id, type, reference, created_at). `type` is an enum: DEPOSIT, TRANSFER, REVERSAL.
- `posting` (id, entry_id, account_id, signed amount, currency)

**Account domain object** (done): `Account` holds a `Money` balance, and `applyPosting(posting)` returns a new `Account` with the balance changed. It rejects postings for another account or currency, on a non-ACTIVE account, or that would overdraw a customer account ("Insufficient funds"). It never changes the original, and `version` is left for the persistence layer to increment. Nothing yet connects a `JournalEntry` to its accounts: that coordination belongs to the transfer service (item 2).

**IDs:** UUIDv7, generated in the app (see `decisions/data-model.md`). Not built yet.

**Balance rule:** Enforced in the domain: `JournalEntry` refuses to build unless it has at least 2 postings that sum to zero per currency. Done. Optionally back it up with a deferred constraint trigger in the database (not done).

**Money value object** (done)
- Stored as `NUMERIC(24,4)`, with a currency-aware scale (GBP 2, JPY 0). At most 20 digits before the decimal point.
- Amounts with more decimals than the currency allows are rejected, not rounded. `RoundingMode.HALF_EVEN` only applies once we add `allocate` for splits.
- Compare with `compareTo`, not `equals`: `new BigDecimal("1.0").equals(new BigDecimal("1.00"))` is false.

**System accounts:** Money has to come from somewhere, so there is a clearing account per currency (GBP, EUR, USD, JPY), seeded in `V1__init.sql`, and customer accounts are funded through them. Bank interviewers notice this detail. The funding flow itself is not built yet.

**Sign convention** (decided): positive = credit (money in), negative = debit (money out). An account's balance is the sum of its postings. Customer accounts are liabilities from the bank's perspective.

## 2. Transfers API

**Status:** Not started. The domain exceptions it will map to problem types already exist (`InsufficientFundsException`, `AccountNotActiveException`, `CurrencyMismatchException`, `UnbalancedEntryException`); the handler, the request validation and the transfer service are not built.

**Validation**
- Amount is positive.
- Source and destination accounts differ.
- Both accounts are active.
- Currencies match until FX is built.
- Sufficient funds.

**Errors:** Use Spring's built-in `ProblemDetail`. Give each error a stable type URI (e.g. `/problems/insufficient-funds`) so clients can branch on it.

**Response:** Return 201 with the transfer resource.

## 3. Idempotency

**Status:** Not started.

**Table:** `idempotency_record` with key, client/principal, request hash, status, response code, response body and expiry.

**Flow**
1. Insert the record first. The unique constraint on (client, key) handles races.
2. Write the stored response in the same transaction as the postings, so the two can never diverge.

**Edge cases**
- Same key and same request hash: replay the stored response.
- Same key with a different request: 422.
- Original request still in progress: 409.
- Scope keys per client, otherwise one user's key could collide with another's.

## 4. Concurrency

**Status:** Not started.

**Pessimistic locking:** `SELECT ... FOR UPDATE` on both accounts, always locked in ascending id order. The safer default for hot accounts.

**Optimistic locking:** `@Version` with retries. Fine under low contention, degrades under load. Be able to explain when to pick each.

**What to lock:** "Derive balance from postings" (item 5) conflicts with "lock something to check funds". The common answer is a balance column on the account row, updated in the same transaction and locked during the check. Postings remain the source of truth, and a reconciliation job verifies the column against them. This leads into the stretch reconciliation job.

**Isolation:** `READ COMMITTED` plus row locks is enough. Know why `SERIALIZABLE` isn't necessary here.

**Lock timeouts:** Set them so stuck requests fail fast rather than hang.

**Tests**
- Fire N concurrent transfers with an `ExecutorService` and a `CountDownLatch` so they start together.
- Assert total money in the system is unchanged and no balance went negative.
- Run A→B and B→A simultaneously to prove there's no deadlock.

## 5. Statements

**Status:** Not started.

**Pagination:** Keyset (cursor) pagination on `(created_at, id)` rather than offset. A common follow-up question.

**Corrections:** A reversal is a new journal entry that links to the original. Revoke `UPDATE`/`DELETE` on the ledger tables at the database level to prove records are immutable.

**Performance:** Balance snapshots or checkpoints are the answer for very long histories.

## 6. Security

**Status:** Not started.

**Ownership checks:** A valid JWT isn't enough. The user must own the source account. This is broken object-level authorisation, the top OWASP API risk, and an easy win to talk about.

**Local IdP:** Run Keycloak in docker-compose.

## 7. Events

**Status:** Not started.

**Outbox:** Write the outbox row in the same transaction as the postings.

**Relay**
- A poller using `FOR UPDATE SKIP LOCKED` is simple.
- Debezium (change data capture) is the more "proper" option.
- Mention both.

**Delivery guarantees:** At-least-once, so consumers must be idempotent. Use account id as the Kafka key to keep per-account ordering.

## 8–9. Observability and docs

**Status:** Not started.

**Business metrics:** Expose transfers by outcome and insufficient-funds rate, not just JVM metrics.

**Correlation IDs:** Use Micrometer Tracing to carry them into the MDC and Kafka headers.

**API docs:** OpenAPI annotations are routine. Keep this phase brief.

## 10. Boot 4 migration

**Status:** Not started.

Expect these changes:
- Spring Framework 7
- Jakarta EE 11
- Hibernate 7
- Jackson 3, whose packages move to `tools.jackson`
- Modularised starters
- JSpecify null-safety

Run the OpenRewrite migration recipe first, then fix the remainder by hand. The PR description should list what broke and why. That write-up is the valuable part.

## Stretch priority

1. Payment state machine and reconciliation: distinctly banking topics.
2. FX.
3. Rate limiting and AWS deployment add little, since the CV already covers Apigee, Kong and AWS.
