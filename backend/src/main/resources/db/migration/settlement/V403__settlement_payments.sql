-- Settlement payments (aggregate SettlementPayment, R16, R17): reported by the debtor and answered by the
-- creditor, or waived by the creditor. The ledger books them from their events; this table is not the balance.
-- No foreign keys to other modules: groups and people belong to them.
CREATE TABLE settlement_payments
(
  id          UUID PRIMARY KEY,
  group_id    UUID          NOT NULL,
  from_debtor UUID          NOT NULL,
  to_creditor UUID          NOT NULL,
  -- a payment or waiver of nothing is meaningless; the aggregate refuses it too
  amount      NUMERIC(19, 2) NOT NULL CHECK (amount > 0),
  currency    VARCHAR(3)    NOT NULL,
  -- PENDING, CONFIRMED, REJECTED or WAIVED (PaymentStatus)
  status      VARCHAR(20)   NOT NULL,
  -- the debtor's remark, later the rejection or waiver reason
  note        VARCHAR(500),
  created_at  TIMESTAMPTZ   NOT NULL,
  -- when the creditor answered; null while PENDING
  resolved_at TIMESTAMPTZ,
  -- optimistic lock: a confirm and a reject at the same time cannot both win
  version     BIGINT        NOT NULL,
  -- nobody pays or forgives themselves
  CONSTRAINT settlement_payments_two_people CHECK (from_debtor <> to_creditor)
);

-- the group's history and the reserved open debt per group (only PENDING and unbooked answers count)
CREATE INDEX settlement_payments_by_group_status ON settlement_payments (group_id, status);
-- the creditor's inbox: PENDING payments addressed to them
CREATE INDEX settlement_payments_by_creditor_status ON settlement_payments (to_creditor, status);
