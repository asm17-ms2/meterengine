ALTER TABLE payment_attempt
  ADD COLUMN mode VARCHAR NOT NULL DEFAULT 'test'
    CONSTRAINT payment_attempt_mode_check CHECK (mode IN ('test', 'live'));

ALTER TABLE payment_attempt ALTER COLUMN mode DROP DEFAULT;

ALTER TABLE payment_attempt
  ADD CONSTRAINT payment_attempt_organization_id_unique UNIQUE (organization_id, id);

CREATE OR REPLACE FUNCTION payment_attempt_guard_update() RETURNS trigger AS $$
BEGIN
  IF OLD.status <> 'pending' THEN
    RAISE EXCEPTION 'payment_attempt is final: % cannot change', OLD.status;
  END IF;
  IF NEW.status NOT IN ('done', 'failed')
     OR (NEW.id, NEW.organization_id, NEW.customer_id, NEW.invoice_id, NEW.mode,
         NEW.amount, NEW.order_name, NEW.requested_at)
        IS DISTINCT FROM
        (OLD.id, OLD.organization_id, OLD.customer_id, OLD.invoice_id, OLD.mode,
         OLD.amount, OLD.order_name, OLD.requested_at) THEN
    RAISE EXCEPTION 'payment_attempt can only move from pending to done or failed';
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP INDEX payment_attempt_organization_invoice_unique;

CREATE UNIQUE INDEX payment_attempt_organization_invoice_mode_unique
  ON payment_attempt (organization_id, invoice_id, mode)
  WHERE status IN ('pending', 'done');

CREATE INDEX payment_attempt_organization_invoice_mode_requested_at
  ON payment_attempt (organization_id, invoice_id, mode, requested_at DESC, id DESC);

CREATE INDEX payment_attempt_pending_requested_at
  ON payment_attempt (requested_at)
  WHERE status = 'pending';

CREATE INDEX payment_attempt_organization_mode_done_completed_at
  ON payment_attempt (organization_id, mode, completed_at)
  WHERE status = 'done';
