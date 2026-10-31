GRANT INSERT,UPDATE ON shared.user_account,shared.user_session,shared.organization,
  shared.organization_environment,shared.organization_member,shared.organization_invitation,shared.api_key TO meterengine_shared;
GRANT INSERT ON shared.consent,shared.audit_log TO meterengine_shared;
GRANT INSERT,UPDATE,DELETE ON shared.auth_request_rate_limit TO meterengine_shared;
GRANT INSERT,UPDATE ON test.supplier,test.customer,test.billable_metric,test.price_rate,test.price_plan,
  test.toss_payments_credential,test.billing_key,test.payment_setting,test.payment_method_registration,
  test.invoice_view_link,test.payment_attempt,test.payment_cancellation,test.webhook_endpoint,
  test.webhook_delivery,test.idempotent_request TO meterengine_test;
GRANT INSERT,UPDATE ON live.supplier,live.customer,live.billable_metric,live.price_rate,live.price_plan,
  live.toss_payments_credential,live.billing_key,live.payment_setting,live.payment_method_registration,
  live.invoice_view_link,live.payment_attempt,live.payment_cancellation,live.webhook_endpoint,
  live.webhook_delivery,live.idempotent_request TO meterengine_live;
GRANT INSERT ON test.event,test.event_rejection,test.billable_metric_period,test.invoice,test.invoice_line,
  test.outbound_email,test.billing_key_history,test.toss_payments_credential_history,
  test.payment_reconciliation_mismatch,test.webhook_event,test.webhook_delivery_attempt TO meterengine_test;
GRANT INSERT ON live.event,live.event_rejection,live.billable_metric_period,live.invoice,live.invoice_line,
  live.outbound_email,live.billing_key_history,live.toss_payments_credential_history,
  live.payment_reconciliation_mismatch,live.webhook_event,live.webhook_delivery_attempt TO meterengine_live;
GRANT UPDATE(id) ON test.invoice TO meterengine_test;
GRANT UPDATE(id) ON live.invoice TO meterengine_live;
GRANT EXECUTE ON FUNCTION shared.lock_organization(uuid),shared.lock_organization_no_key_update(uuid),
  shared.append_business_audit(uuid,timestamptz,uuid,varchar,uuid,varchar,varchar,varchar,jsonb),
  shared.claim_job(uuid,varchar,timestamptz,timestamptz),
  shared.complete_job(uuid,varchar,integer,integer,integer,varchar,timestamptz) TO meterengine_test,meterengine_live;
