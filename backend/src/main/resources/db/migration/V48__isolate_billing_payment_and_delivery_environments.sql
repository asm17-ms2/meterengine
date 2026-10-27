ALTER TABLE organization_environment ADD COLUMN business_registration_number VARCHAR(10);
ALTER TABLE organization_environment ADD COLUMN representative_name VARCHAR(255);
ALTER TABLE organization_environment ADD COLUMN business_address VARCHAR(1000);

ALTER TABLE billable_metric ADD COLUMN environment_id UUID;
ALTER TABLE billable_metric ADD COLUMN environment_scope_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000'::uuid;
ALTER TABLE billable_metric ADD CONSTRAINT billable_metric_environment_scope_check CHECK (environment_scope_id = COALESCE(environment_id, '00000000-0000-0000-0000-000000000000'::uuid));
ALTER TABLE billable_metric ADD CONSTRAINT billable_metric_environment_fk FOREIGN KEY (organization_id, environment_id) REFERENCES organization_environment (organization_id, id);

ALTER TABLE billable_metric_period ADD COLUMN environment_id UUID;
ALTER TABLE billable_metric_period ADD COLUMN environment_scope_id UUID GENERATED ALWAYS AS (COALESCE(environment_id, '00000000-0000-0000-0000-000000000000'::uuid)) STORED NOT NULL;
ALTER TABLE billable_metric_period ADD CONSTRAINT billable_metric_period_environment_fk FOREIGN KEY (organization_id, environment_id) REFERENCES organization_environment (organization_id, id);

ALTER TABLE price_rate ADD COLUMN environment_id UUID;
ALTER TABLE price_rate ADD COLUMN environment_scope_id UUID GENERATED ALWAYS AS (COALESCE(environment_id, '00000000-0000-0000-0000-000000000000'::uuid)) STORED NOT NULL;
ALTER TABLE price_rate ADD CONSTRAINT price_rate_environment_fk FOREIGN KEY (organization_id, environment_id) REFERENCES organization_environment (organization_id, id);

ALTER TABLE price_plan ADD COLUMN environment_id UUID;
ALTER TABLE price_plan ADD COLUMN environment_scope_id UUID GENERATED ALWAYS AS (COALESCE(environment_id, '00000000-0000-0000-0000-000000000000'::uuid)) STORED NOT NULL;
ALTER TABLE price_plan ADD CONSTRAINT price_plan_environment_fk FOREIGN KEY (organization_id, environment_id) REFERENCES organization_environment (organization_id, id);

ALTER TABLE invoice ADD COLUMN environment_id UUID;
ALTER TABLE invoice ADD COLUMN environment_scope_id UUID GENERATED ALWAYS AS (COALESCE(environment_id, '00000000-0000-0000-0000-000000000000'::uuid)) STORED NOT NULL;
ALTER TABLE invoice ADD CONSTRAINT invoice_environment_fk FOREIGN KEY (organization_id, environment_id) REFERENCES organization_environment (organization_id, id);

ALTER TABLE invoice_line ADD COLUMN environment_id UUID;
ALTER TABLE invoice_line ADD COLUMN environment_scope_id UUID GENERATED ALWAYS AS (COALESCE(environment_id, '00000000-0000-0000-0000-000000000000'::uuid)) STORED NOT NULL;
ALTER TABLE invoice_line ADD CONSTRAINT invoice_line_environment_fk FOREIGN KEY (organization_id, environment_id) REFERENCES organization_environment (organization_id, id);

ALTER TABLE toss_payments_credential ADD COLUMN environment_id UUID;
ALTER TABLE toss_payments_credential ADD COLUMN environment_scope_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000'::uuid;
ALTER TABLE toss_payments_credential ADD CONSTRAINT toss_payments_credential_environment_scope_check CHECK (environment_scope_id = COALESCE(environment_id, '00000000-0000-0000-0000-000000000000'::uuid));
ALTER TABLE toss_payments_credential ADD CONSTRAINT toss_payments_credential_environment_mode_fk FOREIGN KEY (organization_id, environment_id, mode) REFERENCES organization_environment (organization_id, id, mode);

ALTER TABLE toss_payments_credential_history ADD COLUMN environment_id UUID;
ALTER TABLE toss_payments_credential_history ADD COLUMN environment_scope_id UUID GENERATED ALWAYS AS (COALESCE(environment_id, '00000000-0000-0000-0000-000000000000'::uuid)) STORED NOT NULL;
ALTER TABLE toss_payments_credential_history ADD CONSTRAINT toss_payments_credential_history_environment_mode_fk FOREIGN KEY (organization_id, environment_id, mode) REFERENCES organization_environment (organization_id, id, mode);

ALTER TABLE billing_key ADD COLUMN environment_id UUID;
ALTER TABLE billing_key ADD COLUMN environment_scope_id UUID GENERATED ALWAYS AS (COALESCE(environment_id, '00000000-0000-0000-0000-000000000000'::uuid)) STORED NOT NULL;
ALTER TABLE billing_key ADD CONSTRAINT billing_key_environment_mode_fk FOREIGN KEY (organization_id, environment_id, mode) REFERENCES organization_environment (organization_id, id, mode);

ALTER TABLE billing_key_history ADD COLUMN environment_id UUID;
ALTER TABLE billing_key_history ADD COLUMN environment_scope_id UUID GENERATED ALWAYS AS (COALESCE(environment_id, '00000000-0000-0000-0000-000000000000'::uuid)) STORED NOT NULL;
ALTER TABLE billing_key_history ADD CONSTRAINT billing_key_history_environment_mode_fk FOREIGN KEY (organization_id, environment_id, mode) REFERENCES organization_environment (organization_id, id, mode);

ALTER TABLE payment_method_registration ADD COLUMN environment_id UUID;
ALTER TABLE payment_method_registration ADD COLUMN environment_scope_id UUID GENERATED ALWAYS AS (COALESCE(environment_id, '00000000-0000-0000-0000-000000000000'::uuid)) STORED NOT NULL;
ALTER TABLE payment_method_registration ADD CONSTRAINT payment_method_registration_environment_mode_fk FOREIGN KEY (organization_id, environment_id, mode) REFERENCES organization_environment (organization_id, id, mode);

ALTER TABLE payment_attempt ADD COLUMN environment_id UUID;
ALTER TABLE payment_attempt ADD COLUMN environment_scope_id UUID GENERATED ALWAYS AS (COALESCE(environment_id, '00000000-0000-0000-0000-000000000000'::uuid)) STORED NOT NULL;
ALTER TABLE payment_attempt ADD CONSTRAINT payment_attempt_environment_mode_fk FOREIGN KEY (organization_id, environment_id, mode) REFERENCES organization_environment (organization_id, id, mode);

ALTER TABLE payment_cancellation ADD COLUMN environment_id UUID;
ALTER TABLE payment_cancellation ADD COLUMN environment_scope_id UUID GENERATED ALWAYS AS (COALESCE(environment_id, '00000000-0000-0000-0000-000000000000'::uuid)) STORED NOT NULL;
ALTER TABLE payment_cancellation ADD CONSTRAINT payment_cancellation_environment_mode_fk FOREIGN KEY (organization_id, environment_id, mode) REFERENCES organization_environment (organization_id, id, mode);

ALTER TABLE payment_reconciliation_mismatch ADD COLUMN environment_id UUID;
ALTER TABLE payment_reconciliation_mismatch ADD COLUMN environment_scope_id UUID GENERATED ALWAYS AS (COALESCE(environment_id, '00000000-0000-0000-0000-000000000000'::uuid)) STORED NOT NULL;
ALTER TABLE payment_reconciliation_mismatch ADD CONSTRAINT payment_reconciliation_mismatch_environment_mode_fk FOREIGN KEY (organization_id, environment_id, mode) REFERENCES organization_environment (organization_id, id, mode);

ALTER TABLE invoice_view_link ADD COLUMN environment_id UUID;
ALTER TABLE invoice_view_link ADD COLUMN environment_scope_id UUID GENERATED ALWAYS AS (COALESCE(environment_id, '00000000-0000-0000-0000-000000000000'::uuid)) STORED NOT NULL;
ALTER TABLE invoice_view_link ADD CONSTRAINT invoice_view_link_environment_mode_fk FOREIGN KEY (organization_id, environment_id, mode) REFERENCES organization_environment (organization_id, id, mode);

ALTER TABLE outbound_email ADD COLUMN environment_id UUID;
ALTER TABLE outbound_email ADD COLUMN environment_scope_id UUID GENERATED ALWAYS AS (COALESCE(environment_id, '00000000-0000-0000-0000-000000000000'::uuid)) STORED NOT NULL;
ALTER TABLE outbound_email ADD CONSTRAINT outbound_email_environment_fk FOREIGN KEY (organization_id, environment_id) REFERENCES organization_environment (organization_id, id);

ALTER TABLE webhook_endpoint ADD COLUMN environment_id UUID;
ALTER TABLE webhook_endpoint ADD COLUMN environment_scope_id UUID GENERATED ALWAYS AS (COALESCE(environment_id, '00000000-0000-0000-0000-000000000000'::uuid)) STORED NOT NULL;
ALTER TABLE webhook_endpoint ADD CONSTRAINT webhook_endpoint_environment_fk FOREIGN KEY (organization_id, environment_id) REFERENCES organization_environment (organization_id, id);

ALTER TABLE webhook_event ADD COLUMN environment_id UUID;
ALTER TABLE webhook_event ADD COLUMN environment_scope_id UUID GENERATED ALWAYS AS (COALESCE(environment_id, '00000000-0000-0000-0000-000000000000'::uuid)) STORED NOT NULL;
ALTER TABLE webhook_event ADD CONSTRAINT webhook_event_environment_fk FOREIGN KEY (organization_id, environment_id) REFERENCES organization_environment (organization_id, id);

ALTER TABLE webhook_delivery ADD COLUMN environment_id UUID;
ALTER TABLE webhook_delivery ADD COLUMN environment_scope_id UUID GENERATED ALWAYS AS (COALESCE(environment_id, '00000000-0000-0000-0000-000000000000'::uuid)) STORED NOT NULL;
ALTER TABLE webhook_delivery ADD CONSTRAINT webhook_delivery_environment_fk FOREIGN KEY (organization_id, environment_id) REFERENCES organization_environment (organization_id, id);

ALTER TABLE webhook_delivery_attempt ADD COLUMN environment_id UUID;
ALTER TABLE webhook_delivery_attempt ADD COLUMN environment_scope_id UUID GENERATED ALWAYS AS (COALESCE(environment_id, '00000000-0000-0000-0000-000000000000'::uuid)) STORED NOT NULL;
ALTER TABLE webhook_delivery_attempt ADD CONSTRAINT webhook_delivery_attempt_environment_fk FOREIGN KEY (organization_id, environment_id) REFERENCES organization_environment (organization_id, id);

ALTER TABLE billable_metric_period DROP CONSTRAINT billable_metric_period_metric_fk;
ALTER TABLE price_rate DROP CONSTRAINT price_rate_billable_metric_same_organization_fk;
ALTER TABLE billable_metric DROP CONSTRAINT billable_metric_pk;
ALTER TABLE billable_metric ADD CONSTRAINT billable_metric_pk PRIMARY KEY (organization_id, environment_scope_id, code);
ALTER TABLE toss_payments_credential DROP CONSTRAINT toss_payments_credential_pk;
ALTER TABLE toss_payments_credential ADD CONSTRAINT toss_payments_credential_pk PRIMARY KEY (organization_id, environment_scope_id, mode);

ALTER TABLE billable_metric_period ADD CONSTRAINT billable_metric_period_metric_fk FOREIGN KEY (organization_id, environment_scope_id, billable_metric_code) REFERENCES billable_metric (organization_id, environment_scope_id, code);
ALTER TABLE price_rate ADD CONSTRAINT price_rate_billable_metric_same_organization_fk FOREIGN KEY (organization_id, environment_scope_id, billable_metric_code) REFERENCES billable_metric (organization_id, environment_scope_id, code);

ALTER TABLE invoice ADD CONSTRAINT invoice_environment_id_unique UNIQUE (organization_id, environment_scope_id, id);
ALTER TABLE payment_attempt ADD CONSTRAINT payment_attempt_environment_id_unique UNIQUE (organization_id, environment_scope_id, id);
ALTER TABLE webhook_endpoint ADD CONSTRAINT webhook_endpoint_environment_id_unique UNIQUE (organization_id, environment_scope_id, id);
ALTER TABLE webhook_event ADD CONSTRAINT webhook_event_environment_id_unique UNIQUE (organization_id, environment_scope_id, id);
ALTER TABLE webhook_delivery ADD CONSTRAINT webhook_delivery_environment_id_unique UNIQUE (organization_id, environment_scope_id, id);

ALTER TABLE price_rate ADD CONSTRAINT price_rate_customer_same_environment_fk FOREIGN KEY (organization_id, environment_scope_id, customer_id) REFERENCES customer (organization_id, environment_scope_id, id);
ALTER TABLE price_plan ADD CONSTRAINT price_plan_customer_same_environment_fk FOREIGN KEY (organization_id, environment_scope_id, customer_id) REFERENCES customer (organization_id, environment_scope_id, id);
ALTER TABLE invoice ADD CONSTRAINT invoice_customer_same_environment_fk FOREIGN KEY (organization_id, environment_scope_id, customer_id) REFERENCES customer (organization_id, environment_scope_id, id);
ALTER TABLE billing_key ADD CONSTRAINT billing_key_customer_same_environment_fk FOREIGN KEY (organization_id, environment_scope_id, customer_id) REFERENCES customer (organization_id, environment_scope_id, id);
ALTER TABLE billing_key_history ADD CONSTRAINT billing_key_history_customer_same_environment_fk FOREIGN KEY (organization_id, environment_scope_id, customer_id) REFERENCES customer (organization_id, environment_scope_id, id);
ALTER TABLE payment_method_registration ADD CONSTRAINT payment_method_registration_customer_same_environment_fk FOREIGN KEY (organization_id, environment_scope_id, customer_id) REFERENCES customer (organization_id, environment_scope_id, id);
ALTER TABLE payment_attempt ADD CONSTRAINT payment_attempt_customer_same_environment_fk FOREIGN KEY (organization_id, environment_scope_id, customer_id) REFERENCES customer (organization_id, environment_scope_id, id);
ALTER TABLE payment_attempt ADD CONSTRAINT payment_attempt_invoice_same_environment_fk FOREIGN KEY (organization_id, environment_scope_id, invoice_id) REFERENCES invoice (organization_id, environment_scope_id, id);
ALTER TABLE payment_cancellation ADD CONSTRAINT payment_cancellation_payment_attempt_same_environment_fk FOREIGN KEY (organization_id, environment_scope_id, payment_attempt_id) REFERENCES payment_attempt (organization_id, environment_scope_id, id);
ALTER TABLE invoice_line ADD CONSTRAINT invoice_line_invoice_same_environment_fk FOREIGN KEY (organization_id, environment_scope_id, invoice_id) REFERENCES invoice (organization_id, environment_scope_id, id);
ALTER TABLE invoice_view_link ADD CONSTRAINT invoice_view_link_invoice_same_environment_fk FOREIGN KEY (organization_id, environment_scope_id, invoice_id) REFERENCES invoice (organization_id, environment_scope_id, id);
ALTER TABLE outbound_email ADD CONSTRAINT outbound_email_invoice_same_environment_fk FOREIGN KEY (organization_id, environment_scope_id, invoice_id) REFERENCES invoice (organization_id, environment_scope_id, id);
ALTER TABLE webhook_delivery ADD CONSTRAINT webhook_delivery_webhook_endpoint_same_environment_fk FOREIGN KEY (organization_id, environment_scope_id, webhook_endpoint_id) REFERENCES webhook_endpoint (organization_id, environment_scope_id, id);
ALTER TABLE webhook_delivery ADD CONSTRAINT webhook_delivery_webhook_event_same_environment_fk FOREIGN KEY (organization_id, environment_scope_id, webhook_event_id) REFERENCES webhook_event (organization_id, environment_scope_id, id);
ALTER TABLE webhook_delivery_attempt ADD CONSTRAINT webhook_delivery_attempt_webhook_delivery_same_environment_fk FOREIGN KEY (organization_id, environment_scope_id, webhook_delivery_id) REFERENCES webhook_delivery (organization_id, environment_scope_id, id);

ALTER TABLE invoice DROP CONSTRAINT invoice_organization_customer_period_unique;
ALTER TABLE invoice ADD CONSTRAINT invoice_organization_customer_period_unique UNIQUE (organization_id, environment_scope_id, customer_id, period);
ALTER TABLE payment_reconciliation_mismatch DROP CONSTRAINT payment_reconciliation_mismatch_kind_order_transaction_unique;
ALTER TABLE payment_reconciliation_mismatch ADD CONSTRAINT payment_reconciliation_mismatch_kind_order_transaction_unique UNIQUE NULLS NOT DISTINCT (organization_id, environment_scope_id, mode, kind, order_id, transaction_key);

DROP INDEX price_rate_organization_version_unique;
CREATE UNIQUE INDEX price_rate_organization_version_unique ON price_rate USING btree (organization_id, environment_scope_id, billable_metric_code, dimension_values, effective_from) WHERE ((customer_id IS NULL) AND (archived_at IS NULL));
DROP INDEX price_rate_customer_version_unique;
CREATE UNIQUE INDEX price_rate_customer_version_unique ON price_rate USING btree (organization_id, environment_scope_id, billable_metric_code, dimension_values, customer_id, effective_from) WHERE ((customer_id IS NOT NULL) AND (archived_at IS NULL));
DROP INDEX price_plan_organization_version_unique;
CREATE UNIQUE INDEX price_plan_organization_version_unique ON price_plan USING btree (organization_id, environment_scope_id, effective_from) WHERE ((customer_id IS NULL) AND (archived_at IS NULL));
DROP INDEX price_plan_customer_version_unique;
CREATE UNIQUE INDEX price_plan_customer_version_unique ON price_plan USING btree (organization_id, environment_scope_id, customer_id, effective_from) WHERE ((customer_id IS NOT NULL) AND (archived_at IS NULL));
DROP INDEX payment_attempt_organization_invoice_mode_unique;
CREATE UNIQUE INDEX payment_attempt_organization_invoice_mode_unique ON payment_attempt USING btree (organization_id, environment_scope_id, invoice_id, mode) WHERE ((status)::text = ANY ((ARRAY['pending'::character varying, 'done'::character varying])::text[]));

ALTER TABLE payment_method_registration ADD COLUMN credential_updated_at TIMESTAMPTZ;
ALTER TABLE billing_key ADD COLUMN credential_updated_at TIMESTAMPTZ;
ALTER TABLE billing_key_history ADD COLUMN credential_updated_at TIMESTAMPTZ;
ALTER TABLE payment_attempt ADD COLUMN credential_updated_at TIMESTAMPTZ;
CREATE INDEX webhook_endpoint_environment_lookup ON webhook_endpoint (organization_id, environment_scope_id);
CREATE INDEX webhook_event_environment_lookup ON webhook_event (organization_id, environment_scope_id);
CREATE INDEX outbound_email_environment_lookup ON outbound_email (organization_id, environment_scope_id);
CREATE INDEX invoice_environment_lookup ON invoice (organization_id, environment_scope_id);
CREATE INDEX payment_attempt_environment_lookup ON payment_attempt (organization_id, environment_scope_id);
CREATE INDEX payment_reconciliation_mismatch_environment_lookup ON payment_reconciliation_mismatch (organization_id, environment_scope_id);

CREATE OR REPLACE FUNCTION payment_attempt_guard_update()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
BEGIN
  IF OLD.status <> 'pending' THEN
    RAISE EXCEPTION 'payment_attempt is final: % cannot change', OLD.status;
  END IF;
  IF NEW.status NOT IN ('done', 'failed')
     OR (NEW.id, NEW.organization_id, NEW.environment_id, NEW.credential_updated_at, NEW.customer_id, NEW.invoice_id, NEW.mode,
         NEW.amount, NEW.order_name, NEW.requested_at)
        IS DISTINCT FROM
        (OLD.id, OLD.organization_id, OLD.environment_id, OLD.credential_updated_at, OLD.customer_id, OLD.invoice_id, OLD.mode,
         OLD.amount, OLD.order_name, OLD.requested_at) THEN
    RAISE EXCEPTION 'payment_attempt can only move from pending to done or failed';
  END IF;
  RETURN NEW;
END;
$function$;

CREATE OR REPLACE FUNCTION payment_cancellation_guard_update()
 RETURNS trigger
 LANGUAGE plpgsql
AS $function$
BEGIN
  IF OLD.status <> 'pending' THEN
    RAISE EXCEPTION 'payment_cancellation is final: % cannot change', OLD.status;
  END IF;
  IF NEW.status NOT IN ('done', 'failed')
     OR (NEW.id, NEW.organization_id, NEW.environment_id, NEW.payment_attempt_id, NEW.mode,
         NEW.amount, NEW.reason, NEW.requested_at, NEW.idempotency_fingerprint)
        IS DISTINCT FROM
        (OLD.id, OLD.organization_id, OLD.environment_id, OLD.payment_attempt_id, OLD.mode,
         OLD.amount, OLD.reason, OLD.requested_at, OLD.idempotency_fingerprint) THEN
    RAISE EXCEPTION 'payment_cancellation can only move from pending to done or failed';
  END IF;
  RETURN NEW;
END;
$function$;
