ALTER TABLE price_policy RENAME COLUMN metric_code TO billable_metric_code;
ALTER TABLE price_rate RENAME COLUMN metric_code TO billable_metric_code;
ALTER TABLE invoice_line RENAME COLUMN metric_code TO billable_metric_code;
