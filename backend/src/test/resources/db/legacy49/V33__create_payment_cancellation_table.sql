CREATE TABLE payment_cancellation (
  id                 UUID NOT NULL,
  organization_id    UUID NOT NULL,
  payment_attempt_id UUID NOT NULL,
  mode               VARCHAR NOT NULL
                       CONSTRAINT payment_cancellation_mode_check
                       CHECK (mode IN ('test', 'live')),
  amount             BIGINT NOT NULL CONSTRAINT payment_cancellation_amount_check CHECK (amount > 0),
  reason             VARCHAR(200) NOT NULL,
  status             VARCHAR NOT NULL
                       CONSTRAINT payment_cancellation_status_check
                       CHECK (status IN ('pending', 'done', 'failed')),
  transaction_key    VARCHAR,
  failure_code       VARCHAR,
  failure_message    VARCHAR,
  requested_at       TIMESTAMPTZ NOT NULL,
  completed_at       TIMESTAMPTZ,

  CONSTRAINT payment_cancellation_pk PRIMARY KEY (id),

  CONSTRAINT payment_cancellation_payment_attempt_same_organization_fk
    FOREIGN KEY (organization_id, payment_attempt_id)
    REFERENCES payment_attempt (organization_id, id),

  CONSTRAINT payment_cancellation_status_result_check CHECK (
    (status = 'pending' AND completed_at IS NULL AND transaction_key IS NULL
      AND failure_code IS NULL AND failure_message IS NULL)
    OR (status = 'done' AND completed_at IS NOT NULL AND transaction_key IS NOT NULL
      AND failure_code IS NULL AND failure_message IS NULL)
    OR (status = 'failed' AND completed_at IS NOT NULL AND failure_code IS NOT NULL
      AND transaction_key IS NULL)
  )
);

CREATE UNIQUE INDEX payment_cancellation_payment_attempt_pending_unique
  ON payment_cancellation (payment_attempt_id)
  WHERE status = 'pending';

CREATE INDEX payment_cancellation_organization_payment_attempt_done
  ON payment_cancellation (organization_id, payment_attempt_id)
  WHERE status = 'done';

CREATE INDEX payment_cancellation_organization_mode_done_completed_at
  ON payment_cancellation (organization_id, mode, completed_at)
  WHERE status = 'done';

CREATE FUNCTION payment_cancellation_guard_update() RETURNS trigger AS $$
BEGIN
  IF OLD.status <> 'pending' THEN
    RAISE EXCEPTION 'payment_cancellation is final: % cannot change', OLD.status;
  END IF;
  IF NEW.status NOT IN ('done', 'failed')
     OR (NEW.id, NEW.organization_id, NEW.payment_attempt_id, NEW.mode,
         NEW.amount, NEW.reason, NEW.requested_at)
        IS DISTINCT FROM
        (OLD.id, OLD.organization_id, OLD.payment_attempt_id, OLD.mode,
         OLD.amount, OLD.reason, OLD.requested_at) THEN
    RAISE EXCEPTION 'payment_cancellation can only move from pending to done or failed';
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER payment_cancellation_guard_update
  BEFORE UPDATE ON payment_cancellation
  FOR EACH ROW EXECUTE FUNCTION payment_cancellation_guard_update();

CREATE FUNCTION payment_cancellation_block_delete() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'payment_cancellation is append-only: DELETE/TRUNCATE is blocked';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER payment_cancellation_block_delete
  BEFORE DELETE ON payment_cancellation
  FOR EACH ROW EXECUTE FUNCTION payment_cancellation_block_delete();

CREATE TRIGGER payment_cancellation_block_truncate
  BEFORE TRUNCATE ON payment_cancellation
  FOR EACH STATEMENT EXECUTE FUNCTION payment_cancellation_block_delete();
