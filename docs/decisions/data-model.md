# Data model and database

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

## UUIDv7 IDs, generated in the app

Chose UUIDv7 (RFC 9562) for entity IDs, generated in Java rather than by the database. The leading timestamp keeps B-tree inserts sequential, which avoids the index fragmentation of random UUIDv4. UUIDs are also safe to expose in URLs (no guessable `/accounts/1042`) and can be created by the domain before saving, which suits idempotency and outbox events. Postgres 17 has no built-in `uuidv7()`, so a small library generates them.

Gave up: `bigint` is smaller (8 bytes vs 16) and slightly faster. Revisit by moving to Postgres 18, which has a built-in `uuidv7()`.

## java-uuid-generator for UUIDv7

Use a library to generate UUIDv7 ids, not our own code: ids are not where the learning is, and a time-ordered UUID done wrong (clock ordering, randomness) is an easy bug. Chose `com.fasterxml.uuid:java-uuid-generator` (5.2.0, last released December 2025, from the FasterXML/Jackson maintainers). The other candidate, `uuid-creator` (6.1.1), was last released April 2025. Both support UUIDv7. The dependency is added when the first code that creates ids is written, and ids are generated outside `domain` objects' own checks, so `domain` stays plain Java.

Gave up: nothing much. A small extra dependency. Revisit if Postgres 18's built-in `uuidv7()` is adopted.

## Sign convention

Postings are signed: positive = credit (money in), negative = debit (money out). An account's balance is the sum of its postings. Customer accounts are liabilities from the bank's perspective; system accounts such as clearing will usually be negative.

## Stored balance column

`account` keeps a `balance` and a `version`, updated in the same transaction as the postings. Postings stay the source of truth; a reconciliation job checks the column against them. The row can be locked for the funds check. `CHECK (balance >= 0)` applies to customer accounts only, since system accounts may go negative.

Gave up: the risk of the column drifting from the postings.

## VARCHAR + CHECK over Postgres ENUM

`type` and `status` are `VARCHAR` with `CHECK` constraints. Postgres `ENUM` types are painful to change later.

## TIMESTAMPTZ, always UTC

All times are `TIMESTAMPTZ`, so each is an exact moment with no timezone ambiguity.

## Clearing accounts seeded for GBP, EUR, USD and JPY

There is one `SYSTEM` clearing account per currency, created by `V1__init.sql` with fixed ids, and a partial unique index (`account_system_currency_idx`) stops a second one for the same currency. Deposits are funded through them, so they go negative: each one's balance mirrors the customer money held in that currency. Banks and ERP systems (Oracle GL, Sage) generally set these accounts up deliberately in advance, and seeding avoids a create-on-first-use race and a typo quietly creating a new system account. JPY is included to exercise the zero-decimal currency path in the real database.

Gave up: supporting a new currency needs a new migration. The unique index also means one system account per currency, so a fee or suspense account later needs a `purpose` column and a changed index.

## Reversals are new entries linked to the original

A posted entry is never edited or deleted. To undo one, post a new `REVERSAL` entry with the opposite signs, linked to the original by a nullable `reverses_entry_id` column on `journal_entry`. A unique index on that column means an entry can be reversed only once, and a reversal cannot be reversed. A mistake after that is fixed with a new corrected entry. Each reversal should record a reason. This follows how ERP systems (Intacct, NetSuite, Sage) treat reversals, and keeps the audit trail intact. Not built yet: the column, the index and the `reason` field are added when reversals are implemented.

Gave up: nothing in the schema yet. Closed accounting periods, which ERP systems also check, do not apply here.
