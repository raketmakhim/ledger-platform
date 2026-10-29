-- Two customer accounts for trying the API. There is no endpoint to create accounts yet.
-- Safe to run again: it puts the balances back to their starting values.
INSERT INTO account (id, currency, type, status, balance) VALUES
    ('11111111-1111-7111-8111-111111111111', 'GBP', 'CUSTOMER', 'ACTIVE', 1000.00),
    ('22222222-2222-7222-8222-222222222222', 'GBP', 'CUSTOMER', 'ACTIVE', 0.00)
ON CONFLICT (id) DO UPDATE SET balance = EXCLUDED.balance;
