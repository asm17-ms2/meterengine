CREATE TABLE invoice (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id UUID NOT NULL REFERENCES organization(id),
  customer_id     UUID NOT NULL,
  period          VARCHAR NOT NULL CONSTRAINT invoice_period_format
                    CHECK (period ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'),
  supply_amount   BIGINT NOT NULL,
  tax_amount      BIGINT NOT NULL,
  finalized_at    TIMESTAMPTZ NOT NULL,

  CONSTRAINT invoice_org_id_unique UNIQUE (organization_id, id),

  CONSTRAINT invoice_customer_period_unique UNIQUE (organization_id, customer_id, period),

  CONSTRAINT invoice_customer_same_org
    FOREIGN KEY (organization_id, customer_id)
    REFERENCES customer (organization_id, id)
);

CREATE TABLE invoice_line (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id  UUID NOT NULL REFERENCES organization(id),
  invoice_id       UUID NOT NULL,
  metric_code      VARCHAR NOT NULL,
  target_property  VARCHAR,
  dimension_values JSONB NOT NULL,
  quantity         NUMERIC NOT NULL,
  unit_price       NUMERIC NOT NULL CHECK (unit_price >= 0),
  amount           BIGINT NOT NULL,

  CONSTRAINT invoice_line_metric_unique
    UNIQUE (organization_id, invoice_id, metric_code, dimension_values),

  CONSTRAINT invoice_line_invoice_same_org
    FOREIGN KEY (organization_id, invoice_id)
    REFERENCES invoice (organization_id, id)
);
