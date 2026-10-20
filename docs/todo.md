# Todo

- [x] **Per-currency balance rule:** `JournalEntry` must refuse to build unless its postings sum to zero **per currency** (at least 2 postings). Backed by Fragment and Infor docs.
- [x] **Maximum amount check in `Money`:** fail in the domain with a clear message instead of at the database (`NUMERIC(24,4)` allows 20 digits before the decimal point). Same idea as commercetools' `MoneyOverflow` error.
- [x] **Reject huge exponents in `Money`:** the size check now runs before `setScale`, so `1E+999999999` is rejected at once instead of building a billion-digit number.

## For the database adapter

Found in review. None of these is a bug yet, because nothing is saved anywhere, but each becomes one once the adapter exists.

- [ ] **One transaction around a deposit:** the three saves (clearing account, customer account, entry) must commit together, or a crash leaves money moved with no entry. Decide where it lives: `@Transactional` on `DepositService` (leaning), a decorator in the adapter, or a transaction-runner port. Add an integration test where the entry save fails halfway and both balances are unchanged.
- [ ] **Duplicate deposit reference:** add `UNIQUE (type, reference)` to `journal_entry` as a backstop, and a `DuplicateReferenceException` (a 409 in the API). Callers must supply a trustworthy unique id (`TxId` or UETR), because `EndToEndId` can be a placeholder. Replaying the original response belongs to idempotency (roadmap item 3).
- [ ] **Lost updates on balances:** two concurrent deposits can both read 100 and save 110. Lock the accounts with `SELECT ... FOR UPDATE` in ascending id order, and keep the `version` check as a safety net. Prove it with the concurrent-deposits test from roadmap item 4.
- [ ] **Hot clearing account:** every deposit in a currency updates that currency's one clearing account, so deposits in a currency run one at a time. Accept and document this for now. The sourced pattern (Modern Treasury, TigerBeetle) is to not lock hot accounts and not make deposits wait on their balance: apply their balance asynchronously, or keep cumulative debits and credits. Splitting one clearing account into several is not confirmed by any source found.
- [ ] **Database should enforce the domain:** `posting.currency` can disagree with the account's currency. Make `(id, currency)` unique on `account` and add a composite foreign key `(account_id, currency)` from `posting`. Add the deferred zero-sum constraint trigger (roadmap item 1, optional) so an unbalanced entry can't be stored.
- [ ] **Transfers need the same three:** the transaction, the duplicate-reference constraint, and locking. Lock both accounts in ascending id order, not source then destination (`TransferService` loads source first), or two opposite transfers (A to B and B to A) can deadlock. Prove it with the A to B and B to A test from roadmap item 4.
- [ ] **Small test gap:** the unsupported-currency test checks that no entry was saved, but not that the customer account is unchanged.

## Later

- [ ] **FX clearing account pattern:** unverified. Research before building FX (roadmap stretch).
