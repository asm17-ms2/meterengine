CREATE TABLE payment_attempt (
  id              UUID NOT NULL,
  organization_id UUID NOT NULL,
  customer_id     UUID NOT NULL,
  invoice_id      UUID NOT NULL,
  amount          BIGINT NOT NULL CONSTRAINT payment_attempt_amount_check CHECK (amount > 0),
  order_name      VARCHAR(100) NOT NULL,
  status          VARCHAR NOT NULL
                    CONSTRAINT payment_attempt_status_check
                    CHECK (status IN ('pending', 'done', 'failed')),
  payment_key     VARCHAR,
  failure_code    VARCHAR,
  failure_message VARCHAR,
  requested_at    TIMESTAMPTZ NOT NULL,
  completed_at    TIMESTAMPTZ,

  CONSTRAINT payment_attempt_pk PRIMARY KEY (id),

  CONSTRAINT payment_attempt_customer_same_organization_fk
    FOREIGN KEY (organization_id, customer_id)
    REFERENCES customer (organization_id, id),

  CONSTRAINT payment_attempt_status_result_check CHECK (
    (status = 'pending' AND completed_at IS NULL AND payment_key IS NULL
      AND failure_code IS NULL AND failure_message IS NULL)
    OR (status = 'done' AND completed_at IS NOT NULL AND payment_key IS NOT NULL
      AND failure_code IS NULL AND failure_message IS NULL)
    OR (status = 'failed' AND completed_at IS NOT NULL AND failure_code IS NOT NULL
      AND payment_key IS NULL)
  )
);

CREATE UNIQUE INDEX payment_attempt_organization_invoice_unique
  ON payment_attempt (organization_id, invoice_id)
  WHERE status IN ('pending', 'done');

CREATE INDEX payment_attempt_pending_requested_at
  ON payment_attempt (requested_at)
  WHERE status = 'pending';
