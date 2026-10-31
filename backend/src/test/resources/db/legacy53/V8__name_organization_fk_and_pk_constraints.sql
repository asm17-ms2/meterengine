ALTER TABLE customer RENAME CONSTRAINT customer_organization_id_fkey TO customer_organization_fk;
ALTER TABLE billable_metric RENAME CONSTRAINT billable_metric_organization_id_fkey TO billable_metric_organization_fk;
ALTER TABLE event RENAME CONSTRAINT event_organization_id_fkey TO event_organization_fk;
ALTER TABLE price_policy RENAME CONSTRAINT price_policy_organization_id_fkey TO price_policy_organization_fk;
ALTER TABLE price_rate RENAME CONSTRAINT price_rate_organization_id_fkey TO price_rate_organization_fk;
ALTER TABLE invoice RENAME CONSTRAINT invoice_organization_id_fkey TO invoice_organization_fk;
ALTER TABLE invoice_line RENAME CONSTRAINT invoice_line_organization_id_fkey TO invoice_line_organization_fk;

ALTER TABLE organization RENAME CONSTRAINT organization_pkey TO organization_pk;
ALTER TABLE customer RENAME CONSTRAINT customer_pkey TO customer_pk;
ALTER TABLE billable_metric RENAME CONSTRAINT billable_metric_pkey TO billable_metric_pk;
ALTER TABLE event RENAME CONSTRAINT event_pkey TO event_pk;
ALTER TABLE price_policy RENAME CONSTRAINT price_policy_pkey TO price_policy_pk;
ALTER TABLE price_rate RENAME CONSTRAINT price_rate_pkey TO price_rate_pk;
ALTER TABLE invoice RENAME CONSTRAINT invoice_pkey TO invoice_pk;
ALTER TABLE invoice_line RENAME CONSTRAINT invoice_line_pkey TO invoice_line_pk;
