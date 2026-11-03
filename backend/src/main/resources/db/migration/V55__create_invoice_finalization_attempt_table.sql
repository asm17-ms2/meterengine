CREATE TABLE invoice_finalization_attempt (
  id                   UUID NOT NULL,
  organization_id      UUID NOT NULL,
  environment_id       UUID,
  environment_scope_id UUID GENERATED ALWAYS AS
                         (COALESCE(environment_id, '00000000-0000-0000-0000-000000000000'::uuid))
                         STORED NOT NULL,
  customer_id          UUID NOT NULL,
  period               VARCHAR NOT NULL
                         CONSTRAINT invoice_finalization_attempt_period_format
                         CHECK (period ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
  result               VARCHAR NOT NULL
                         CONSTRAINT invoice_finalization_attempt_result_check
                         CHECK (result IN
                           ('finalized', 'no_events', 'skipped', 'zero_amount', 'held', 'failed')),
  reason               VARCHAR,
  unpriced_usages      JSONB,
  attempted_at         TIMESTAMPTZ NOT NULL,

  CONSTRAINT invoice_finalization_attempt_pk PRIMARY KEY (id),

  CONSTRAINT invoice_finalization_attempt_organization_fk
    FOREIGN KEY (organization_id) REFERENCES organization (id),

  CONSTRAINT invoice_finalization_attempt_environment_fk
    FOREIGN KEY (organization_id, environment_id)
    REFERENCES organization_environment (organization_id, id),

  CONSTRAINT invoice_finalization_attempt_customer_same_environment_fk
    FOREIGN KEY (organization_id, environment_scope_id, customer_id)
    REFERENCES customer (organization_id, environment_scope_id, id),

  CONSTRAINT invoice_finalization_attempt_held_unpriced_usages_check
    CHECK ((result = 'held') = (unpriced_usages IS NOT NULL))
);

-- 끝난 (고객, 달)을 거르는 조회와, 아직 열린 보류와 실패만 다시 보는 조회가 표 전체를 읽지 않게 한다.
CREATE INDEX invoice_finalization_attempt_finished
  ON invoice_finalization_attempt (organization_id, environment_scope_id, period, customer_id)
  WHERE result IN ('finalized', 'no_events', 'skipped', 'zero_amount');

CREATE INDEX invoice_finalization_attempt_open
  ON invoice_finalization_attempt
    (organization_id, environment_scope_id, customer_id, period, attempted_at)
  WHERE result IN ('held', 'failed');

CREATE TABLE invoice_finalization_month (
  organization_id      UUID NOT NULL,
  environment_id       UUID,
  environment_scope_id UUID GENERATED ALWAYS AS
                         (COALESCE(environment_id, '00000000-0000-0000-0000-000000000000'::uuid))
                         STORED NOT NULL,
  period               VARCHAR NOT NULL
                         CONSTRAINT invoice_finalization_month_period_format
                         CHECK (period ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
  swept_at             TIMESTAMPTZ NOT NULL,

  CONSTRAINT invoice_finalization_month_pk
    PRIMARY KEY (organization_id, environment_scope_id, period),

  CONSTRAINT invoice_finalization_month_organization_fk
    FOREIGN KEY (organization_id) REFERENCES organization (id),

  CONSTRAINT invoice_finalization_month_environment_fk
    FOREIGN KEY (organization_id, environment_id)
    REFERENCES organization_environment (organization_id, id)
);

CREATE FUNCTION invoice_finalization_attempt_block_mutation() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION '% is append-only: UPDATE/DELETE/TRUNCATE is blocked', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER invoice_finalization_attempt_block_update
  BEFORE UPDATE ON invoice_finalization_attempt
  FOR EACH ROW EXECUTE FUNCTION invoice_finalization_attempt_block_mutation();

CREATE TRIGGER invoice_finalization_attempt_block_delete
  BEFORE DELETE ON invoice_finalization_attempt
  FOR EACH ROW EXECUTE FUNCTION invoice_finalization_attempt_block_mutation();

CREATE TRIGGER invoice_finalization_attempt_block_truncate
  BEFORE TRUNCATE ON invoice_finalization_attempt
  FOR EACH STATEMENT EXECUTE FUNCTION invoice_finalization_attempt_block_mutation();

CREATE TRIGGER invoice_finalization_month_block_update
  BEFORE UPDATE ON invoice_finalization_month
  FOR EACH ROW EXECUTE FUNCTION invoice_finalization_attempt_block_mutation();

CREATE TRIGGER invoice_finalization_month_block_delete
  BEFORE DELETE ON invoice_finalization_month
  FOR EACH ROW EXECUTE FUNCTION invoice_finalization_attempt_block_mutation();

CREATE TRIGGER invoice_finalization_month_block_truncate
  BEFORE TRUNCATE ON invoice_finalization_month
  FOR EACH STATEMENT EXECUTE FUNCTION invoice_finalization_attempt_block_mutation();
