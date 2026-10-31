CREATE SCHEMA test;

CREATE SCHEMA live;

CREATE FUNCTION test.valid_price_tiers(tiers jsonb) RETURNS boolean
    LANGUAGE plpgsql IMMUTABLE
    AS $$
DECLARE item JSONB; previous_limit NUMERIC := 0; position INTEGER := 0; limit_value NUMERIC;
BEGIN
  IF tiers IS NULL OR jsonb_typeof(tiers) <> 'array' THEN RETURN FALSE; END IF;
  IF jsonb_array_length(tiers) < 1 OR jsonb_array_length(tiers) > 100 THEN RETURN FALSE; END IF;
  FOR item IN SELECT value FROM jsonb_array_elements(tiers) LOOP
    position := position + 1;
    IF jsonb_typeof(item) <> 'object' OR NOT (item ? 'unit_price') OR item->'unit_price' = 'null'::jsonb OR jsonb_typeof(item->'unit_price') <> 'number'
      OR (item->>'unit_price')::numeric < 0 THEN RETURN FALSE; END IF;
    IF item->'up_to' IS NULL OR item->'up_to' = 'null'::jsonb THEN
      IF position <> jsonb_array_length(tiers) THEN RETURN FALSE; END IF;
    ELSE
      IF jsonb_typeof(item->'up_to') <> 'number' THEN RETURN FALSE; END IF;
      limit_value := (item->>'up_to')::numeric;
      IF limit_value <= previous_limit OR position = jsonb_array_length(tiers) THEN RETURN FALSE; END IF;
      previous_limit := limit_value;
    END IF;
  END LOOP;
  RETURN TRUE;
EXCEPTION WHEN OTHERS THEN RETURN FALSE;
END $$;

CREATE FUNCTION live.valid_price_tiers(tiers jsonb) RETURNS boolean
    LANGUAGE plpgsql IMMUTABLE
    AS $$
DECLARE item JSONB; previous_limit NUMERIC := 0; position INTEGER := 0; limit_value NUMERIC;
BEGIN
  IF tiers IS NULL OR jsonb_typeof(tiers) <> 'array' THEN RETURN FALSE; END IF;
  IF jsonb_array_length(tiers) < 1 OR jsonb_array_length(tiers) > 100 THEN RETURN FALSE; END IF;
  FOR item IN SELECT value FROM jsonb_array_elements(tiers) LOOP
    position := position + 1;
    IF jsonb_typeof(item) <> 'object' OR NOT (item ? 'unit_price') OR item->'unit_price' = 'null'::jsonb OR jsonb_typeof(item->'unit_price') <> 'number'
      OR (item->>'unit_price')::numeric < 0 THEN RETURN FALSE; END IF;
    IF item->'up_to' IS NULL OR item->'up_to' = 'null'::jsonb THEN
      IF position <> jsonb_array_length(tiers) THEN RETURN FALSE; END IF;
    ELSE
      IF jsonb_typeof(item->'up_to') <> 'number' THEN RETURN FALSE; END IF;
      limit_value := (item->>'up_to')::numeric;
      IF limit_value <= previous_limit OR position = jsonb_array_length(tiers) THEN RETURN FALSE; END IF;
      previous_limit := limit_value;
    END IF;
  END LOOP;
  RETURN TRUE;
EXCEPTION WHEN OTHERS THEN RETURN FALSE;
END $$;

CREATE TABLE test.billable_metric (
    organization_id uuid NOT NULL,
    code character varying NOT NULL,
    name character varying NOT NULL COLLATE shared.korean,
    event_type character varying NOT NULL,
    aggregation character varying NOT NULL,
    target_property character varying,
    dimension_properties text[] DEFAULT '{}'::text[] NOT NULL,
    unit_size numeric DEFAULT 1 NOT NULL,
    rounding_mode character varying DEFAULT 'none'::character varying NOT NULL,
    rounding_precision integer DEFAULT 0 NOT NULL,
    percentile numeric,
    archived_at timestamp with time zone,
    active_from character varying DEFAULT '0001-01'::character varying NOT NULL,
    inactive_from character varying,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT billable_metric_percentile_check CHECK (((percentile >= (0)::numeric) AND (percentile <= (100)::numeric))),
    CONSTRAINT billable_metric_aggregation_percentile_check CHECK ((((aggregation)::text = 'percentile'::text) = (percentile IS NOT NULL))),
    CONSTRAINT billable_metric_rounding_mode_check CHECK (((rounding_mode)::text = ANY ((ARRAY['none'::character varying, 'ceil'::character varying, 'floor'::character varying, 'round'::character varying])::text[]))),
    CONSTRAINT billable_metric_rounding_precision_check CHECK (((rounding_precision >= 0) AND (rounding_precision <= 12))),
    CONSTRAINT billable_metric_aggregation_check CHECK (((aggregation)::text = ANY ((ARRAY['sum'::character varying, 'count'::character varying, 'count_distinct'::character varying, 'min'::character varying, 'max'::character varying, 'avg'::character varying, 'first'::character varying, 'last'::character varying, 'median'::character varying, 'percentile'::character varying])::text[]))),
    CONSTRAINT billable_metric_aggregation_target_property_check CHECK (((((aggregation)::text = 'count'::text) AND (target_property IS NULL)) OR (((aggregation)::text <> 'count'::text) AND (target_property IS NOT NULL) AND (btrim((target_property)::text) <> ''::text)))),
    CONSTRAINT billable_metric_unit_size_check CHECK ((unit_size > (0)::numeric))
);

CREATE TABLE live.billable_metric (
    organization_id uuid NOT NULL,
    code character varying NOT NULL,
    name character varying NOT NULL COLLATE shared.korean,
    event_type character varying NOT NULL,
    aggregation character varying NOT NULL,
    target_property character varying,
    dimension_properties text[] DEFAULT '{}'::text[] NOT NULL,
    unit_size numeric DEFAULT 1 NOT NULL,
    rounding_mode character varying DEFAULT 'none'::character varying NOT NULL,
    rounding_precision integer DEFAULT 0 NOT NULL,
    percentile numeric,
    archived_at timestamp with time zone,
    active_from character varying DEFAULT '0001-01'::character varying NOT NULL,
    inactive_from character varying,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT billable_metric_percentile_check CHECK (((percentile >= (0)::numeric) AND (percentile <= (100)::numeric))),
    CONSTRAINT billable_metric_aggregation_percentile_check CHECK ((((aggregation)::text = 'percentile'::text) = (percentile IS NOT NULL))),
    CONSTRAINT billable_metric_rounding_mode_check CHECK (((rounding_mode)::text = ANY ((ARRAY['none'::character varying, 'ceil'::character varying, 'floor'::character varying, 'round'::character varying])::text[]))),
    CONSTRAINT billable_metric_rounding_precision_check CHECK (((rounding_precision >= 0) AND (rounding_precision <= 12))),
    CONSTRAINT billable_metric_aggregation_check CHECK (((aggregation)::text = ANY ((ARRAY['sum'::character varying, 'count'::character varying, 'count_distinct'::character varying, 'min'::character varying, 'max'::character varying, 'avg'::character varying, 'first'::character varying, 'last'::character varying, 'median'::character varying, 'percentile'::character varying])::text[]))),
    CONSTRAINT billable_metric_aggregation_target_property_check CHECK (((((aggregation)::text = 'count'::text) AND (target_property IS NULL)) OR (((aggregation)::text <> 'count'::text) AND (target_property IS NOT NULL) AND (btrim((target_property)::text) <> ''::text)))),
    CONSTRAINT billable_metric_unit_size_check CHECK ((unit_size > (0)::numeric))
);

CREATE TABLE test.billable_metric_period (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    billable_metric_code character varying NOT NULL,
    active_from character varying NOT NULL,
    active_until character varying NOT NULL,
    CONSTRAINT billable_metric_period_range_check CHECK (((active_from)::text < (active_until)::text))
);

CREATE TABLE live.billable_metric_period (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    billable_metric_code character varying NOT NULL,
    active_from character varying NOT NULL,
    active_until character varying NOT NULL,
    CONSTRAINT billable_metric_period_range_check CHECK (((active_from)::text < (active_until)::text))
);

CREATE TABLE test.billing_key (
    organization_id uuid NOT NULL,
    customer_id uuid NOT NULL,
    mode character varying NOT NULL,
    billing_key character varying NOT NULL,
    card_issuer_code character varying NOT NULL,
    card_number character varying NOT NULL,
    authenticated_at timestamp with time zone NOT NULL,
    consent_document_version character varying NOT NULL,
    consented_at timestamp with time zone NOT NULL,
    archived_at timestamp with time zone,
    credential_updated_at timestamp with time zone,
    CONSTRAINT billing_key_mode_check CHECK (mode = 'test')
);

CREATE TABLE live.billing_key (
    organization_id uuid NOT NULL,
    customer_id uuid NOT NULL,
    mode character varying NOT NULL,
    billing_key character varying NOT NULL,
    card_issuer_code character varying NOT NULL,
    card_number character varying NOT NULL,
    authenticated_at timestamp with time zone NOT NULL,
    consent_document_version character varying NOT NULL,
    consented_at timestamp with time zone NOT NULL,
    archived_at timestamp with time zone,
    credential_updated_at timestamp with time zone,
    CONSTRAINT billing_key_mode_check CHECK (mode = 'live')
);

CREATE TABLE test.billing_key_history (
    organization_id uuid CONSTRAINT billing_key_organization_id_not_null NOT NULL,
    customer_id uuid CONSTRAINT billing_key_customer_id_not_null NOT NULL,
    mode character varying CONSTRAINT billing_key_mode_not_null NOT NULL,
    billing_key character varying CONSTRAINT billing_key_billing_key_not_null NOT NULL,
    card_issuer_code character varying CONSTRAINT billing_key_card_issuer_code_not_null NOT NULL,
    card_number character varying CONSTRAINT billing_key_card_number_not_null NOT NULL,
    authenticated_at timestamp with time zone CONSTRAINT billing_key_authenticated_at_not_null NOT NULL,
    consent_document_version character varying CONSTRAINT billing_key_consent_document_version_not_null NOT NULL,
    consented_at timestamp with time zone CONSTRAINT billing_key_consented_at_not_null NOT NULL,
    archived_at timestamp with time zone,
    history_id uuid DEFAULT gen_random_uuid() NOT NULL,
    retired_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    credential_updated_at timestamp with time zone,
    CONSTRAINT billing_key_history_mode_check CHECK (mode = 'test')
);

CREATE TABLE live.billing_key_history (
    organization_id uuid CONSTRAINT billing_key_organization_id_not_null NOT NULL,
    customer_id uuid CONSTRAINT billing_key_customer_id_not_null NOT NULL,
    mode character varying CONSTRAINT billing_key_mode_not_null NOT NULL,
    billing_key character varying CONSTRAINT billing_key_billing_key_not_null NOT NULL,
    card_issuer_code character varying CONSTRAINT billing_key_card_issuer_code_not_null NOT NULL,
    card_number character varying CONSTRAINT billing_key_card_number_not_null NOT NULL,
    authenticated_at timestamp with time zone CONSTRAINT billing_key_authenticated_at_not_null NOT NULL,
    consent_document_version character varying CONSTRAINT billing_key_consent_document_version_not_null NOT NULL,
    consented_at timestamp with time zone CONSTRAINT billing_key_consented_at_not_null NOT NULL,
    archived_at timestamp with time zone,
    history_id uuid DEFAULT gen_random_uuid() NOT NULL,
    retired_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    credential_updated_at timestamp with time zone,
    CONSTRAINT billing_key_history_mode_check CHECK (mode = 'live')
);

CREATE TABLE test.customer (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    organization_id uuid NOT NULL,
    name character varying NOT NULL COLLATE shared.korean,
    created_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    billing_email character varying,
    external_id character varying(255),
    archived_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT customer_external_id_check CHECK (((external_id IS NULL) OR ((length((external_id)::text) >= 1) AND (length((external_id)::text) <= 255))))
);

CREATE TABLE live.customer (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    organization_id uuid NOT NULL,
    name character varying NOT NULL COLLATE shared.korean,
    created_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    billing_email character varying,
    external_id character varying(255),
    archived_at timestamp with time zone,
    version bigint DEFAULT 0 NOT NULL,
    CONSTRAINT customer_external_id_check CHECK (((external_id IS NULL) OR ((length((external_id)::text) >= 1) AND (length((external_id)::text) <= 255))))
);

CREATE TABLE test.event (
    organization_id uuid NOT NULL,
    transaction_id character varying NOT NULL,
    customer_id uuid NOT NULL,
    type character varying NOT NULL,
    properties jsonb DEFAULT '{}'::jsonb NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    received_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    request_id uuid,
    actor_type character varying,
    actor_id uuid,
    CONSTRAINT event_actor_identity_check CHECK (
CASE
    WHEN ((actor_type IS NULL) OR ((actor_type)::text = 'system'::text)) THEN (actor_id IS NULL)
    ELSE (actor_id IS NOT NULL)
END),
    CONSTRAINT event_actor_type_check CHECK (((actor_type)::text = ANY ((ARRAY['api_key'::character varying, 'user_account'::character varying, 'system'::character varying])::text[])))
);

CREATE TABLE live.event (
    organization_id uuid NOT NULL,
    transaction_id character varying NOT NULL,
    customer_id uuid NOT NULL,
    type character varying NOT NULL,
    properties jsonb DEFAULT '{}'::jsonb NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    received_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    request_id uuid,
    actor_type character varying,
    actor_id uuid,
    CONSTRAINT event_actor_identity_check CHECK (
CASE
    WHEN ((actor_type IS NULL) OR ((actor_type)::text = 'system'::text)) THEN (actor_id IS NULL)
    ELSE (actor_id IS NOT NULL)
END),
    CONSTRAINT event_actor_type_check CHECK (((actor_type)::text = ANY ((ARRAY['api_key'::character varying, 'user_account'::character varying, 'system'::character varying])::text[])))
);

CREATE TABLE test.event_rejection (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    organization_id uuid NOT NULL,
    transaction_id character varying(255),
    customer_id uuid,
    type character varying(255),
    occurred_at timestamp with time zone,
    error_code character varying NOT NULL,
    error_fields jsonb DEFAULT '[]'::jsonb NOT NULL,
    request_id uuid,
    actor_type character varying NOT NULL,
    actor_id uuid,
    batch_index integer,
    recorded_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    CONSTRAINT event_rejection_actor_type_check CHECK (((actor_type)::text = ANY ((ARRAY['api_key'::character varying, 'user_account'::character varying, 'system'::character varying])::text[]))),
    CONSTRAINT event_rejection_batch_index_check CHECK (((batch_index >= 0) AND (batch_index <= 99))),
    CONSTRAINT event_rejection_error_code_check CHECK (((error_code)::text = ANY ((ARRAY['customer_not_found'::character varying, 'customer_archived'::character varying, 'event_too_old'::character varying, 'validation_error'::character varying])::text[]))),
    CONSTRAINT event_rejection_error_fields_check CHECK ((jsonb_typeof(error_fields) = 'array'::text))
);

CREATE TABLE live.event_rejection (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    organization_id uuid NOT NULL,
    transaction_id character varying(255),
    customer_id uuid,
    type character varying(255),
    occurred_at timestamp with time zone,
    error_code character varying NOT NULL,
    error_fields jsonb DEFAULT '[]'::jsonb NOT NULL,
    request_id uuid,
    actor_type character varying NOT NULL,
    actor_id uuid,
    batch_index integer,
    recorded_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    CONSTRAINT event_rejection_actor_type_check CHECK (((actor_type)::text = ANY ((ARRAY['api_key'::character varying, 'user_account'::character varying, 'system'::character varying])::text[]))),
    CONSTRAINT event_rejection_batch_index_check CHECK (((batch_index >= 0) AND (batch_index <= 99))),
    CONSTRAINT event_rejection_error_code_check CHECK (((error_code)::text = ANY ((ARRAY['customer_not_found'::character varying, 'customer_archived'::character varying, 'event_too_old'::character varying, 'validation_error'::character varying])::text[]))),
    CONSTRAINT event_rejection_error_fields_check CHECK ((jsonb_typeof(error_fields) = 'array'::text))
);

CREATE TABLE test.event_rejection_recording_start (
    singleton boolean DEFAULT true NOT NULL,
    started_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    CONSTRAINT event_rejection_recording_start_singleton_check CHECK (singleton)
);

CREATE TABLE live.event_rejection_recording_start (
    singleton boolean DEFAULT true NOT NULL,
    started_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    CONSTRAINT event_rejection_recording_start_singleton_check CHECK (singleton)
);

CREATE TABLE test.idempotent_request (
    organization_id uuid NOT NULL,
    operation character varying NOT NULL,
    key_hash character varying(64) NOT NULL,
    request_hash character varying(64) NOT NULL,
    response_status integer,
    response_body text,
    created_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    CONSTRAINT idempotent_request_response_check CHECK (((response_status IS NULL) = (response_body IS NULL)))
);

CREATE TABLE live.idempotent_request (
    organization_id uuid NOT NULL,
    operation character varying NOT NULL,
    key_hash character varying(64) NOT NULL,
    request_hash character varying(64) NOT NULL,
    response_status integer,
    response_body text,
    created_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    CONSTRAINT idempotent_request_response_check CHECK (((response_status IS NULL) = (response_body IS NULL)))
);

CREATE TABLE test.invoice (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    organization_id uuid NOT NULL,
    customer_id uuid NOT NULL,
    period character varying NOT NULL,
    supply_amount bigint NOT NULL,
    tax_amount bigint NOT NULL,
    finalized_at timestamp with time zone NOT NULL,
    total_amount bigint NOT NULL,
    customer_name character varying NOT NULL COLLATE shared.korean,
    display_number bigint NOT NULL,
    CONSTRAINT invoice_period_check CHECK (((period)::text ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'::text)),
    CONSTRAINT invoice_total_amount_check CHECK ((total_amount = (supply_amount + tax_amount)))
);

CREATE TABLE live.invoice (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    organization_id uuid NOT NULL,
    customer_id uuid NOT NULL,
    period character varying NOT NULL,
    supply_amount bigint NOT NULL,
    tax_amount bigint NOT NULL,
    finalized_at timestamp with time zone NOT NULL,
    total_amount bigint NOT NULL,
    customer_name character varying NOT NULL COLLATE shared.korean,
    display_number bigint NOT NULL,
    CONSTRAINT invoice_period_check CHECK (((period)::text ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'::text)),
    CONSTRAINT invoice_total_amount_check CHECK ((total_amount = (supply_amount + tax_amount)))
);

ALTER TABLE test.invoice ALTER COLUMN display_number ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME test.invoice_display_number_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

ALTER TABLE live.invoice ALTER COLUMN display_number ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME live.invoice_display_number_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);

CREATE TABLE test.invoice_line (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    organization_id uuid NOT NULL,
    invoice_id uuid NOT NULL,
    billable_metric_code character varying,
    target_property character varying,
    dimension_values jsonb NOT NULL,
    quantity numeric NOT NULL,
    unit_price numeric NOT NULL,
    amount bigint NOT NULL,
    line_type character varying DEFAULT 'per_unit'::character varying NOT NULL,
    tier_index integer DEFAULT 0 NOT NULL,
    quantity_numerator numeric,
    quantity_denominator numeric,
    billable_metric_name character varying,
    CONSTRAINT invoice_line_quantity_denominator_check CHECK ((quantity_denominator > (0)::numeric)),
    CONSTRAINT invoice_line_quantity_fraction_check CHECK (((quantity_numerator IS NULL) = (quantity_denominator IS NULL))),
    CONSTRAINT invoice_line_type_check CHECK (((((line_type)::text = ANY ((ARRAY['per_unit'::character varying, 'volume'::character varying])::text[])) AND (billable_metric_code IS NOT NULL) AND (tier_index = 0)) OR (((line_type)::text = 'graduated_tier'::text) AND (billable_metric_code IS NOT NULL) AND (tier_index >= 0)) OR (((line_type)::text = ANY ((ARRAY['base_fee'::character varying, 'minimum_adjustment'::character varying])::text[])) AND (billable_metric_code IS NULL) AND (tier_index = 0) AND (dimension_values = '{}'::jsonb) AND (quantity = (1)::numeric)))),
    CONSTRAINT invoice_line_unit_price_check CHECK ((unit_price >= (0)::numeric))
);

CREATE TABLE live.invoice_line (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    organization_id uuid NOT NULL,
    invoice_id uuid NOT NULL,
    billable_metric_code character varying,
    target_property character varying,
    dimension_values jsonb NOT NULL,
    quantity numeric NOT NULL,
    unit_price numeric NOT NULL,
    amount bigint NOT NULL,
    line_type character varying DEFAULT 'per_unit'::character varying NOT NULL,
    tier_index integer DEFAULT 0 NOT NULL,
    quantity_numerator numeric,
    quantity_denominator numeric,
    billable_metric_name character varying,
    CONSTRAINT invoice_line_quantity_denominator_check CHECK ((quantity_denominator > (0)::numeric)),
    CONSTRAINT invoice_line_quantity_fraction_check CHECK (((quantity_numerator IS NULL) = (quantity_denominator IS NULL))),
    CONSTRAINT invoice_line_type_check CHECK (((((line_type)::text = ANY ((ARRAY['per_unit'::character varying, 'volume'::character varying])::text[])) AND (billable_metric_code IS NOT NULL) AND (tier_index = 0)) OR (((line_type)::text = 'graduated_tier'::text) AND (billable_metric_code IS NOT NULL) AND (tier_index >= 0)) OR (((line_type)::text = ANY ((ARRAY['base_fee'::character varying, 'minimum_adjustment'::character varying])::text[])) AND (billable_metric_code IS NULL) AND (tier_index = 0) AND (dimension_values = '{}'::jsonb) AND (quantity = (1)::numeric)))),
    CONSTRAINT invoice_line_unit_price_check CHECK ((unit_price >= (0)::numeric))
);

CREATE TABLE test.invoice_view_link (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    invoice_id uuid NOT NULL,
    token_hash bytea NOT NULL,
    mode character varying NOT NULL,
    created_at timestamp with time zone NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    allow_payment_retry boolean DEFAULT false NOT NULL,
    CONSTRAINT invoice_view_link_expires_at_check CHECK ((expires_at > created_at)),
    CONSTRAINT invoice_view_link_mode_check CHECK (mode = 'test'),
    CONSTRAINT invoice_view_link_token_hash_check CHECK ((octet_length(token_hash) = 32))
);

CREATE TABLE live.invoice_view_link (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    invoice_id uuid NOT NULL,
    token_hash bytea NOT NULL,
    mode character varying NOT NULL,
    created_at timestamp with time zone NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    allow_payment_retry boolean DEFAULT false NOT NULL,
    CONSTRAINT invoice_view_link_expires_at_check CHECK ((expires_at > created_at)),
    CONSTRAINT invoice_view_link_mode_check CHECK (mode = 'live'),
    CONSTRAINT invoice_view_link_token_hash_check CHECK ((octet_length(token_hash) = 32))
);

CREATE TABLE test.outbound_email (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    kind character varying NOT NULL,
    period character varying NOT NULL,
    invoice_id uuid,
    recipient_email character varying,
    subject character varying NOT NULL,
    html_body character varying NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT outbound_email_kind_check CHECK (((kind)::text = ANY ((ARRAY['invoice'::character varying, 'finalization_report'::character varying])::text[]))),
    CONSTRAINT outbound_email_kind_invoice_check CHECK ((((kind)::text = 'invoice'::text) = (invoice_id IS NOT NULL))),
    CONSTRAINT outbound_email_period_format CHECK (((period)::text ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'::text))
);

CREATE TABLE live.outbound_email (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    kind character varying NOT NULL,
    period character varying NOT NULL,
    invoice_id uuid,
    recipient_email character varying,
    subject character varying NOT NULL,
    html_body character varying NOT NULL,
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT outbound_email_kind_check CHECK (((kind)::text = ANY ((ARRAY['invoice'::character varying, 'finalization_report'::character varying])::text[]))),
    CONSTRAINT outbound_email_kind_invoice_check CHECK ((((kind)::text = 'invoice'::text) = (invoice_id IS NOT NULL))),
    CONSTRAINT outbound_email_period_format CHECK (((period)::text ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'::text))
);

CREATE TABLE test.payment_attempt (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    customer_id uuid NOT NULL,
    invoice_id uuid NOT NULL,
    amount bigint NOT NULL,
    order_name character varying(100) NOT NULL,
    status character varying NOT NULL,
    payment_key character varying,
    failure_code character varying,
    failure_message character varying,
    requested_at timestamp with time zone NOT NULL,
    completed_at timestamp with time zone,
    mode character varying NOT NULL,
    credential_updated_at timestamp with time zone,
    CONSTRAINT payment_attempt_amount_check CHECK ((amount > 0)),
    CONSTRAINT payment_attempt_mode_check CHECK (mode = 'test'),
    CONSTRAINT payment_attempt_status_check CHECK (((status)::text = ANY ((ARRAY['pending'::character varying, 'done'::character varying, 'failed'::character varying])::text[]))),
    CONSTRAINT payment_attempt_status_result_check CHECK (((((status)::text = 'pending'::text) AND (completed_at IS NULL) AND (payment_key IS NULL) AND (failure_code IS NULL) AND (failure_message IS NULL)) OR (((status)::text = 'done'::text) AND (completed_at IS NOT NULL) AND (payment_key IS NOT NULL) AND (failure_code IS NULL) AND (failure_message IS NULL)) OR (((status)::text = 'failed'::text) AND (completed_at IS NOT NULL) AND (failure_code IS NOT NULL) AND (payment_key IS NULL))))
);

CREATE TABLE live.payment_attempt (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    customer_id uuid NOT NULL,
    invoice_id uuid NOT NULL,
    amount bigint NOT NULL,
    order_name character varying(100) NOT NULL,
    status character varying NOT NULL,
    payment_key character varying,
    failure_code character varying,
    failure_message character varying,
    requested_at timestamp with time zone NOT NULL,
    completed_at timestamp with time zone,
    mode character varying NOT NULL,
    credential_updated_at timestamp with time zone,
    CONSTRAINT payment_attempt_amount_check CHECK ((amount > 0)),
    CONSTRAINT payment_attempt_mode_check CHECK (mode = 'live'),
    CONSTRAINT payment_attempt_status_check CHECK (((status)::text = ANY ((ARRAY['pending'::character varying, 'done'::character varying, 'failed'::character varying])::text[]))),
    CONSTRAINT payment_attempt_status_result_check CHECK (((((status)::text = 'pending'::text) AND (completed_at IS NULL) AND (payment_key IS NULL) AND (failure_code IS NULL) AND (failure_message IS NULL)) OR (((status)::text = 'done'::text) AND (completed_at IS NOT NULL) AND (payment_key IS NOT NULL) AND (failure_code IS NULL) AND (failure_message IS NULL)) OR (((status)::text = 'failed'::text) AND (completed_at IS NOT NULL) AND (failure_code IS NOT NULL) AND (payment_key IS NULL))))
);

CREATE TABLE test.payment_cancellation (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    payment_attempt_id uuid NOT NULL,
    mode character varying NOT NULL,
    amount bigint NOT NULL,
    reason character varying(200) NOT NULL,
    status character varying NOT NULL,
    transaction_key character varying,
    failure_code character varying,
    failure_message character varying,
    requested_at timestamp with time zone NOT NULL,
    completed_at timestamp with time zone,
    idempotency_fingerprint character varying(64),
    CONSTRAINT payment_cancellation_amount_check CHECK ((amount > 0)),
    CONSTRAINT payment_cancellation_idempotency_fingerprint_check CHECK (((idempotency_fingerprint)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT payment_cancellation_mode_check CHECK (mode = 'test'),
    CONSTRAINT payment_cancellation_status_check CHECK (((status)::text = ANY ((ARRAY['pending'::character varying, 'done'::character varying, 'failed'::character varying])::text[]))),
    CONSTRAINT payment_cancellation_status_result_check CHECK (((((status)::text = 'pending'::text) AND (completed_at IS NULL) AND (transaction_key IS NULL) AND (failure_code IS NULL) AND (failure_message IS NULL)) OR (((status)::text = 'done'::text) AND (completed_at IS NOT NULL) AND (transaction_key IS NOT NULL) AND (failure_code IS NULL) AND (failure_message IS NULL)) OR (((status)::text = 'failed'::text) AND (completed_at IS NOT NULL) AND (failure_code IS NOT NULL) AND (transaction_key IS NULL))))
);

CREATE TABLE live.payment_cancellation (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    payment_attempt_id uuid NOT NULL,
    mode character varying NOT NULL,
    amount bigint NOT NULL,
    reason character varying(200) NOT NULL,
    status character varying NOT NULL,
    transaction_key character varying,
    failure_code character varying,
    failure_message character varying,
    requested_at timestamp with time zone NOT NULL,
    completed_at timestamp with time zone,
    idempotency_fingerprint character varying(64),
    CONSTRAINT payment_cancellation_amount_check CHECK ((amount > 0)),
    CONSTRAINT payment_cancellation_idempotency_fingerprint_check CHECK (((idempotency_fingerprint)::text ~ '^[0-9a-f]{64}$'::text)),
    CONSTRAINT payment_cancellation_mode_check CHECK (mode = 'live'),
    CONSTRAINT payment_cancellation_status_check CHECK (((status)::text = ANY ((ARRAY['pending'::character varying, 'done'::character varying, 'failed'::character varying])::text[]))),
    CONSTRAINT payment_cancellation_status_result_check CHECK (((((status)::text = 'pending'::text) AND (completed_at IS NULL) AND (transaction_key IS NULL) AND (failure_code IS NULL) AND (failure_message IS NULL)) OR (((status)::text = 'done'::text) AND (completed_at IS NOT NULL) AND (transaction_key IS NOT NULL) AND (failure_code IS NULL) AND (failure_message IS NULL)) OR (((status)::text = 'failed'::text) AND (completed_at IS NOT NULL) AND (failure_code IS NOT NULL) AND (transaction_key IS NULL))))
);

CREATE TABLE test.payment_method_registration (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    customer_id uuid NOT NULL,
    token_hash character varying NOT NULL,
    mode character varying NOT NULL,
    created_at timestamp with time zone NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    used_at timestamp with time zone,
    credential_updated_at timestamp with time zone,
    CONSTRAINT payment_method_registration_mode_check CHECK (mode = 'test')
);

CREATE TABLE live.payment_method_registration (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    customer_id uuid NOT NULL,
    token_hash character varying NOT NULL,
    mode character varying NOT NULL,
    created_at timestamp with time zone NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    used_at timestamp with time zone,
    credential_updated_at timestamp with time zone,
    CONSTRAINT payment_method_registration_mode_check CHECK (mode = 'live')
);

CREATE TABLE test.payment_reconciliation_mismatch (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    mode character varying NOT NULL,
    kind character varying NOT NULL,
    order_id character varying NOT NULL,
    payment_key character varying,
    transaction_key character varying,
    meterengine_amount bigint,
    toss_payments_amount bigint,
    transacted_at timestamp with time zone,
    detected_at timestamp with time zone NOT NULL,
    CONSTRAINT payment_reconciliation_mismatch_kind_check CHECK (((kind)::text = ANY ((ARRAY['missing_in_meterengine'::character varying, 'missing_in_toss_payments'::character varying, 'amount_mismatch'::character varying, 'status_mismatch'::character varying])::text[]))),
    CONSTRAINT payment_reconciliation_mismatch_mode_check CHECK (mode = 'test')
);

CREATE TABLE live.payment_reconciliation_mismatch (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    mode character varying NOT NULL,
    kind character varying NOT NULL,
    order_id character varying NOT NULL,
    payment_key character varying,
    transaction_key character varying,
    meterengine_amount bigint,
    toss_payments_amount bigint,
    transacted_at timestamp with time zone,
    detected_at timestamp with time zone NOT NULL,
    CONSTRAINT payment_reconciliation_mismatch_kind_check CHECK (((kind)::text = ANY ((ARRAY['missing_in_meterengine'::character varying, 'missing_in_toss_payments'::character varying, 'amount_mismatch'::character varying, 'status_mismatch'::character varying])::text[]))),
    CONSTRAINT payment_reconciliation_mismatch_mode_check CHECK (mode = 'live')
);

CREATE TABLE test.payment_setting (
    organization_id uuid NOT NULL,
    mode character varying NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT payment_setting_mode_check CHECK (mode = 'test')
);

CREATE TABLE live.payment_setting (
    organization_id uuid NOT NULL,
    mode character varying NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT payment_setting_mode_check CHECK (mode = 'live')
);

CREATE TABLE test.price_plan (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    organization_id uuid NOT NULL,
    customer_id uuid,
    effective_from character varying DEFAULT '2000-01'::character varying NOT NULL,
    effective_until character varying,
    base_fee bigint NOT NULL,
    minimum_amount bigint NOT NULL,
    archived_at timestamp with time zone,
    archived_from character varying,
    CONSTRAINT price_plan_base_fee_check CHECK ((base_fee >= 0)),
    CONSTRAINT price_plan_effective_from_check CHECK ((((effective_from)::text ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'::text) AND ((effective_from)::text >= '0001-01'::text))),
    CONSTRAINT price_plan_effective_until_check CHECK (((effective_until IS NULL) OR (((effective_until)::text ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'::text) AND ((effective_until)::text > (effective_from)::text)))),
    CONSTRAINT price_plan_minimum_amount_check CHECK ((minimum_amount >= 0))
);

CREATE TABLE live.price_plan (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    organization_id uuid NOT NULL,
    customer_id uuid,
    effective_from character varying DEFAULT '2000-01'::character varying NOT NULL,
    effective_until character varying,
    base_fee bigint NOT NULL,
    minimum_amount bigint NOT NULL,
    archived_at timestamp with time zone,
    archived_from character varying,
    CONSTRAINT price_plan_base_fee_check CHECK ((base_fee >= 0)),
    CONSTRAINT price_plan_effective_from_check CHECK ((((effective_from)::text ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'::text) AND ((effective_from)::text >= '0001-01'::text))),
    CONSTRAINT price_plan_effective_until_check CHECK (((effective_until IS NULL) OR (((effective_until)::text ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'::text) AND ((effective_until)::text > (effective_from)::text)))),
    CONSTRAINT price_plan_minimum_amount_check CHECK ((minimum_amount >= 0))
);

CREATE TABLE test.price_rate (
    organization_id uuid NOT NULL,
    billable_metric_code character varying NOT NULL,
    dimension_values jsonb NOT NULL,
    unit_price numeric,
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    customer_id uuid,
    effective_from character varying DEFAULT '2000-01'::character varying NOT NULL,
    effective_until character varying,
    model character varying DEFAULT 'per_unit'::character varying NOT NULL,
    tiers jsonb,
    archived_at timestamp with time zone,
    archived_from character varying,
    CONSTRAINT price_rate_effective_from_check CHECK ((((effective_from)::text ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'::text) AND ((effective_from)::text >= '0001-01'::text))),
    CONSTRAINT price_rate_effective_until_check CHECK (((effective_until IS NULL) OR (((effective_until)::text ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'::text) AND ((effective_until)::text > (effective_from)::text)))),
    CONSTRAINT price_rate_model_check CHECK (((((model)::text = 'per_unit'::text) AND (unit_price IS NOT NULL) AND (tiers IS NULL)) OR (((model)::text = ANY ((ARRAY['graduated'::character varying, 'volume'::character varying])::text[])) AND (unit_price IS NULL) AND test.valid_price_tiers(tiers)))),
    CONSTRAINT price_rate_unit_price_check CHECK ((unit_price >= (0)::numeric))
);

CREATE TABLE live.price_rate (
    organization_id uuid NOT NULL,
    billable_metric_code character varying NOT NULL,
    dimension_values jsonb NOT NULL,
    unit_price numeric,
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    customer_id uuid,
    effective_from character varying DEFAULT '2000-01'::character varying NOT NULL,
    effective_until character varying,
    model character varying DEFAULT 'per_unit'::character varying NOT NULL,
    tiers jsonb,
    archived_at timestamp with time zone,
    archived_from character varying,
    CONSTRAINT price_rate_effective_from_check CHECK ((((effective_from)::text ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'::text) AND ((effective_from)::text >= '0001-01'::text))),
    CONSTRAINT price_rate_effective_until_check CHECK (((effective_until IS NULL) OR (((effective_until)::text ~ '^[0-9]{4}-(0[1-9]|1[0-2])$'::text) AND ((effective_until)::text > (effective_from)::text)))),
    CONSTRAINT price_rate_model_check CHECK (((((model)::text = 'per_unit'::text) AND (unit_price IS NOT NULL) AND (tiers IS NULL)) OR (((model)::text = ANY ((ARRAY['graduated'::character varying, 'volume'::character varying])::text[])) AND (unit_price IS NULL) AND live.valid_price_tiers(tiers)))),
    CONSTRAINT price_rate_unit_price_check CHECK ((unit_price >= (0)::numeric))
);

CREATE TABLE test.toss_payments_credential (
    organization_id uuid NOT NULL,
    mode character varying NOT NULL,
    client_key character varying NOT NULL,
    secret_key character varying NOT NULL,
    secret_key_last_four character varying NOT NULL,
    verified_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    archived_at timestamp with time zone,
    CONSTRAINT toss_payments_credential_mode_check CHECK (mode = 'test')
);

CREATE TABLE live.toss_payments_credential (
    organization_id uuid NOT NULL,
    mode character varying NOT NULL,
    client_key character varying NOT NULL,
    secret_key character varying NOT NULL,
    secret_key_last_four character varying NOT NULL,
    verified_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    archived_at timestamp with time zone,
    CONSTRAINT toss_payments_credential_mode_check CHECK (mode = 'live')
);

CREATE TABLE test.toss_payments_credential_history (
    organization_id uuid CONSTRAINT toss_payments_credential_organization_id_not_null NOT NULL,
    mode character varying CONSTRAINT toss_payments_credential_mode_not_null NOT NULL,
    client_key character varying CONSTRAINT toss_payments_credential_client_key_not_null NOT NULL,
    secret_key character varying CONSTRAINT toss_payments_credential_secret_key_not_null NOT NULL,
    secret_key_last_four character varying CONSTRAINT toss_payments_credential_secret_key_last_four_not_null NOT NULL,
    verified_at timestamp with time zone CONSTRAINT toss_payments_credential_verified_at_not_null NOT NULL,
    updated_at timestamp with time zone CONSTRAINT toss_payments_credential_updated_at_not_null NOT NULL,
    archived_at timestamp with time zone,
    history_id uuid DEFAULT gen_random_uuid() NOT NULL,
    retired_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    CONSTRAINT toss_payments_credential_history_mode_check CHECK (mode = 'test')
);

CREATE TABLE live.toss_payments_credential_history (
    organization_id uuid CONSTRAINT toss_payments_credential_organization_id_not_null NOT NULL,
    mode character varying CONSTRAINT toss_payments_credential_mode_not_null NOT NULL,
    client_key character varying CONSTRAINT toss_payments_credential_client_key_not_null NOT NULL,
    secret_key character varying CONSTRAINT toss_payments_credential_secret_key_not_null NOT NULL,
    secret_key_last_four character varying CONSTRAINT toss_payments_credential_secret_key_last_four_not_null NOT NULL,
    verified_at timestamp with time zone CONSTRAINT toss_payments_credential_verified_at_not_null NOT NULL,
    updated_at timestamp with time zone CONSTRAINT toss_payments_credential_updated_at_not_null NOT NULL,
    archived_at timestamp with time zone,
    history_id uuid DEFAULT gen_random_uuid() NOT NULL,
    retired_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    CONSTRAINT toss_payments_credential_history_mode_check CHECK (mode = 'live')
);

CREATE TABLE test.webhook_delivery (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    webhook_endpoint_id uuid NOT NULL,
    webhook_event_id uuid NOT NULL,
    status character varying DEFAULT 'pending'::character varying NOT NULL,
    attempt_count integer DEFAULT 0 NOT NULL,
    next_attempt_at timestamp with time zone,
    last_attempted_at timestamp with time zone,
    claim_token uuid,
    response_status integer,
    failure_reason character varying,
    created_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    CONSTRAINT webhook_delivery_failure_reason_check CHECK (failure_reason IN ('timeout', 'connection_error', 'unexpected_status', 'blocked_address', 'endpoint_disabled', 'secret_unavailable', 'lease_expired')),
    CONSTRAINT webhook_delivery_attempt_count_check CHECK ((attempt_count >= 0)),
    CONSTRAINT webhook_delivery_status_check CHECK (((status)::text = ANY ((ARRAY['pending'::character varying, 'done'::character varying, 'failed'::character varying])::text[])))
);

CREATE TABLE live.webhook_delivery (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    webhook_endpoint_id uuid NOT NULL,
    webhook_event_id uuid NOT NULL,
    status character varying DEFAULT 'pending'::character varying NOT NULL,
    attempt_count integer DEFAULT 0 NOT NULL,
    next_attempt_at timestamp with time zone,
    last_attempted_at timestamp with time zone,
    claim_token uuid,
    response_status integer,
    failure_reason character varying,
    created_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    CONSTRAINT webhook_delivery_failure_reason_check CHECK (failure_reason IN ('timeout', 'connection_error', 'unexpected_status', 'blocked_address', 'endpoint_disabled', 'secret_unavailable', 'lease_expired')),
    CONSTRAINT webhook_delivery_attempt_count_check CHECK ((attempt_count >= 0)),
    CONSTRAINT webhook_delivery_status_check CHECK (((status)::text = ANY ((ARRAY['pending'::character varying, 'done'::character varying, 'failed'::character varying])::text[])))
);

CREATE TABLE test.webhook_delivery_attempt (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    webhook_delivery_id uuid NOT NULL,
    attempted_at timestamp with time zone NOT NULL,
    response_status integer,
    failure_reason character varying,
    duration_ms bigint NOT NULL,
    CONSTRAINT webhook_delivery_attempt_duration_ms_check CHECK ((duration_ms >= 0)),
    CONSTRAINT webhook_delivery_attempt_failure_reason_check CHECK (((failure_reason)::text = ANY ((ARRAY['timeout'::character varying, 'connection_error'::character varying, 'unexpected_status'::character varying, 'blocked_address'::character varying, 'endpoint_disabled'::character varying, 'secret_unavailable'::character varying, 'lease_expired'::character varying])::text[])))
);

CREATE TABLE live.webhook_delivery_attempt (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    webhook_delivery_id uuid NOT NULL,
    attempted_at timestamp with time zone NOT NULL,
    response_status integer,
    failure_reason character varying,
    duration_ms bigint NOT NULL,
    CONSTRAINT webhook_delivery_attempt_duration_ms_check CHECK ((duration_ms >= 0)),
    CONSTRAINT webhook_delivery_attempt_failure_reason_check CHECK (((failure_reason)::text = ANY ((ARRAY['timeout'::character varying, 'connection_error'::character varying, 'unexpected_status'::character varying, 'blocked_address'::character varying, 'endpoint_disabled'::character varying, 'secret_unavailable'::character varying, 'lease_expired'::character varying])::text[])))
);

CREATE TABLE test.webhook_endpoint (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    url character varying(2048) NOT NULL,
    secret character varying NOT NULL,
    event_types text[] NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    description character varying(1000),
    created_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    deleted_at timestamp with time zone,
    CONSTRAINT webhook_endpoint_event_types_check CHECK ((cardinality(event_types) > 0))
);

CREATE TABLE live.webhook_endpoint (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    url character varying(2048) NOT NULL,
    secret character varying NOT NULL,
    event_types text[] NOT NULL,
    enabled boolean DEFAULT true NOT NULL,
    description character varying(1000),
    created_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL,
    deleted_at timestamp with time zone,
    CONSTRAINT webhook_endpoint_event_types_check CHECK ((cardinality(event_types) > 0))
);

CREATE TABLE test.webhook_event (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    type character varying NOT NULL,
    payload jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL
);

CREATE TABLE live.webhook_event (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    type character varying NOT NULL,
    payload jsonb NOT NULL,
    created_at timestamp with time zone DEFAULT clock_timestamp() NOT NULL
);

ALTER TABLE ONLY test.billable_metric_period
    ADD CONSTRAINT billable_metric_period_pk PRIMARY KEY (id);

ALTER TABLE ONLY live.billable_metric_period
    ADD CONSTRAINT billable_metric_period_pk PRIMARY KEY (id);

ALTER TABLE ONLY test.billable_metric
    ADD CONSTRAINT billable_metric_pk PRIMARY KEY (organization_id, code);

ALTER TABLE ONLY live.billable_metric
    ADD CONSTRAINT billable_metric_pk PRIMARY KEY (organization_id, code);

ALTER TABLE ONLY test.billing_key_history
    ADD CONSTRAINT billing_key_history_pk PRIMARY KEY (history_id);

ALTER TABLE ONLY live.billing_key_history
    ADD CONSTRAINT billing_key_history_pk PRIMARY KEY (history_id);

ALTER TABLE ONLY test.billing_key
    ADD CONSTRAINT billing_key_pk PRIMARY KEY (organization_id, customer_id, mode);

ALTER TABLE ONLY live.billing_key
    ADD CONSTRAINT billing_key_pk PRIMARY KEY (organization_id, customer_id, mode);

ALTER TABLE ONLY test.customer
    ADD CONSTRAINT customer_environment_id_unique UNIQUE (organization_id, id);

ALTER TABLE ONLY live.customer
    ADD CONSTRAINT customer_environment_id_unique UNIQUE (organization_id, id);

ALTER TABLE ONLY test.customer
    ADD CONSTRAINT customer_pk PRIMARY KEY (id);

ALTER TABLE ONLY live.customer
    ADD CONSTRAINT customer_pk PRIMARY KEY (id);

ALTER TABLE ONLY test.event
    ADD CONSTRAINT event_pk PRIMARY KEY (organization_id, transaction_id);

ALTER TABLE ONLY live.event
    ADD CONSTRAINT event_pk PRIMARY KEY (organization_id, transaction_id);

ALTER TABLE ONLY test.event_rejection
    ADD CONSTRAINT event_rejection_pk PRIMARY KEY (id);

ALTER TABLE ONLY live.event_rejection
    ADD CONSTRAINT event_rejection_pk PRIMARY KEY (id);

ALTER TABLE ONLY test.event_rejection_recording_start
    ADD CONSTRAINT event_rejection_recording_start_pk PRIMARY KEY (singleton);

ALTER TABLE ONLY live.event_rejection_recording_start
    ADD CONSTRAINT event_rejection_recording_start_pk PRIMARY KEY (singleton);

ALTER TABLE ONLY test.idempotent_request
    ADD CONSTRAINT idempotent_request_pk PRIMARY KEY (organization_id, operation, key_hash);

ALTER TABLE ONLY live.idempotent_request
    ADD CONSTRAINT idempotent_request_pk PRIMARY KEY (organization_id, operation, key_hash);

ALTER TABLE ONLY test.invoice
    ADD CONSTRAINT invoice_display_number_unique UNIQUE (display_number);

ALTER TABLE ONLY live.invoice
    ADD CONSTRAINT invoice_display_number_unique UNIQUE (display_number);

ALTER TABLE ONLY test.invoice
    ADD CONSTRAINT invoice_environment_id_unique UNIQUE (organization_id, id);

ALTER TABLE ONLY live.invoice
    ADD CONSTRAINT invoice_environment_id_unique UNIQUE (organization_id, id);

ALTER TABLE ONLY test.invoice_line
    ADD CONSTRAINT invoice_line_pk PRIMARY KEY (id);

ALTER TABLE ONLY live.invoice_line
    ADD CONSTRAINT invoice_line_pk PRIMARY KEY (id);

ALTER TABLE ONLY test.invoice
    ADD CONSTRAINT invoice_organization_customer_period_unique UNIQUE (organization_id, customer_id, period);

ALTER TABLE ONLY live.invoice
    ADD CONSTRAINT invoice_organization_customer_period_unique UNIQUE (organization_id, customer_id, period);

ALTER TABLE ONLY test.invoice
    ADD CONSTRAINT invoice_pk PRIMARY KEY (id);

ALTER TABLE ONLY live.invoice
    ADD CONSTRAINT invoice_pk PRIMARY KEY (id);

ALTER TABLE ONLY test.invoice_view_link
    ADD CONSTRAINT invoice_view_link_pk PRIMARY KEY (id);

ALTER TABLE ONLY live.invoice_view_link
    ADD CONSTRAINT invoice_view_link_pk PRIMARY KEY (id);

ALTER TABLE ONLY test.invoice_view_link
    ADD CONSTRAINT invoice_view_link_token_hash_unique UNIQUE (token_hash);

ALTER TABLE ONLY live.invoice_view_link
    ADD CONSTRAINT invoice_view_link_token_hash_unique UNIQUE (token_hash);

ALTER TABLE ONLY test.outbound_email
    ADD CONSTRAINT outbound_email_pk PRIMARY KEY (id);

ALTER TABLE ONLY live.outbound_email
    ADD CONSTRAINT outbound_email_pk PRIMARY KEY (id);

ALTER TABLE ONLY test.payment_attempt
    ADD CONSTRAINT payment_attempt_environment_id_unique UNIQUE (organization_id, id);

ALTER TABLE ONLY live.payment_attempt
    ADD CONSTRAINT payment_attempt_environment_id_unique UNIQUE (organization_id, id);

ALTER TABLE ONLY test.payment_attempt
    ADD CONSTRAINT payment_attempt_pk PRIMARY KEY (id);

ALTER TABLE ONLY live.payment_attempt
    ADD CONSTRAINT payment_attempt_pk PRIMARY KEY (id);

ALTER TABLE ONLY test.payment_cancellation
    ADD CONSTRAINT payment_cancellation_pk PRIMARY KEY (id);

ALTER TABLE ONLY live.payment_cancellation
    ADD CONSTRAINT payment_cancellation_pk PRIMARY KEY (id);

ALTER TABLE ONLY test.payment_method_registration
    ADD CONSTRAINT payment_method_registration_pk PRIMARY KEY (id);

ALTER TABLE ONLY live.payment_method_registration
    ADD CONSTRAINT payment_method_registration_pk PRIMARY KEY (id);

ALTER TABLE ONLY test.payment_method_registration
    ADD CONSTRAINT payment_method_registration_token_hash_unique UNIQUE (token_hash);

ALTER TABLE ONLY live.payment_method_registration
    ADD CONSTRAINT payment_method_registration_token_hash_unique UNIQUE (token_hash);

ALTER TABLE ONLY test.payment_reconciliation_mismatch
    ADD CONSTRAINT payment_reconciliation_mismatch_kind_order_transaction_unique UNIQUE NULLS NOT DISTINCT (organization_id, mode, kind, order_id, transaction_key);

ALTER TABLE ONLY live.payment_reconciliation_mismatch
    ADD CONSTRAINT payment_reconciliation_mismatch_kind_order_transaction_unique UNIQUE NULLS NOT DISTINCT (organization_id, mode, kind, order_id, transaction_key);

ALTER TABLE ONLY test.payment_reconciliation_mismatch
    ADD CONSTRAINT payment_reconciliation_mismatch_pk PRIMARY KEY (id);

ALTER TABLE ONLY live.payment_reconciliation_mismatch
    ADD CONSTRAINT payment_reconciliation_mismatch_pk PRIMARY KEY (id);

ALTER TABLE ONLY test.payment_setting
    ADD CONSTRAINT payment_setting_pk PRIMARY KEY (organization_id);

ALTER TABLE ONLY live.payment_setting
    ADD CONSTRAINT payment_setting_pk PRIMARY KEY (organization_id);

ALTER TABLE ONLY test.price_plan
    ADD CONSTRAINT price_plan_pk PRIMARY KEY (id);

ALTER TABLE ONLY live.price_plan
    ADD CONSTRAINT price_plan_pk PRIMARY KEY (id);

ALTER TABLE ONLY test.price_rate
    ADD CONSTRAINT price_rate_pk PRIMARY KEY (id);

ALTER TABLE ONLY live.price_rate
    ADD CONSTRAINT price_rate_pk PRIMARY KEY (id);

ALTER TABLE ONLY test.toss_payments_credential_history
    ADD CONSTRAINT toss_payments_credential_history_pk PRIMARY KEY (history_id);

ALTER TABLE ONLY live.toss_payments_credential_history
    ADD CONSTRAINT toss_payments_credential_history_pk PRIMARY KEY (history_id);

ALTER TABLE ONLY test.toss_payments_credential
    ADD CONSTRAINT toss_payments_credential_pk PRIMARY KEY (organization_id, mode);

ALTER TABLE ONLY live.toss_payments_credential
    ADD CONSTRAINT toss_payments_credential_pk PRIMARY KEY (organization_id, mode);

ALTER TABLE ONLY test.webhook_delivery_attempt
    ADD CONSTRAINT webhook_delivery_attempt_pk PRIMARY KEY (id);

ALTER TABLE ONLY live.webhook_delivery_attempt
    ADD CONSTRAINT webhook_delivery_attempt_pk PRIMARY KEY (id);

ALTER TABLE ONLY test.webhook_delivery
    ADD CONSTRAINT webhook_delivery_environment_id_unique UNIQUE (organization_id, id);

ALTER TABLE ONLY live.webhook_delivery
    ADD CONSTRAINT webhook_delivery_environment_id_unique UNIQUE (organization_id, id);

ALTER TABLE ONLY test.webhook_delivery
    ADD CONSTRAINT webhook_delivery_pk PRIMARY KEY (id);

ALTER TABLE ONLY live.webhook_delivery
    ADD CONSTRAINT webhook_delivery_pk PRIMARY KEY (id);

ALTER TABLE ONLY test.webhook_delivery
    ADD CONSTRAINT webhook_delivery_webhook_endpoint_webhook_event_unique UNIQUE (webhook_endpoint_id, webhook_event_id);

ALTER TABLE ONLY live.webhook_delivery
    ADD CONSTRAINT webhook_delivery_webhook_endpoint_webhook_event_unique UNIQUE (webhook_endpoint_id, webhook_event_id);

ALTER TABLE ONLY test.webhook_endpoint
    ADD CONSTRAINT webhook_endpoint_environment_id_unique UNIQUE (organization_id, id);

ALTER TABLE ONLY live.webhook_endpoint
    ADD CONSTRAINT webhook_endpoint_environment_id_unique UNIQUE (organization_id, id);

ALTER TABLE ONLY test.webhook_endpoint
    ADD CONSTRAINT webhook_endpoint_pk PRIMARY KEY (id);

ALTER TABLE ONLY live.webhook_endpoint
    ADD CONSTRAINT webhook_endpoint_pk PRIMARY KEY (id);

ALTER TABLE ONLY test.webhook_event
    ADD CONSTRAINT webhook_event_environment_id_unique UNIQUE (organization_id, id);

ALTER TABLE ONLY live.webhook_event
    ADD CONSTRAINT webhook_event_environment_id_unique UNIQUE (organization_id, id);

ALTER TABLE ONLY test.webhook_event
    ADD CONSTRAINT webhook_event_pk PRIMARY KEY (id);

ALTER TABLE ONLY live.webhook_event
    ADD CONSTRAINT webhook_event_pk PRIMARY KEY (id);

CREATE INDEX billable_metric_period_organization_index ON test.billable_metric_period USING btree (organization_id, billable_metric_code);

CREATE INDEX billable_metric_period_organization_index ON live.billable_metric_period USING btree (organization_id, billable_metric_code);

CREATE INDEX billing_key_history_organization_index ON test.billing_key_history USING btree (organization_id, mode, customer_id);

CREATE INDEX billing_key_history_organization_index ON live.billing_key_history USING btree (organization_id, mode, customer_id);

CREATE UNIQUE INDEX customer_environment_external_id_unique ON test.customer USING btree (organization_id, external_id) WHERE (external_id IS NOT NULL);

CREATE UNIQUE INDEX customer_environment_external_id_unique ON live.customer USING btree (organization_id, external_id) WHERE (external_id IS NOT NULL);

CREATE INDEX event_rejection_customer_index ON test.event_rejection USING btree (organization_id, customer_id, recorded_at DESC);

CREATE INDEX event_rejection_customer_index ON live.event_rejection USING btree (organization_id, customer_id, recorded_at DESC);

CREATE INDEX event_rejection_search_index ON test.event_rejection USING btree (organization_id, recorded_at DESC, id DESC);

CREATE INDEX event_rejection_search_index ON live.event_rejection USING btree (organization_id, recorded_at DESC, id DESC);

CREATE INDEX invoice_environment_lookup ON test.invoice USING btree (organization_id);

CREATE INDEX invoice_environment_lookup ON live.invoice USING btree (organization_id);

CREATE UNIQUE INDEX invoice_line_invoice_billable_metric_dimension_values_unique ON test.invoice_line USING btree (organization_id, invoice_id, billable_metric_code, dimension_values, line_type, tier_index) WHERE (billable_metric_code IS NOT NULL);

CREATE UNIQUE INDEX invoice_line_invoice_billable_metric_dimension_values_unique ON live.invoice_line USING btree (organization_id, invoice_id, billable_metric_code, dimension_values, line_type, tier_index) WHERE (billable_metric_code IS NOT NULL);

CREATE UNIQUE INDEX invoice_line_invoice_surcharge_unique ON test.invoice_line USING btree (organization_id, invoice_id, line_type) WHERE (billable_metric_code IS NULL);

CREATE UNIQUE INDEX invoice_line_invoice_surcharge_unique ON live.invoice_line USING btree (organization_id, invoice_id, line_type) WHERE (billable_metric_code IS NULL);

CREATE INDEX invoice_view_link_organization_invoice_created_index ON test.invoice_view_link USING btree (organization_id, invoice_id, created_at DESC, id DESC);

CREATE INDEX invoice_view_link_organization_invoice_created_index ON live.invoice_view_link USING btree (organization_id, invoice_id, created_at DESC, id DESC);

CREATE INDEX outbound_email_environment_lookup ON test.outbound_email USING btree (organization_id);

CREATE INDEX outbound_email_environment_lookup ON live.outbound_email USING btree (organization_id);

CREATE INDEX payment_attempt_environment_lookup ON test.payment_attempt USING btree (organization_id);

CREATE INDEX payment_attempt_environment_lookup ON live.payment_attempt USING btree (organization_id);

CREATE INDEX payment_attempt_organization_invoice_mode_requested_at ON test.payment_attempt USING btree (organization_id, invoice_id, mode, requested_at DESC, id DESC);

CREATE INDEX payment_attempt_organization_invoice_mode_requested_at ON live.payment_attempt USING btree (organization_id, invoice_id, mode, requested_at DESC, id DESC);

CREATE UNIQUE INDEX payment_attempt_organization_invoice_mode_unique ON test.payment_attempt USING btree (organization_id, invoice_id, mode) WHERE ((status)::text = ANY (ARRAY[('pending'::character varying)::text, ('done'::character varying)::text]));

CREATE UNIQUE INDEX payment_attempt_organization_invoice_mode_unique ON live.payment_attempt USING btree (organization_id, invoice_id, mode) WHERE ((status)::text = ANY (ARRAY[('pending'::character varying)::text, ('done'::character varying)::text]));

CREATE INDEX payment_attempt_organization_mode_done_completed_at ON test.payment_attempt USING btree (organization_id, mode, completed_at) WHERE ((status)::text = 'done'::text);

CREATE INDEX payment_attempt_organization_mode_done_completed_at ON live.payment_attempt USING btree (organization_id, mode, completed_at) WHERE ((status)::text = 'done'::text);

CREATE INDEX payment_attempt_pending_requested_at ON test.payment_attempt USING btree (requested_at) WHERE ((status)::text = 'pending'::text);

CREATE INDEX payment_attempt_pending_requested_at ON live.payment_attempt USING btree (requested_at) WHERE ((status)::text = 'pending'::text);

CREATE INDEX payment_cancellation_organization_mode_done_completed_at ON test.payment_cancellation USING btree (organization_id, mode, completed_at) WHERE ((status)::text = 'done'::text);

CREATE INDEX payment_cancellation_organization_mode_done_completed_at ON live.payment_cancellation USING btree (organization_id, mode, completed_at) WHERE ((status)::text = 'done'::text);

CREATE INDEX payment_cancellation_organization_payment_attempt_done ON test.payment_cancellation USING btree (organization_id, payment_attempt_id) WHERE ((status)::text = 'done'::text);

CREATE INDEX payment_cancellation_organization_payment_attempt_done ON live.payment_cancellation USING btree (organization_id, payment_attempt_id) WHERE ((status)::text = 'done'::text);

CREATE UNIQUE INDEX payment_cancellation_payment_attempt_pending_unique ON test.payment_cancellation USING btree (payment_attempt_id) WHERE ((status)::text = 'pending'::text);

CREATE UNIQUE INDEX payment_cancellation_payment_attempt_pending_unique ON live.payment_cancellation USING btree (payment_attempt_id) WHERE ((status)::text = 'pending'::text);

CREATE INDEX payment_reconciliation_mismatch_environment_lookup ON test.payment_reconciliation_mismatch USING btree (organization_id);

CREATE INDEX payment_reconciliation_mismatch_environment_lookup ON live.payment_reconciliation_mismatch USING btree (organization_id);

CREATE UNIQUE INDEX price_plan_customer_version_unique ON test.price_plan USING btree (organization_id, customer_id, effective_from) WHERE ((customer_id IS NOT NULL) AND (archived_at IS NULL));

CREATE UNIQUE INDEX price_plan_customer_version_unique ON live.price_plan USING btree (organization_id, customer_id, effective_from) WHERE ((customer_id IS NOT NULL) AND (archived_at IS NULL));

CREATE UNIQUE INDEX price_plan_organization_version_unique ON test.price_plan USING btree (organization_id, effective_from) WHERE ((customer_id IS NULL) AND (archived_at IS NULL));

CREATE UNIQUE INDEX price_plan_organization_version_unique ON live.price_plan USING btree (organization_id, effective_from) WHERE ((customer_id IS NULL) AND (archived_at IS NULL));

CREATE UNIQUE INDEX price_rate_customer_version_unique ON test.price_rate USING btree (organization_id, billable_metric_code, dimension_values, customer_id, effective_from) WHERE ((customer_id IS NOT NULL) AND (archived_at IS NULL));

CREATE UNIQUE INDEX price_rate_customer_version_unique ON live.price_rate USING btree (organization_id, billable_metric_code, dimension_values, customer_id, effective_from) WHERE ((customer_id IS NOT NULL) AND (archived_at IS NULL));

CREATE UNIQUE INDEX price_rate_organization_version_unique ON test.price_rate USING btree (organization_id, billable_metric_code, dimension_values, effective_from) WHERE ((customer_id IS NULL) AND (archived_at IS NULL));

CREATE UNIQUE INDEX price_rate_organization_version_unique ON live.price_rate USING btree (organization_id, billable_metric_code, dimension_values, effective_from) WHERE ((customer_id IS NULL) AND (archived_at IS NULL));

CREATE INDEX toss_payments_credential_history_organization_index ON test.toss_payments_credential_history USING btree (organization_id, mode, updated_at);

CREATE INDEX toss_payments_credential_history_organization_index ON live.toss_payments_credential_history USING btree (organization_id, mode, updated_at);

CREATE INDEX webhook_delivery_attempt_webhook_delivery_lookup ON test.webhook_delivery_attempt USING btree (organization_id, webhook_delivery_id);

CREATE INDEX webhook_delivery_attempt_webhook_delivery_lookup ON live.webhook_delivery_attempt USING btree (organization_id, webhook_delivery_id);

CREATE INDEX webhook_delivery_endpoint_created_at ON test.webhook_delivery USING btree (organization_id, webhook_endpoint_id, created_at DESC, id DESC);

CREATE INDEX webhook_delivery_endpoint_created_at ON live.webhook_delivery USING btree (organization_id, webhook_endpoint_id, created_at DESC, id DESC);

CREATE INDEX webhook_delivery_pending_next_attempt_at ON test.webhook_delivery USING btree (next_attempt_at) WHERE ((status)::text = 'pending'::text);

CREATE INDEX webhook_delivery_pending_next_attempt_at ON live.webhook_delivery USING btree (next_attempt_at) WHERE ((status)::text = 'pending'::text);

CREATE INDEX webhook_endpoint_environment_lookup ON test.webhook_endpoint USING btree (organization_id);

CREATE INDEX webhook_endpoint_environment_lookup ON live.webhook_endpoint USING btree (organization_id);

CREATE INDEX webhook_event_environment_lookup ON test.webhook_event USING btree (organization_id);

CREATE INDEX webhook_event_environment_lookup ON live.webhook_event USING btree (organization_id);

CREATE TABLE test.supplier (
    organization_id uuid PRIMARY KEY,
    business_registration_number varchar(10) CHECK (business_registration_number IS NULL OR business_registration_number ~ '^[0-9]{10}$'),
    representative_name varchar(255) COLLATE shared.korean,
    business_address varchar(1000)
);

CREATE TABLE live.supplier (
    organization_id uuid PRIMARY KEY,
    business_registration_number varchar(10) CHECK (business_registration_number IS NULL OR business_registration_number ~ '^[0-9]{10}$'),
    representative_name varchar(255) COLLATE shared.korean,
    business_address varchar(1000)
);

CREATE INDEX price_rate_organization_index ON test.price_rate USING btree (organization_id, billable_metric_code);

CREATE INDEX price_rate_organization_index ON live.price_rate USING btree (organization_id, billable_metric_code);
