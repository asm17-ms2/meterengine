CREATE TABLE price_policy (
  organization_id      UUID NOT NULL REFERENCES organization(id),
  metric_code          VARCHAR NOT NULL,
  dimension_properties TEXT[] NOT NULL DEFAULT '{}',

  PRIMARY KEY (organization_id, metric_code),

  CONSTRAINT price_policy_metric_same_org
    FOREIGN KEY (organization_id, metric_code)
    REFERENCES billable_metric (organization_id, code)
);

CREATE TABLE price_rate (
  organization_id  UUID NOT NULL REFERENCES organization(id),
  metric_code      VARCHAR NOT NULL,
  dimension_values JSONB NOT NULL,
  unit_price       NUMERIC NOT NULL CHECK (unit_price >= 0),

  PRIMARY KEY (organization_id, metric_code, dimension_values),

  CONSTRAINT price_rate_policy_fk
    FOREIGN KEY (organization_id, metric_code)
    REFERENCES price_policy (organization_id, metric_code)
);

INSERT INTO price_policy (organization_id, metric_code)
SELECT organization_id, code FROM billable_metric;

INSERT INTO price_rate (organization_id, metric_code, dimension_values, unit_price)
SELECT organization_id, code, '{}'::jsonb, unit_price FROM billable_metric;

ALTER TABLE billable_metric DROP COLUMN unit_price;
