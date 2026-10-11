# Roadmap

## 1. Ledger

**Tables**
- `account` (id, currency, type, status)
- `journal_entry` (id, type, reference, created_at)
- `posting` (id, entry_id, account_id, signed amount, currency)

**Balance rule:** Enforce "postings sum to zero per currency" in a domain object (`JournalEntry` refuses to build if unbalanced). Optionally back it up with a deferred constraint trigger in the database.

**Money value object**
- Store amounts as `NUMERIC(19,4)` with a currency-aware scale (GBP 2, JPY 0).
- Use `RoundingMode.HALF_EVEN`.
- Compare with `compareTo`, not `equals`: `new BigDecimal("1.0").equals(new BigDecimal("1.00"))` is false.

**System accounts:** Money has to come from somewhere, so add a clearing or suspense account and fund customer accounts through it. Bank interviewers notice this detail.

**Sign convention:** Know which one you've used. Customer accounts are liabilities from the bank's perspective.

## 2. Transfers API

**Validation**
- Amount is positive.
- Source and destination accounts differ.
- Both accounts are active.
- Currencies match until FX is built.
- Sufficient funds.

**Errors:** Use Spring's built-in `ProblemDetail`. Give each error a stable type URI (e.g. `/problems/insufficient-funds`) so clients can branch on it.

**Response:** Return 201 with the transfer resource.

## 3. Idempotency

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

**Pagination:** Keyset (cursor) pagination on `(created_at, id)` rather than offset. A common follow-up question.

**Corrections:** A reversal is a new journal entry that links to the original. Revoke `UPDATE`/`DELETE` on the ledger tables at the database level to prove records are immutable.

**Performance:** Balance snapshots or checkpoints are the answer for very long histories.

## 6. Security

**Ownership checks:** A valid JWT isn't enough. The user must own the source account. This is broken object-level authorisation, the top OWASP API risk, and an easy win to talk about.

**Local IdP:** Run Keycloak in docker-compose.

## 7. Events

**Outbox:** Write the outbox row in the same transaction as the postings.

**Relay**
- A poller using `FOR UPDATE SKIP LOCKED` is simple.
- Debezium (change data capture) is the more "proper" option.
- Mention both.

**Delivery guarantees:** At-least-once, so consumers must be idempotent. Use account id as the Kafka key to keep per-account ordering.

## 8–9. Observability and docs

**Business metrics:** Expose transfers by outcome and insufficient-funds rate, not just JVM metrics.

**Correlation IDs:** Use Micrometer Tracing to carry them into the MDC and Kafka headers.

**API docs:** OpenAPI annotations are routine. Keep this phase brief.

## 10. Boot 4 migration

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
