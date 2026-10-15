CREATE TABLE account (
    id          UUID PRIMARY KEY,
    currency    CHAR(3)         NOT NULL,
    type        VARCHAR(20)     NOT NULL CHECK (type IN ('CUSTOMER', 'SYSTEM')),
    status      VARCHAR(20)     NOT NULL CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    balance     NUMERIC(24,4)   NOT NULL DEFAULT 0,
    version     BIGINT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT now(),
    CHECK (type = 'SYSTEM' OR balance >= 0)
);

CREATE TABLE journal_entry (
    id          UUID PRIMARY KEY,
    type        VARCHAR(30)     NOT NULL CHECK (type IN ('DEPOSIT', 'TRANSFER', 'REVERSAL')),
    reference   VARCHAR(100)    NOT NULL,
    created_at  TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE TABLE posting (
    id          UUID PRIMARY KEY,
    entry_id    UUID          NOT NULL REFERENCES journal_entry (id),
    account_id  UUID          NOT NULL REFERENCES account (id),
    amount      NUMERIC(24,4) NOT NULL CHECK (amount <> 0),
    currency    CHAR(3)       NOT NULL
);

CREATE INDEX posting_entry_id_idx   ON posting (entry_id);
CREATE INDEX posting_account_id_idx ON posting (account_id);

-- One clearing account per currency. Customer deposits are funded through these,
-- so they go negative. Fixed ids because the app looks them up by type and currency.
CREATE UNIQUE INDEX account_system_currency_idx ON account (currency) WHERE type = 'SYSTEM';

INSERT INTO account (id, currency, type, status) VALUES
    ('00000000-0000-7000-8000-000000000001', 'GBP', 'SYSTEM', 'ACTIVE'),
    ('00000000-0000-7000-8000-000000000002', 'EUR', 'SYSTEM', 'ACTIVE'),
    ('00000000-0000-7000-8000-000000000003', 'USD', 'SYSTEM', 'ACTIVE'),
    ('00000000-0000-7000-8000-000000000004', 'JPY', 'SYSTEM', 'ACTIVE');
