ALTER TABLE billable_metric
  ADD COLUMN dimension_properties TEXT[]
    CONSTRAINT billable_metric_dimension_properties_not_null NOT NULL
    DEFAULT '{}';

UPDATE billable_metric AS billable_metric
SET dimension_properties = price_policy.dimension_properties
FROM price_policy
WHERE price_policy.organization_id = billable_metric.organization_id
  AND price_policy.billable_metric_code = billable_metric.code;

ALTER TABLE price_rate
  DROP CONSTRAINT price_rate_price_policy_same_organization_fk;

ALTER TABLE price_rate
  ADD CONSTRAINT price_rate_billable_metric_same_organization_fk
    FOREIGN KEY (organization_id, billable_metric_code)
    REFERENCES billable_metric (organization_id, code);
