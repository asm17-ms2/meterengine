ALTER TABLE invoice_line DROP CONSTRAINT invoice_line_invoice_billable_metric_dimension_values_unique;
ALTER TABLE invoice_line ALTER COLUMN billable_metric_code DROP NOT NULL;
ALTER TABLE invoice_line ADD COLUMN line_type VARCHAR NOT NULL DEFAULT 'per_unit';
ALTER TABLE invoice_line ADD COLUMN tier_index INTEGER NOT NULL DEFAULT 0;
ALTER TABLE invoice_line ADD CONSTRAINT invoice_line_type_check CHECK (
  (line_type IN ('per_unit', 'volume') AND billable_metric_code IS NOT NULL AND tier_index = 0)
  OR (line_type = 'graduated_tier' AND billable_metric_code IS NOT NULL AND tier_index >= 0)
  OR (line_type IN ('base_fee', 'minimum_adjustment') AND billable_metric_code IS NULL
    AND tier_index = 0 AND dimension_values = '{}'::jsonb AND quantity = 1));
CREATE UNIQUE INDEX invoice_line_invoice_billable_metric_dimension_values_unique
  ON invoice_line (organization_id, invoice_id, billable_metric_code, dimension_values, line_type, tier_index)
  WHERE billable_metric_code IS NOT NULL;
CREATE UNIQUE INDEX invoice_line_invoice_surcharge_unique
  ON invoice_line (organization_id, invoice_id, line_type) WHERE billable_metric_code IS NULL;
