CREATE FUNCTION shared.audit_log_block_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  RAISE EXCEPTION 'audit_log is append-only: UPDATE/DELETE/TRUNCATE is blocked';
END;
$$;

CREATE FUNCTION shared.consent_block_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  RAISE EXCEPTION 'consent is append-only: UPDATE/DELETE/TRUNCATE is blocked';
END;
$$;

CREATE FUNCTION test.event_block_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  RAISE EXCEPTION 'event is append-only: UPDATE/DELETE/TRUNCATE is blocked';
END;
$$;

CREATE FUNCTION live.event_block_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  RAISE EXCEPTION 'event is append-only: UPDATE/DELETE/TRUNCATE is blocked';
END;
$$;

CREATE FUNCTION test.event_rejection_block_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  RAISE EXCEPTION 'event_rejection is append-only: UPDATE/DELETE/TRUNCATE is blocked';
END;
$$;

CREATE FUNCTION live.event_rejection_block_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  RAISE EXCEPTION 'event_rejection is append-only: UPDATE/DELETE/TRUNCATE is blocked';
END;
$$;

CREATE FUNCTION test.event_set_received_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  NEW.received_at := clock_timestamp();
  RETURN NEW;
END;
$$;

CREATE FUNCTION live.event_set_received_at() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  NEW.received_at := clock_timestamp();
  RETURN NEW;
END;
$$;

CREATE FUNCTION test.invoice_block_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  RAISE EXCEPTION 'invoice is append-only: UPDATE/DELETE/TRUNCATE is blocked';
END;
$$;

CREATE FUNCTION live.invoice_block_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  RAISE EXCEPTION 'invoice is append-only: UPDATE/DELETE/TRUNCATE is blocked';
END;
$$;

CREATE FUNCTION test.invoice_line_block_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  RAISE EXCEPTION 'invoice_line is append-only: UPDATE/DELETE/TRUNCATE is blocked';
END;
$$;

CREATE FUNCTION live.invoice_line_block_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  RAISE EXCEPTION 'invoice_line is append-only: UPDATE/DELETE/TRUNCATE is blocked';
END;
$$;

CREATE FUNCTION shared.organization_create_environments() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  INSERT INTO shared.organization_environment (organization_id, mode) VALUES (NEW.id, 'test'), (NEW.id, 'live');
  RETURN NEW;
END;
$$;

CREATE FUNCTION test.outbound_email_block_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  RAISE EXCEPTION 'outbound_email is append-only: UPDATE/DELETE/TRUNCATE is blocked';
END;
$$;

CREATE FUNCTION live.outbound_email_block_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  RAISE EXCEPTION 'outbound_email is append-only: UPDATE/DELETE/TRUNCATE is blocked';
END;
$$;

CREATE FUNCTION test.payment_attempt_block_delete() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  RAISE EXCEPTION 'payment_attempt is append-only: DELETE/TRUNCATE is blocked';
END;
$$;

CREATE FUNCTION live.payment_attempt_block_delete() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  RAISE EXCEPTION 'payment_attempt is append-only: DELETE/TRUNCATE is blocked';
END;
$$;

CREATE FUNCTION test.payment_attempt_guard_update() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  IF OLD.status <> 'pending' THEN
    RAISE EXCEPTION 'payment_attempt is final: % cannot change', OLD.status;
  END IF;
  IF NEW.status NOT IN ('done', 'failed')
     OR (NEW.id, NEW.organization_id, NEW.credential_updated_at, NEW.customer_id, NEW.invoice_id, NEW.mode,
         NEW.amount, NEW.order_name, NEW.requested_at)
        IS DISTINCT FROM
        (OLD.id, OLD.organization_id, OLD.credential_updated_at, OLD.customer_id, OLD.invoice_id, OLD.mode,
         OLD.amount, OLD.order_name, OLD.requested_at) THEN
    RAISE EXCEPTION 'payment_attempt can only move from pending to done or failed';
  END IF;
  RETURN NEW;
END;
$$;

CREATE FUNCTION live.payment_attempt_guard_update() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  IF OLD.status <> 'pending' THEN
    RAISE EXCEPTION 'payment_attempt is final: % cannot change', OLD.status;
  END IF;
  IF NEW.status NOT IN ('done', 'failed')
     OR (NEW.id, NEW.organization_id, NEW.credential_updated_at, NEW.customer_id, NEW.invoice_id, NEW.mode,
         NEW.amount, NEW.order_name, NEW.requested_at)
        IS DISTINCT FROM
        (OLD.id, OLD.organization_id, OLD.credential_updated_at, OLD.customer_id, OLD.invoice_id, OLD.mode,
         OLD.amount, OLD.order_name, OLD.requested_at) THEN
    RAISE EXCEPTION 'payment_attempt can only move from pending to done or failed';
  END IF;
  RETURN NEW;
END;
$$;

CREATE FUNCTION test.payment_cancellation_block_delete() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  RAISE EXCEPTION 'payment_cancellation is append-only: DELETE/TRUNCATE is blocked';
END;
$$;

CREATE FUNCTION live.payment_cancellation_block_delete() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  RAISE EXCEPTION 'payment_cancellation is append-only: DELETE/TRUNCATE is blocked';
END;
$$;

CREATE FUNCTION test.payment_cancellation_guard_update() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  IF OLD.status <> 'pending' THEN
    RAISE EXCEPTION 'payment_cancellation is final: % cannot change', OLD.status;
  END IF;
  IF NEW.status NOT IN ('done', 'failed')
     OR (NEW.id, NEW.organization_id, NEW.payment_attempt_id, NEW.mode,
         NEW.amount, NEW.reason, NEW.requested_at, NEW.idempotency_fingerprint)
        IS DISTINCT FROM
        (OLD.id, OLD.organization_id, OLD.payment_attempt_id, OLD.mode,
         OLD.amount, OLD.reason, OLD.requested_at, OLD.idempotency_fingerprint) THEN
    RAISE EXCEPTION 'payment_cancellation can only move from pending to done or failed';
  END IF;
  RETURN NEW;
END;
$$;

CREATE FUNCTION live.payment_cancellation_guard_update() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  IF OLD.status <> 'pending' THEN
    RAISE EXCEPTION 'payment_cancellation is final: % cannot change', OLD.status;
  END IF;
  IF NEW.status NOT IN ('done', 'failed')
     OR (NEW.id, NEW.organization_id, NEW.payment_attempt_id, NEW.mode,
         NEW.amount, NEW.reason, NEW.requested_at, NEW.idempotency_fingerprint)
        IS DISTINCT FROM
        (OLD.id, OLD.organization_id, OLD.payment_attempt_id, OLD.mode,
         OLD.amount, OLD.reason, OLD.requested_at, OLD.idempotency_fingerprint) THEN
    RAISE EXCEPTION 'payment_cancellation can only move from pending to done or failed';
  END IF;
  RETURN NEW;
END;
$$;

CREATE FUNCTION test.payment_reconciliation_mismatch_block_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  RAISE EXCEPTION 'payment_reconciliation_mismatch is append-only: UPDATE/DELETE/TRUNCATE is blocked';
END;
$$;

CREATE FUNCTION live.payment_reconciliation_mismatch_block_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  RAISE EXCEPTION 'payment_reconciliation_mismatch is append-only: UPDATE/DELETE/TRUNCATE is blocked';
END;
$$;

CREATE FUNCTION test.webhook_block_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  RAISE EXCEPTION 'webhook history is append-only';
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

CREATE FUNCTION live.webhook_block_mutation() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
  RAISE EXCEPTION 'webhook history is append-only';
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

CREATE TRIGGER audit_log_block_delete BEFORE DELETE ON shared.audit_log FOR EACH ROW EXECUTE FUNCTION shared.audit_log_block_mutation();

CREATE TRIGGER audit_log_block_truncate BEFORE TRUNCATE ON shared.audit_log FOR EACH STATEMENT EXECUTE FUNCTION shared.audit_log_block_mutation();

CREATE TRIGGER audit_log_block_update BEFORE UPDATE ON shared.audit_log FOR EACH ROW EXECUTE FUNCTION shared.audit_log_block_mutation();

CREATE TRIGGER consent_block_delete BEFORE DELETE ON shared.consent FOR EACH ROW EXECUTE FUNCTION shared.consent_block_mutation();

CREATE TRIGGER consent_block_truncate BEFORE TRUNCATE ON shared.consent FOR EACH STATEMENT EXECUTE FUNCTION shared.consent_block_mutation();

CREATE TRIGGER consent_block_update BEFORE UPDATE ON shared.consent FOR EACH ROW EXECUTE FUNCTION shared.consent_block_mutation();

CREATE TRIGGER event_block_delete BEFORE DELETE ON test.event FOR EACH ROW EXECUTE FUNCTION test.event_block_mutation();

CREATE TRIGGER event_block_delete BEFORE DELETE ON live.event FOR EACH ROW EXECUTE FUNCTION live.event_block_mutation();

CREATE TRIGGER event_block_truncate BEFORE TRUNCATE ON test.event FOR EACH STATEMENT EXECUTE FUNCTION test.event_block_mutation();

CREATE TRIGGER event_block_truncate BEFORE TRUNCATE ON live.event FOR EACH STATEMENT EXECUTE FUNCTION live.event_block_mutation();

CREATE TRIGGER event_block_update BEFORE UPDATE ON test.event FOR EACH ROW EXECUTE FUNCTION test.event_block_mutation();

CREATE TRIGGER event_block_update BEFORE UPDATE ON live.event FOR EACH ROW EXECUTE FUNCTION live.event_block_mutation();

CREATE TRIGGER event_rejection_block_delete BEFORE DELETE ON test.event_rejection FOR EACH ROW EXECUTE FUNCTION test.event_rejection_block_mutation();

CREATE TRIGGER event_rejection_block_delete BEFORE DELETE ON live.event_rejection FOR EACH ROW EXECUTE FUNCTION live.event_rejection_block_mutation();

CREATE TRIGGER event_rejection_block_truncate BEFORE TRUNCATE ON test.event_rejection FOR EACH STATEMENT EXECUTE FUNCTION test.event_rejection_block_mutation();

CREATE TRIGGER event_rejection_block_truncate BEFORE TRUNCATE ON live.event_rejection FOR EACH STATEMENT EXECUTE FUNCTION live.event_rejection_block_mutation();

CREATE TRIGGER event_rejection_block_update BEFORE UPDATE ON test.event_rejection FOR EACH ROW EXECUTE FUNCTION test.event_rejection_block_mutation();

CREATE TRIGGER event_rejection_block_update BEFORE UPDATE ON live.event_rejection FOR EACH ROW EXECUTE FUNCTION live.event_rejection_block_mutation();

CREATE TRIGGER event_set_received_at BEFORE INSERT ON test.event FOR EACH ROW EXECUTE FUNCTION test.event_set_received_at();

CREATE TRIGGER event_set_received_at BEFORE INSERT ON live.event FOR EACH ROW EXECUTE FUNCTION live.event_set_received_at();

CREATE TRIGGER invoice_block_delete BEFORE DELETE ON test.invoice FOR EACH ROW EXECUTE FUNCTION test.invoice_block_mutation();

CREATE TRIGGER invoice_block_delete BEFORE DELETE ON live.invoice FOR EACH ROW EXECUTE FUNCTION live.invoice_block_mutation();

CREATE TRIGGER invoice_block_truncate BEFORE TRUNCATE ON test.invoice FOR EACH STATEMENT EXECUTE FUNCTION test.invoice_block_mutation();

CREATE TRIGGER invoice_block_truncate BEFORE TRUNCATE ON live.invoice FOR EACH STATEMENT EXECUTE FUNCTION live.invoice_block_mutation();

CREATE TRIGGER invoice_block_update BEFORE UPDATE ON test.invoice FOR EACH ROW EXECUTE FUNCTION test.invoice_block_mutation();

CREATE TRIGGER invoice_block_update BEFORE UPDATE ON live.invoice FOR EACH ROW EXECUTE FUNCTION live.invoice_block_mutation();

CREATE TRIGGER invoice_line_block_delete BEFORE DELETE ON test.invoice_line FOR EACH ROW EXECUTE FUNCTION test.invoice_line_block_mutation();

CREATE TRIGGER invoice_line_block_delete BEFORE DELETE ON live.invoice_line FOR EACH ROW EXECUTE FUNCTION live.invoice_line_block_mutation();

CREATE TRIGGER invoice_line_block_truncate BEFORE TRUNCATE ON test.invoice_line FOR EACH STATEMENT EXECUTE FUNCTION test.invoice_line_block_mutation();

CREATE TRIGGER invoice_line_block_truncate BEFORE TRUNCATE ON live.invoice_line FOR EACH STATEMENT EXECUTE FUNCTION live.invoice_line_block_mutation();

CREATE TRIGGER invoice_line_block_update BEFORE UPDATE ON test.invoice_line FOR EACH ROW EXECUTE FUNCTION test.invoice_line_block_mutation();

CREATE TRIGGER invoice_line_block_update BEFORE UPDATE ON live.invoice_line FOR EACH ROW EXECUTE FUNCTION live.invoice_line_block_mutation();

CREATE TRIGGER organization_create_environments AFTER INSERT ON shared.organization FOR EACH ROW EXECUTE FUNCTION shared.organization_create_environments();

CREATE TRIGGER outbound_email_block_delete BEFORE DELETE ON test.outbound_email FOR EACH ROW EXECUTE FUNCTION test.outbound_email_block_mutation();

CREATE TRIGGER outbound_email_block_delete BEFORE DELETE ON live.outbound_email FOR EACH ROW EXECUTE FUNCTION live.outbound_email_block_mutation();

CREATE TRIGGER outbound_email_block_truncate BEFORE TRUNCATE ON test.outbound_email FOR EACH STATEMENT EXECUTE FUNCTION test.outbound_email_block_mutation();

CREATE TRIGGER outbound_email_block_truncate BEFORE TRUNCATE ON live.outbound_email FOR EACH STATEMENT EXECUTE FUNCTION live.outbound_email_block_mutation();

CREATE TRIGGER outbound_email_block_update BEFORE UPDATE ON test.outbound_email FOR EACH ROW EXECUTE FUNCTION test.outbound_email_block_mutation();

CREATE TRIGGER outbound_email_block_update BEFORE UPDATE ON live.outbound_email FOR EACH ROW EXECUTE FUNCTION live.outbound_email_block_mutation();

CREATE TRIGGER payment_attempt_block_delete BEFORE DELETE ON test.payment_attempt FOR EACH ROW EXECUTE FUNCTION test.payment_attempt_block_delete();

CREATE TRIGGER payment_attempt_block_delete BEFORE DELETE ON live.payment_attempt FOR EACH ROW EXECUTE FUNCTION live.payment_attempt_block_delete();

CREATE TRIGGER payment_attempt_block_truncate BEFORE TRUNCATE ON test.payment_attempt FOR EACH STATEMENT EXECUTE FUNCTION test.payment_attempt_block_delete();

CREATE TRIGGER payment_attempt_block_truncate BEFORE TRUNCATE ON live.payment_attempt FOR EACH STATEMENT EXECUTE FUNCTION live.payment_attempt_block_delete();

CREATE TRIGGER payment_attempt_guard_update BEFORE UPDATE ON test.payment_attempt FOR EACH ROW EXECUTE FUNCTION test.payment_attempt_guard_update();

CREATE TRIGGER payment_attempt_guard_update BEFORE UPDATE ON live.payment_attempt FOR EACH ROW EXECUTE FUNCTION live.payment_attempt_guard_update();

CREATE TRIGGER payment_cancellation_block_delete BEFORE DELETE ON test.payment_cancellation FOR EACH ROW EXECUTE FUNCTION test.payment_cancellation_block_delete();

CREATE TRIGGER payment_cancellation_block_delete BEFORE DELETE ON live.payment_cancellation FOR EACH ROW EXECUTE FUNCTION live.payment_cancellation_block_delete();

CREATE TRIGGER payment_cancellation_block_truncate BEFORE TRUNCATE ON test.payment_cancellation FOR EACH STATEMENT EXECUTE FUNCTION test.payment_cancellation_block_delete();

CREATE TRIGGER payment_cancellation_block_truncate BEFORE TRUNCATE ON live.payment_cancellation FOR EACH STATEMENT EXECUTE FUNCTION live.payment_cancellation_block_delete();

CREATE TRIGGER payment_cancellation_guard_update BEFORE UPDATE ON test.payment_cancellation FOR EACH ROW EXECUTE FUNCTION test.payment_cancellation_guard_update();

CREATE TRIGGER payment_cancellation_guard_update BEFORE UPDATE ON live.payment_cancellation FOR EACH ROW EXECUTE FUNCTION live.payment_cancellation_guard_update();

CREATE TRIGGER payment_reconciliation_mismatch_block_delete BEFORE DELETE ON test.payment_reconciliation_mismatch FOR EACH ROW EXECUTE FUNCTION test.payment_reconciliation_mismatch_block_mutation();

CREATE TRIGGER payment_reconciliation_mismatch_block_delete BEFORE DELETE ON live.payment_reconciliation_mismatch FOR EACH ROW EXECUTE FUNCTION live.payment_reconciliation_mismatch_block_mutation();

CREATE TRIGGER payment_reconciliation_mismatch_block_truncate BEFORE TRUNCATE ON test.payment_reconciliation_mismatch FOR EACH STATEMENT EXECUTE FUNCTION test.payment_reconciliation_mismatch_block_mutation();

CREATE TRIGGER payment_reconciliation_mismatch_block_truncate BEFORE TRUNCATE ON live.payment_reconciliation_mismatch FOR EACH STATEMENT EXECUTE FUNCTION live.payment_reconciliation_mismatch_block_mutation();

CREATE TRIGGER payment_reconciliation_mismatch_block_update BEFORE UPDATE ON test.payment_reconciliation_mismatch FOR EACH ROW EXECUTE FUNCTION test.payment_reconciliation_mismatch_block_mutation();

CREATE TRIGGER payment_reconciliation_mismatch_block_update BEFORE UPDATE ON live.payment_reconciliation_mismatch FOR EACH ROW EXECUTE FUNCTION live.payment_reconciliation_mismatch_block_mutation();

CREATE TRIGGER webhook_delivery_attempt_block_delete BEFORE DELETE ON test.webhook_delivery_attempt FOR EACH ROW EXECUTE FUNCTION test.webhook_block_mutation();

CREATE TRIGGER webhook_delivery_attempt_block_delete BEFORE DELETE ON live.webhook_delivery_attempt FOR EACH ROW EXECUTE FUNCTION live.webhook_block_mutation();

CREATE TRIGGER webhook_delivery_attempt_block_truncate BEFORE TRUNCATE ON test.webhook_delivery_attempt FOR EACH STATEMENT EXECUTE FUNCTION test.webhook_block_mutation();

CREATE TRIGGER webhook_delivery_attempt_block_truncate BEFORE TRUNCATE ON live.webhook_delivery_attempt FOR EACH STATEMENT EXECUTE FUNCTION live.webhook_block_mutation();

CREATE TRIGGER webhook_delivery_attempt_block_update BEFORE UPDATE ON test.webhook_delivery_attempt FOR EACH ROW EXECUTE FUNCTION test.webhook_block_mutation();

CREATE TRIGGER webhook_delivery_attempt_block_update BEFORE UPDATE ON live.webhook_delivery_attempt FOR EACH ROW EXECUTE FUNCTION live.webhook_block_mutation();

CREATE TRIGGER webhook_event_block_delete BEFORE DELETE ON test.webhook_event FOR EACH ROW EXECUTE FUNCTION test.webhook_block_mutation();

CREATE TRIGGER webhook_event_block_delete BEFORE DELETE ON live.webhook_event FOR EACH ROW EXECUTE FUNCTION live.webhook_block_mutation();

CREATE TRIGGER webhook_event_block_truncate BEFORE TRUNCATE ON test.webhook_event FOR EACH STATEMENT EXECUTE FUNCTION test.webhook_block_mutation();

CREATE TRIGGER webhook_event_block_truncate BEFORE TRUNCATE ON live.webhook_event FOR EACH STATEMENT EXECUTE FUNCTION live.webhook_block_mutation();

CREATE TRIGGER webhook_event_block_update BEFORE UPDATE ON test.webhook_event FOR EACH ROW EXECUTE FUNCTION test.webhook_block_mutation();

CREATE TRIGGER webhook_event_block_update BEFORE UPDATE ON live.webhook_event FOR EACH ROW EXECUTE FUNCTION live.webhook_block_mutation();

ALTER TABLE ONLY shared.api_key
    ADD CONSTRAINT api_key_environment_mode_fk FOREIGN KEY (organization_id, environment_id, mode) REFERENCES shared.organization_environment(organization_id, id, mode);

ALTER TABLE ONLY shared.api_key
    ADD CONSTRAINT api_key_organization_fk FOREIGN KEY (organization_id) REFERENCES shared.organization(id);

ALTER TABLE ONLY shared.audit_log
    ADD CONSTRAINT audit_log_environment_fk FOREIGN KEY (organization_id, environment_id) REFERENCES shared.organization_environment(organization_id, id);

ALTER TABLE ONLY test.billable_metric
    ADD CONSTRAINT billable_metric_organization_fk FOREIGN KEY (organization_id) REFERENCES shared.organization(id);

ALTER TABLE ONLY live.billable_metric
    ADD CONSTRAINT billable_metric_organization_fk FOREIGN KEY (organization_id) REFERENCES shared.organization(id);

ALTER TABLE ONLY test.billable_metric_period
    ADD CONSTRAINT billable_metric_period_metric_fk FOREIGN KEY (organization_id, billable_metric_code) REFERENCES test.billable_metric(organization_id, code);

ALTER TABLE ONLY live.billable_metric_period
    ADD CONSTRAINT billable_metric_period_metric_fk FOREIGN KEY (organization_id, billable_metric_code) REFERENCES live.billable_metric(organization_id, code);

ALTER TABLE ONLY test.billing_key
    ADD CONSTRAINT billing_key_customer_same_environment_fk FOREIGN KEY (organization_id, customer_id) REFERENCES test.customer(organization_id, id);

ALTER TABLE ONLY live.billing_key
    ADD CONSTRAINT billing_key_customer_same_environment_fk FOREIGN KEY (organization_id, customer_id) REFERENCES live.customer(organization_id, id);

ALTER TABLE ONLY test.billing_key
    ADD CONSTRAINT billing_key_customer_same_organization_fk FOREIGN KEY (organization_id, customer_id) REFERENCES test.customer(organization_id, id) ON DELETE CASCADE;

ALTER TABLE ONLY live.billing_key
    ADD CONSTRAINT billing_key_customer_same_organization_fk FOREIGN KEY (organization_id, customer_id) REFERENCES live.customer(organization_id, id) ON DELETE CASCADE;

ALTER TABLE ONLY test.billing_key_history
    ADD CONSTRAINT billing_key_history_customer_same_environment_fk FOREIGN KEY (organization_id, customer_id) REFERENCES test.customer(organization_id, id);

ALTER TABLE ONLY live.billing_key_history
    ADD CONSTRAINT billing_key_history_customer_same_environment_fk FOREIGN KEY (organization_id, customer_id) REFERENCES live.customer(organization_id, id);

ALTER TABLE ONLY shared.consent
    ADD CONSTRAINT consent_user_account_fk FOREIGN KEY (user_account_id) REFERENCES shared.user_account(id);

ALTER TABLE ONLY test.customer
    ADD CONSTRAINT customer_organization_fk FOREIGN KEY (organization_id) REFERENCES shared.organization(id);

ALTER TABLE ONLY live.customer
    ADD CONSTRAINT customer_organization_fk FOREIGN KEY (organization_id) REFERENCES shared.organization(id);

ALTER TABLE ONLY test.event
    ADD CONSTRAINT event_customer_same_environment_fk FOREIGN KEY (organization_id, customer_id) REFERENCES test.customer(organization_id, id);

ALTER TABLE ONLY live.event
    ADD CONSTRAINT event_customer_same_environment_fk FOREIGN KEY (organization_id, customer_id) REFERENCES live.customer(organization_id, id);

ALTER TABLE ONLY test.event
    ADD CONSTRAINT event_organization_fk FOREIGN KEY (organization_id) REFERENCES shared.organization(id);

ALTER TABLE ONLY live.event
    ADD CONSTRAINT event_organization_fk FOREIGN KEY (organization_id) REFERENCES shared.organization(id);

ALTER TABLE ONLY test.event_rejection
    ADD CONSTRAINT event_rejection_organization_fk FOREIGN KEY (organization_id) REFERENCES shared.organization(id);

ALTER TABLE ONLY live.event_rejection
    ADD CONSTRAINT event_rejection_organization_fk FOREIGN KEY (organization_id) REFERENCES shared.organization(id);

ALTER TABLE ONLY test.idempotent_request
    ADD CONSTRAINT idempotent_request_organization_fk FOREIGN KEY (organization_id) REFERENCES shared.organization(id);

ALTER TABLE ONLY live.idempotent_request
    ADD CONSTRAINT idempotent_request_organization_fk FOREIGN KEY (organization_id) REFERENCES shared.organization(id);

ALTER TABLE ONLY test.invoice
    ADD CONSTRAINT invoice_customer_same_environment_fk FOREIGN KEY (organization_id, customer_id) REFERENCES test.customer(organization_id, id);

ALTER TABLE ONLY live.invoice
    ADD CONSTRAINT invoice_customer_same_environment_fk FOREIGN KEY (organization_id, customer_id) REFERENCES live.customer(organization_id, id);

ALTER TABLE ONLY test.invoice_line
    ADD CONSTRAINT invoice_line_invoice_same_environment_fk FOREIGN KEY (organization_id, invoice_id) REFERENCES test.invoice(organization_id, id);

ALTER TABLE ONLY live.invoice_line
    ADD CONSTRAINT invoice_line_invoice_same_environment_fk FOREIGN KEY (organization_id, invoice_id) REFERENCES live.invoice(organization_id, id);

ALTER TABLE ONLY test.invoice_line
    ADD CONSTRAINT invoice_line_organization_fk FOREIGN KEY (organization_id) REFERENCES shared.organization(id);

ALTER TABLE ONLY live.invoice_line
    ADD CONSTRAINT invoice_line_organization_fk FOREIGN KEY (organization_id) REFERENCES shared.organization(id);

ALTER TABLE ONLY test.invoice
    ADD CONSTRAINT invoice_organization_fk FOREIGN KEY (organization_id) REFERENCES shared.organization(id);

ALTER TABLE ONLY live.invoice
    ADD CONSTRAINT invoice_organization_fk FOREIGN KEY (organization_id) REFERENCES shared.organization(id);

ALTER TABLE ONLY test.invoice_view_link
    ADD CONSTRAINT invoice_view_link_invoice_same_environment_fk FOREIGN KEY (organization_id, invoice_id) REFERENCES test.invoice(organization_id, id);

ALTER TABLE ONLY live.invoice_view_link
    ADD CONSTRAINT invoice_view_link_invoice_same_environment_fk FOREIGN KEY (organization_id, invoice_id) REFERENCES live.invoice(organization_id, id);

ALTER TABLE ONLY shared.organization_environment
    ADD CONSTRAINT organization_environment_organization_fk FOREIGN KEY (organization_id) REFERENCES shared.organization(id);

ALTER TABLE ONLY shared.organization_invitation
    ADD CONSTRAINT organization_invitation_organization_fk FOREIGN KEY (organization_id) REFERENCES shared.organization(id);

ALTER TABLE ONLY shared.organization_member
    ADD CONSTRAINT organization_member_organization_fk FOREIGN KEY (organization_id) REFERENCES shared.organization(id);

ALTER TABLE ONLY shared.organization_member
    ADD CONSTRAINT organization_member_user_account_fk FOREIGN KEY (user_account_id) REFERENCES shared.user_account(id);

ALTER TABLE ONLY test.outbound_email
    ADD CONSTRAINT outbound_email_invoice_same_environment_fk FOREIGN KEY (organization_id, invoice_id) REFERENCES test.invoice(organization_id, id);

ALTER TABLE ONLY live.outbound_email
    ADD CONSTRAINT outbound_email_invoice_same_environment_fk FOREIGN KEY (organization_id, invoice_id) REFERENCES live.invoice(organization_id, id);

ALTER TABLE ONLY test.outbound_email
    ADD CONSTRAINT outbound_email_organization_fk FOREIGN KEY (organization_id) REFERENCES shared.organization(id);

ALTER TABLE ONLY live.outbound_email
    ADD CONSTRAINT outbound_email_organization_fk FOREIGN KEY (organization_id) REFERENCES shared.organization(id);

ALTER TABLE ONLY test.payment_attempt
    ADD CONSTRAINT payment_attempt_customer_same_environment_fk FOREIGN KEY (organization_id, customer_id) REFERENCES test.customer(organization_id, id);

ALTER TABLE ONLY live.payment_attempt
    ADD CONSTRAINT payment_attempt_customer_same_environment_fk FOREIGN KEY (organization_id, customer_id) REFERENCES live.customer(organization_id, id);

ALTER TABLE ONLY test.payment_attempt
    ADD CONSTRAINT payment_attempt_invoice_same_environment_fk FOREIGN KEY (organization_id, invoice_id) REFERENCES test.invoice(organization_id, id);

ALTER TABLE ONLY live.payment_attempt
    ADD CONSTRAINT payment_attempt_invoice_same_environment_fk FOREIGN KEY (organization_id, invoice_id) REFERENCES live.invoice(organization_id, id);

ALTER TABLE ONLY test.payment_cancellation
    ADD CONSTRAINT payment_cancellation_payment_attempt_same_environment_fk FOREIGN KEY (organization_id, payment_attempt_id) REFERENCES test.payment_attempt(organization_id, id);

ALTER TABLE ONLY live.payment_cancellation
    ADD CONSTRAINT payment_cancellation_payment_attempt_same_environment_fk FOREIGN KEY (organization_id, payment_attempt_id) REFERENCES live.payment_attempt(organization_id, id);

ALTER TABLE ONLY test.payment_method_registration
    ADD CONSTRAINT payment_method_registration_customer_same_environment_fk FOREIGN KEY (organization_id, customer_id) REFERENCES test.customer(organization_id, id);

ALTER TABLE ONLY live.payment_method_registration
    ADD CONSTRAINT payment_method_registration_customer_same_environment_fk FOREIGN KEY (organization_id, customer_id) REFERENCES live.customer(organization_id, id);

ALTER TABLE ONLY test.payment_method_registration
    ADD CONSTRAINT payment_method_registration_customer_same_organization_fk FOREIGN KEY (organization_id, customer_id) REFERENCES test.customer(organization_id, id) ON DELETE CASCADE;

ALTER TABLE ONLY live.payment_method_registration
    ADD CONSTRAINT payment_method_registration_customer_same_organization_fk FOREIGN KEY (organization_id, customer_id) REFERENCES live.customer(organization_id, id) ON DELETE CASCADE;

ALTER TABLE ONLY test.payment_reconciliation_mismatch
    ADD CONSTRAINT payment_reconciliation_mismatch_organization_fk FOREIGN KEY (organization_id) REFERENCES shared.organization(id);

ALTER TABLE ONLY live.payment_reconciliation_mismatch
    ADD CONSTRAINT payment_reconciliation_mismatch_organization_fk FOREIGN KEY (organization_id) REFERENCES shared.organization(id);

ALTER TABLE ONLY test.payment_setting
    ADD CONSTRAINT payment_setting_organization_fk FOREIGN KEY (organization_id) REFERENCES shared.organization(id);

ALTER TABLE ONLY live.payment_setting
    ADD CONSTRAINT payment_setting_organization_fk FOREIGN KEY (organization_id) REFERENCES shared.organization(id);

ALTER TABLE ONLY test.price_plan
    ADD CONSTRAINT price_plan_customer_same_environment_fk FOREIGN KEY (organization_id, customer_id) REFERENCES test.customer(organization_id, id);

ALTER TABLE ONLY live.price_plan
    ADD CONSTRAINT price_plan_customer_same_environment_fk FOREIGN KEY (organization_id, customer_id) REFERENCES live.customer(organization_id, id);

ALTER TABLE ONLY test.price_plan
    ADD CONSTRAINT price_plan_organization_fk FOREIGN KEY (organization_id) REFERENCES shared.organization(id);

ALTER TABLE ONLY live.price_plan
    ADD CONSTRAINT price_plan_organization_fk FOREIGN KEY (organization_id) REFERENCES shared.organization(id);

ALTER TABLE ONLY test.price_rate
    ADD CONSTRAINT price_rate_billable_metric_same_organization_fk FOREIGN KEY (organization_id, billable_metric_code) REFERENCES test.billable_metric(organization_id, code);

ALTER TABLE ONLY live.price_rate
    ADD CONSTRAINT price_rate_billable_metric_same_organization_fk FOREIGN KEY (organization_id, billable_metric_code) REFERENCES live.billable_metric(organization_id, code);

ALTER TABLE ONLY test.price_rate
    ADD CONSTRAINT price_rate_customer_same_environment_fk FOREIGN KEY (organization_id, customer_id) REFERENCES test.customer(organization_id, id);

ALTER TABLE ONLY live.price_rate
    ADD CONSTRAINT price_rate_customer_same_environment_fk FOREIGN KEY (organization_id, customer_id) REFERENCES live.customer(organization_id, id);

ALTER TABLE ONLY test.price_rate
    ADD CONSTRAINT price_rate_organization_fk FOREIGN KEY (organization_id) REFERENCES shared.organization(id);

ALTER TABLE ONLY live.price_rate
    ADD CONSTRAINT price_rate_organization_fk FOREIGN KEY (organization_id) REFERENCES shared.organization(id);

ALTER TABLE ONLY test.toss_payments_credential
    ADD CONSTRAINT toss_payments_credential_organization_fk FOREIGN KEY (organization_id) REFERENCES shared.organization(id);

ALTER TABLE ONLY live.toss_payments_credential
    ADD CONSTRAINT toss_payments_credential_organization_fk FOREIGN KEY (organization_id) REFERENCES shared.organization(id);

ALTER TABLE ONLY shared.user_session
    ADD CONSTRAINT user_session_organization_member_fk FOREIGN KEY (organization_id, user_account_id) REFERENCES shared.organization_member(organization_id, user_account_id);

ALTER TABLE ONLY shared.user_session
    ADD CONSTRAINT user_session_user_account_fk FOREIGN KEY (user_account_id) REFERENCES shared.user_account(id);

ALTER TABLE ONLY test.webhook_delivery_attempt
    ADD CONSTRAINT webhook_delivery_attempt_organization_fk FOREIGN KEY (organization_id) REFERENCES shared.organization(id);

ALTER TABLE ONLY live.webhook_delivery_attempt
    ADD CONSTRAINT webhook_delivery_attempt_organization_fk FOREIGN KEY (organization_id) REFERENCES shared.organization(id);

ALTER TABLE ONLY test.webhook_delivery_attempt
    ADD CONSTRAINT webhook_delivery_attempt_webhook_delivery_same_environment_fk FOREIGN KEY (organization_id, webhook_delivery_id) REFERENCES test.webhook_delivery(organization_id, id);

ALTER TABLE ONLY live.webhook_delivery_attempt
    ADD CONSTRAINT webhook_delivery_attempt_webhook_delivery_same_environment_fk FOREIGN KEY (organization_id, webhook_delivery_id) REFERENCES live.webhook_delivery(organization_id, id);

ALTER TABLE ONLY test.webhook_delivery
    ADD CONSTRAINT webhook_delivery_organization_fk FOREIGN KEY (organization_id) REFERENCES shared.organization(id);

ALTER TABLE ONLY live.webhook_delivery
    ADD CONSTRAINT webhook_delivery_organization_fk FOREIGN KEY (organization_id) REFERENCES shared.organization(id);

ALTER TABLE ONLY test.webhook_delivery
    ADD CONSTRAINT webhook_delivery_webhook_endpoint_same_environment_fk FOREIGN KEY (organization_id, webhook_endpoint_id) REFERENCES test.webhook_endpoint(organization_id, id);

ALTER TABLE ONLY live.webhook_delivery
    ADD CONSTRAINT webhook_delivery_webhook_endpoint_same_environment_fk FOREIGN KEY (organization_id, webhook_endpoint_id) REFERENCES live.webhook_endpoint(organization_id, id);

ALTER TABLE ONLY test.webhook_delivery
    ADD CONSTRAINT webhook_delivery_webhook_event_same_environment_fk FOREIGN KEY (organization_id, webhook_event_id) REFERENCES test.webhook_event(organization_id, id);

ALTER TABLE ONLY live.webhook_delivery
    ADD CONSTRAINT webhook_delivery_webhook_event_same_environment_fk FOREIGN KEY (organization_id, webhook_event_id) REFERENCES live.webhook_event(organization_id, id);

ALTER TABLE ONLY test.webhook_endpoint
    ADD CONSTRAINT webhook_endpoint_organization_fk FOREIGN KEY (organization_id) REFERENCES shared.organization(id);

ALTER TABLE ONLY live.webhook_endpoint
    ADD CONSTRAINT webhook_endpoint_organization_fk FOREIGN KEY (organization_id) REFERENCES shared.organization(id);

ALTER TABLE ONLY test.webhook_event
    ADD CONSTRAINT webhook_event_organization_fk FOREIGN KEY (organization_id) REFERENCES shared.organization(id);


--
-- PostgreSQL database dump complete
--

ALTER TABLE ONLY live.webhook_event
    ADD CONSTRAINT webhook_event_organization_fk FOREIGN KEY (organization_id) REFERENCES shared.organization(id);


--
-- PostgreSQL database dump complete
--

ALTER TABLE test.supplier ADD CONSTRAINT supplier_organization_fk FOREIGN KEY (organization_id) REFERENCES shared.organization(id);

INSERT INTO test.event_rejection_recording_start (singleton) VALUES (true);

ALTER TABLE live.supplier ADD CONSTRAINT supplier_organization_fk FOREIGN KEY (organization_id) REFERENCES shared.organization(id);

INSERT INTO live.event_rejection_recording_start (singleton) VALUES (true);

CREATE FUNCTION shared.runtime_scope() RETURNS varchar
LANGUAGE plpgsql STABLE SECURITY INVOKER AS $$
BEGIN
  CASE session_user
    WHEN 'meterengine_shared' THEN RETURN 'shared';
    WHEN 'meterengine_test' THEN RETURN 'test';
    WHEN 'meterengine_live' THEN RETURN 'live';
    ELSE RAISE EXCEPTION 'unsupported runtime login' USING ERRCODE = '42501';
  END CASE;
END;
$$;

CREATE VIEW shared.organization_reference AS SELECT id, name, status FROM shared.organization;

CREATE FUNCTION shared.lock_organization(p_organization_id uuid) RETURNS TABLE(id uuid)
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
DECLARE v_scope varchar := shared.runtime_scope();
BEGIN
  IF v_scope NOT IN ('test','live') THEN RAISE EXCEPTION 'business login required' USING ERRCODE = '42501'; END IF;
  RETURN QUERY SELECT o.id FROM shared.organization o WHERE o.id = p_organization_id
    AND EXISTS (SELECT 1 FROM shared.organization_environment e WHERE e.organization_id = o.id AND e.mode = v_scope)
    FOR UPDATE OF o;
END;
$$;

CREATE FUNCTION shared.lock_organization_no_key_update(p_organization_id uuid) RETURNS TABLE(id uuid)
LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
DECLARE v_scope varchar := shared.runtime_scope();
BEGIN
  IF v_scope NOT IN ('test','live') THEN RAISE EXCEPTION 'business login required' USING ERRCODE = '42501'; END IF;
  RETURN QUERY SELECT o.id FROM shared.organization o WHERE o.id = p_organization_id
    AND EXISTS (SELECT 1 FROM shared.organization_environment e WHERE e.organization_id = o.id AND e.mode = v_scope)
    FOR NO KEY UPDATE OF o;
END;
$$;

CREATE FUNCTION shared.append_business_audit(p_id uuid, p_occurred_at timestamptz, p_organization_id uuid,
    p_actor_type varchar, p_actor_id uuid, p_action varchar, p_target_type varchar, p_target_id varchar, p_detail jsonb)
RETURNS uuid LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
DECLARE v_scope varchar := shared.runtime_scope(); v_environment_id uuid;
BEGIN
  IF v_scope NOT IN ('test','live') THEN RAISE EXCEPTION 'business login required' USING ERRCODE = '42501'; END IF;
  SELECT e.id INTO STRICT v_environment_id FROM shared.organization_environment e
    WHERE e.organization_id = p_organization_id AND e.mode = v_scope;
  INSERT INTO shared.audit_log(id,occurred_at,organization_id,environment_id,actor_type,actor_id,action,target_type,target_id,detail)
    VALUES(p_id,p_occurred_at,p_organization_id,v_environment_id,p_actor_type,p_actor_id,p_action,p_target_type,p_target_id,p_detail);
  RETURN p_id;
END;
$$;

CREATE VIEW shared.audit_for_scope WITH (security_barrier=true) AS
SELECT a.* FROM shared.audit_log a WHERE a.environment_id IS NULL OR EXISTS (
  SELECT 1 FROM shared.organization_environment e WHERE e.id = a.environment_id AND e.mode = shared.runtime_scope()
);

CREATE FUNCTION shared.claim_job(p_id uuid, p_job_name varchar, p_scheduled_at timestamptz, p_started_at timestamptz)
RETURNS boolean LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
DECLARE v_scope varchar := shared.runtime_scope();
BEGIN
  IF v_scope NOT IN ('test','live') THEN RAISE EXCEPTION 'business login required' USING ERRCODE = '42501'; END IF;
  INSERT INTO shared.scheduled_job_run(id,scope,job_name,scheduled_at,started_at,status)
    VALUES(p_id,v_scope,p_job_name,p_scheduled_at,p_started_at,'running')
    ON CONFLICT (job_name,scope,scheduled_at) DO NOTHING;
  RETURN FOUND;
END;
$$;

CREATE FUNCTION shared.complete_job(p_id uuid,p_status varchar,p_processed_count integer,p_skipped_count integer,
    p_failed_count integer,p_error_summary varchar,p_finished_at timestamptz)
RETURNS boolean LANGUAGE plpgsql VOLATILE SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
DECLARE v_scope varchar := shared.runtime_scope();
BEGIN
  IF v_scope NOT IN ('test','live') THEN RAISE EXCEPTION 'business login required' USING ERRCODE = '42501'; END IF;
  IF p_status NOT IN ('succeeded','failed') OR p_status IS NULL THEN
    RAISE EXCEPTION 'invalid completion status' USING ERRCODE = '23514';
  END IF;
  UPDATE shared.scheduled_job_run SET status=p_status,processed_count=p_processed_count,skipped_count=p_skipped_count,
    failed_count=p_failed_count,error_summary=p_error_summary,finished_at=p_finished_at
    WHERE id=p_id AND scope=v_scope AND status='running';
  RETURN FOUND;
END;
$$;

CREATE FUNCTION shared.job_last_success(p_job_name varchar) RETURNS timestamptz
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
  SELECT max(finished_at) FROM shared.scheduled_job_run WHERE job_name=p_job_name AND scope=shared.runtime_scope() AND status='succeeded'
$$;
CREATE FUNCTION shared.job_last_completion(p_job_name varchar) RETURNS timestamptz
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
  SELECT max(finished_at) FROM shared.scheduled_job_run WHERE job_name=p_job_name AND scope=shared.runtime_scope()
$$;
CREATE FUNCTION shared.job_watermark(p_job_name varchar,p_before timestamptz) RETURNS timestamptz
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
  SELECT max(scheduled_at) FROM shared.scheduled_job_run WHERE job_name=p_job_name AND scope=shared.runtime_scope()
    AND status='succeeded' AND failed_count=0 AND scheduled_at<p_before
$$;
CREATE FUNCTION shared.job_earliest(p_job_name varchar,p_before timestamptz) RETURNS timestamptz
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = pg_catalog, pg_temp AS $$
  SELECT min(scheduled_at) FROM shared.scheduled_job_run WHERE job_name=p_job_name AND scope=shared.runtime_scope() AND scheduled_at<p_before
$$;

CREATE VIEW shared.invoice_view_link_route AS
SELECT token_hash,'test'::varchar AS scope,organization_id,id AS link_id FROM test.invoice_view_link
UNION ALL SELECT token_hash,'live'::varchar,organization_id,id FROM live.invoice_view_link;
CREATE VIEW shared.payment_registration_route AS
SELECT token_hash,'test'::varchar AS scope,organization_id,id AS registration_id FROM test.payment_method_registration
UNION ALL SELECT token_hash,'live'::varchar,organization_id,id FROM live.payment_method_registration;
CREATE VIEW shared.payment_order_route AS
SELECT organization_id,id AS order_id,'test'::varchar AS scope FROM test.payment_attempt
UNION ALL SELECT organization_id,id,'live'::varchar FROM live.payment_attempt;

CREATE VIEW shared.operations_business_counts AS
SELECT 'test'::varchar AS scope,
  (SELECT count(*) FROM test.payment_attempt WHERE status='pending') AS pending_payment_count,
  (SELECT count(*) FROM test.payment_attempt WHERE status='failed' AND requested_at>=now()-interval '24 hours') AS failed_payment_24h_count,
  (SELECT count(*) FROM test.webhook_delivery d WHERE status='failed' AND EXISTS
    (SELECT 1 FROM test.webhook_endpoint e WHERE e.organization_id=d.organization_id AND e.id=d.webhook_endpoint_id AND e.deleted_at IS NULL)) AS failed_webhook_count
UNION ALL SELECT 'live'::varchar,
  (SELECT count(*) FROM live.payment_attempt WHERE status='pending'),
  (SELECT count(*) FROM live.payment_attempt WHERE status='failed' AND requested_at>=now()-interval '24 hours'),
  (SELECT count(*) FROM live.webhook_delivery d WHERE status='failed' AND EXISTS
    (SELECT 1 FROM live.webhook_endpoint e WHERE e.organization_id=d.organization_id AND e.id=d.webhook_endpoint_id AND e.deleted_at IS NULL));

CREATE VIEW shared.operations_job_status AS
SELECT scopes.scope,jobs.job_name,
  (SELECT max(r.finished_at) FROM shared.scheduled_job_run r WHERE r.scope=scopes.scope AND r.job_name=jobs.job_name AND r.status='succeeded') AS last_success_at,
  latest.started_at AS last_started_at, latest.status AS latest_status
FROM (VALUES ('test'::varchar),('live'::varchar)) scopes(scope)
CROSS JOIN (VALUES ('payment_attempt_confirmation'::varchar),('payment_retry'::varchar),('payment_reconciliation'::varchar),('webhook_delivery'::varchar),('unbilled_usage_report'::varchar)) jobs(job_name)
LEFT JOIN LATERAL (SELECT r.started_at,r.status FROM shared.scheduled_job_run r WHERE r.scope=scopes.scope AND r.job_name=jobs.job_name ORDER BY r.started_at DESC,r.id DESC LIMIT 1) latest ON true;

REVOKE ALL ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON SCHEMA shared,test,live FROM PUBLIC;
REVOKE ALL ON ALL TABLES IN SCHEMA shared,test,live FROM PUBLIC;
REVOKE ALL ON ALL SEQUENCES IN SCHEMA shared,test,live FROM PUBLIC;
REVOKE ALL ON ALL FUNCTIONS IN SCHEMA shared,test,live FROM PUBLIC;
ALTER DEFAULT PRIVILEGES REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;
DO $$ BEGIN EXECUTE format('REVOKE ALL ON DATABASE %I FROM PUBLIC',current_database()); END $$;
GRANT CONNECT ON DATABASE meterengine_app TO meterengine_shared,meterengine_test,meterengine_live;
GRANT USAGE ON SCHEMA shared TO meterengine_shared,meterengine_test,meterengine_live;
GRANT USAGE ON SCHEMA test TO meterengine_test;
GRANT USAGE ON SCHEMA live TO meterengine_live;
GRANT SELECT ON shared.user_account,shared.user_session,shared.organization,shared.organization_environment,
  shared.organization_member,shared.organization_invitation,shared.api_key,shared.consent,shared.audit_log,
  shared.auth_request_rate_limit,shared.invoice_view_link_route,shared.payment_registration_route,
  shared.payment_order_route,shared.operations_business_counts,shared.operations_job_status TO meterengine_shared;
GRANT SELECT ON ALL TABLES IN SCHEMA test TO meterengine_test;
GRANT SELECT ON ALL TABLES IN SCHEMA live TO meterengine_live;
GRANT SELECT ON shared.organization_reference,shared.audit_for_scope TO meterengine_test,meterengine_live;
GRANT EXECUTE ON FUNCTION shared.runtime_scope() TO meterengine_shared,meterengine_test,meterengine_live;
GRANT EXECUTE ON FUNCTION test.valid_price_tiers(jsonb) TO meterengine_test;
GRANT EXECUTE ON FUNCTION live.valid_price_tiers(jsonb) TO meterengine_live;
GRANT EXECUTE ON FUNCTION shared.job_last_success(varchar),shared.job_last_completion(varchar),shared.job_watermark(varchar,timestamptz),
  shared.job_earliest(varchar,timestamptz) TO meterengine_test,meterengine_live;
