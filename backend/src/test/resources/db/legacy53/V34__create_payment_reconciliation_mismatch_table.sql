CREATE TABLE payment_reconciliation_mismatch (
  id                   UUID NOT NULL,
  organization_id      UUID NOT NULL,
  mode                 VARCHAR NOT NULL
                         CONSTRAINT payment_reconciliation_mismatch_mode_check
                         CHECK (mode IN ('test', 'live')),
  kind                 VARCHAR NOT NULL
                         CONSTRAINT payment_reconciliation_mismatch_kind_check
                         CHECK (kind IN ('missing_in_meterengine', 'missing_in_toss_payments',
                                         'amount_mismatch', 'status_mismatch')),
  order_id             VARCHAR NOT NULL,
  payment_key          VARCHAR,
  transaction_key      VARCHAR,
  meterengine_amount   BIGINT,
  toss_payments_amount BIGINT,
  transacted_at        TIMESTAMPTZ,
  detected_at          TIMESTAMPTZ NOT NULL,

  CONSTRAINT payment_reconciliation_mismatch_pk PRIMARY KEY (id),

  CONSTRAINT payment_reconciliation_mismatch_organization_fk
    FOREIGN KEY (organization_id) REFERENCES organization (id),

  CONSTRAINT payment_reconciliation_mismatch_kind_order_transaction_unique
    UNIQUE NULLS NOT DISTINCT (organization_id, mode, kind, order_id, transaction_key)
);

CREATE FUNCTION payment_reconciliation_mismatch_block_mutation() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'payment_reconciliation_mismatch is append-only: UPDATE/DELETE/TRUNCATE is blocked';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER payment_reconciliation_mismatch_block_update
  BEFORE UPDATE ON payment_reconciliation_mismatch
  FOR EACH ROW EXECUTE FUNCTION payment_reconciliation_mismatch_block_mutation();

CREATE TRIGGER payment_reconciliation_mismatch_block_delete
  BEFORE DELETE ON payment_reconciliation_mismatch
  FOR EACH ROW EXECUTE FUNCTION payment_reconciliation_mismatch_block_mutation();

CREATE TRIGGER payment_reconciliation_mismatch_block_truncate
  BEFORE TRUNCATE ON payment_reconciliation_mismatch
  FOR EACH STATEMENT EXECUTE FUNCTION payment_reconciliation_mismatch_block_mutation();
