# Todo

- [ ] **Per-currency balance rule:** `JournalEntry` must refuse to build unless its postings sum to zero **per currency** (at least 2 postings). Backed by Fragment and Infor docs.
- [ ] **Maximum amount check in `Money`:** fail in the domain with a clear message instead of at the database (`NUMERIC(19,4)` allows 15 digits before the decimal point). Same idea as commercetools' `MoneyOverflow` error.
- [ ] **FX clearing account pattern:** unverified. Research before building FX (roadmap stretch).
