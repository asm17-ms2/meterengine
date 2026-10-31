ALTER TABLE payment_cancellation
  ADD COLUMN idempotency_fingerprint VARCHAR(64)
    CONSTRAINT payment_cancellation_idempotency_fingerprint_check
    CHECK (idempotency_fingerprint ~ '^[0-9a-f]{64}$');

CREATE OR REPLACE FUNCTION payment_cancellation_guard_update() RETURNS trigger AS $$
BEGIN
  IF OLD.status <> 'pending' THEN
    RAISE EXCEPTION 'payment_cancellation is final: % cannot change', OLD.status;
  END IF;
  IF NEW.status NOT IN ('done', 'failed')
     OR (NEW.id, NEW.organization_id, NEW.payment_attempt_id, NEW.mode,
         NEW.amount, NEW.reason, NEW.requested_at, NEW.idempotency_fingerprint)
        IS DISTINCT FROM
        (OLD.id, OLD.organization_id, OLD.payment_attempt_id, OLD.mode,
         OLD.amount, OLD.reason, OLD.requested_at, OLD.idempotency_fingerprint) THEN
    RAISE EXCEPTION 'payment_cancellation can only move from pending to done or failed';
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
