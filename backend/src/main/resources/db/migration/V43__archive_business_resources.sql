ALTER TABLE customer ADD COLUMN archived_at TIMESTAMPTZ;
ALTER TABLE billable_metric ADD COLUMN archived_at TIMESTAMPTZ;
ALTER TABLE billable_metric ADD COLUMN active_from VARCHAR NOT NULL DEFAULT '0001-01';
ALTER TABLE billable_metric ADD COLUMN inactive_from VARCHAR;
CREATE TABLE billable_metric_period (
  id UUID CONSTRAINT billable_metric_period_pk PRIMARY KEY,
  organization_id UUID NOT NULL,
  billable_metric_code VARCHAR NOT NULL,
  active_from VARCHAR NOT NULL,
  active_until VARCHAR NOT NULL,
  CONSTRAINT billable_metric_period_metric_fk FOREIGN KEY (organization_id, billable_metric_code)
    REFERENCES billable_metric (organization_id, code),
  CONSTRAINT billable_metric_period_range_check CHECK (active_from < active_until)
);
CREATE INDEX billable_metric_period_organization_index ON billable_metric_period (organization_id, billable_metric_code);
ALTER TABLE price_rate ADD COLUMN archived_at TIMESTAMPTZ;
ALTER TABLE price_rate ADD COLUMN archived_from VARCHAR;
ALTER TABLE price_plan ADD COLUMN archived_at TIMESTAMPTZ;
ALTER TABLE price_plan ADD COLUMN archived_from VARCHAR;
DROP INDEX price_rate_organization_version_unique;
DROP INDEX price_rate_customer_version_unique;
CREATE UNIQUE INDEX price_rate_organization_version_unique
  ON price_rate (organization_id, billable_metric_code, dimension_values, effective_from)
  WHERE customer_id IS NULL AND archived_at IS NULL;
CREATE UNIQUE INDEX price_rate_customer_version_unique
  ON price_rate (organization_id, billable_metric_code, dimension_values, customer_id, effective_from)
  WHERE customer_id IS NOT NULL AND archived_at IS NULL;
DROP INDEX price_plan_organization_version_unique;
DROP INDEX price_plan_customer_version_unique;
CREATE UNIQUE INDEX price_plan_organization_version_unique ON price_plan (organization_id, effective_from)
  WHERE customer_id IS NULL AND archived_at IS NULL;
CREATE UNIQUE INDEX price_plan_customer_version_unique ON price_plan (organization_id, customer_id, effective_from)
  WHERE customer_id IS NOT NULL AND archived_at IS NULL;
ALTER TABLE billing_key ADD COLUMN archived_at TIMESTAMPTZ;
ALTER TABLE toss_payments_credential ADD COLUMN archived_at TIMESTAMPTZ;
CREATE TABLE billing_key_history (LIKE billing_key INCLUDING DEFAULTS);
ALTER TABLE billing_key_history ADD COLUMN history_id UUID NOT NULL DEFAULT gen_random_uuid() CONSTRAINT billing_key_history_pk PRIMARY KEY;
ALTER TABLE billing_key_history ADD COLUMN retired_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp();
CREATE INDEX billing_key_history_organization_index ON billing_key_history (organization_id, mode, customer_id);
CREATE TABLE toss_payments_credential_history (LIKE toss_payments_credential INCLUDING DEFAULTS);
ALTER TABLE toss_payments_credential_history ADD COLUMN history_id UUID NOT NULL DEFAULT gen_random_uuid() CONSTRAINT toss_payments_credential_history_pk PRIMARY KEY;
ALTER TABLE toss_payments_credential_history ADD COLUMN retired_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp();
CREATE INDEX toss_payments_credential_history_organization_index ON toss_payments_credential_history (organization_id, mode, updated_at);
