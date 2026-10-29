-- Two funded accounts for one demo customer, for trying transfers. New accounts opened through POST /accounts
-- start at zero and there is no deposit endpoint yet, so this is how money gets in.
-- The customer id is the collection variable customerId in the Postman collection.
-- Safe to run again: it puts the balances back to their starting values.
INSERT INTO account (id, owner_id, currency, type, status, balance) VALUES
    ('11111111-1111-7111-8111-111111111111', '33333333-3333-7333-8333-333333333333', 'GBP', 'CUSTOMER', 'ACTIVE', 1000.00),
    ('22222222-2222-7222-8222-222222222222', '33333333-3333-7333-8333-333333333333', 'GBP', 'CUSTOMER', 'ACTIVE', 0.00)
ON CONFLICT (id) DO UPDATE SET balance = EXCLUDED.balance;
