REVOKE ALL ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON DATABASE meterengine_app FROM PUBLIC;
ALTER DEFAULT PRIVILEGES REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC;

CREATE SCHEMA IF NOT EXISTS shared;

CREATE COLLATION shared.korean (provider = icu, locale = 'ko-KR');

CREATE TABLE shared.api_key (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    mode character varying NOT NULL,
    label character varying NOT NULL COLLATE shared.korean,
    key_hash bytea NOT NULL,
    last_four character varying NOT NULL,
    scopes character varying[] NOT NULL,
    rate_limit_per_minute integer,
    created_at timestamp with time zone NOT NULL,
    expires_at timestamp with time zone,
    revoked_at timestamp with time zone,
    environment_id uuid,
    CONSTRAINT api_key_key_hash_check CHECK ((octet_length(key_hash) = 32)),
    CONSTRAINT api_key_last_four_check CHECK ((char_length((last_four)::text) = 4)),
    CONSTRAINT api_key_mode_check CHECK (((mode)::text = ANY ((ARRAY['live'::character varying, 'test'::character varying])::text[]))),
    CONSTRAINT api_key_rate_limit_per_minute_check CHECK ((rate_limit_per_minute > 0)),
    CONSTRAINT api_key_scopes_check CHECK (((cardinality(scopes) > 0) AND (scopes <@ ARRAY['ingest'::character varying, 'read'::character varying, 'admin'::character varying])))
);

CREATE TABLE shared.audit_log (
    id uuid NOT NULL,
    occurred_at timestamp with time zone NOT NULL,
    organization_id uuid,
    actor_type character varying NOT NULL,
    actor_id uuid,
    action character varying NOT NULL,
    target_type character varying,
    target_id character varying,
    detail jsonb DEFAULT '{}'::jsonb NOT NULL,
    environment_id uuid,
    CONSTRAINT audit_log_action_check CHECK (((action)::text ~ '^[a-z]+(_[a-z]+)*$'::text)),
    CONSTRAINT audit_log_actor_id_check CHECK ((((actor_type)::text = ANY ((ARRAY['system'::character varying, 'anonymous'::character varying])::text[])) = (actor_id IS NULL))),
    CONSTRAINT audit_log_actor_type_check CHECK (((actor_type)::text = ANY ((ARRAY['user_account'::character varying, 'api_key'::character varying, 'system'::character varying, 'anonymous'::character varying, 'invoice_view_link'::character varying])::text[])))
);

CREATE TABLE shared.auth_request_rate_limit (
    scope character varying(32) NOT NULL,
    identity_hash bytea NOT NULL,
    attempts bigint NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    CONSTRAINT auth_request_rate_limit_attempts_check CHECK ((attempts > 0)),
    CONSTRAINT auth_request_rate_limit_identity_hash_check CHECK ((octet_length(identity_hash) = 32)),
    CONSTRAINT auth_request_rate_limit_scope_check CHECK (((scope)::text = ANY ((ARRAY['login_ip'::character varying, 'login_account'::character varying, 'signup_ip'::character varying, 'signup_account'::character varying])::text[])))
);

CREATE TABLE shared.consent (
    id uuid NOT NULL,
    user_account_id uuid NOT NULL,
    document character varying NOT NULL,
    document_version character varying NOT NULL,
    agreed_at timestamp with time zone NOT NULL,
    CONSTRAINT consent_document_check CHECK (((document)::text = ANY ((ARRAY['terms_of_service'::character varying, 'privacy_policy'::character varying])::text[])))
);

CREATE TABLE shared.organization (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    name character varying NOT NULL COLLATE shared.korean,
    status character varying DEFAULT 'active'::character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT organization_status_check CHECK (((status)::text = ANY ((ARRAY['pending'::character varying, 'active'::character varying, 'rejected'::character varying, 'suspended'::character varying])::text[])))
);

CREATE TABLE shared.organization_environment (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    organization_id uuid NOT NULL,
    mode character varying NOT NULL,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT organization_environment_mode_check CHECK (((mode)::text = ANY ((ARRAY['test'::character varying, 'live'::character varying])::text[]))),
    CONSTRAINT organization_environment_nonlegacy_id_check CHECK ((id <> '00000000-0000-0000-0000-000000000000'::uuid))
);

CREATE TABLE shared.organization_invitation (
    id uuid NOT NULL,
    organization_id uuid NOT NULL,
    email character varying NOT NULL,
    token_hash bytea NOT NULL,
    created_at timestamp with time zone NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    accepted_at timestamp with time zone,
    revoked_at timestamp with time zone,
    scopes character varying[] DEFAULT ARRAY['admin'::character varying] NOT NULL,
    CONSTRAINT organization_invitation_expires_at_check CHECK ((expires_at > created_at)),
    CONSTRAINT organization_invitation_resolution_check CHECK (((accepted_at IS NULL) OR (revoked_at IS NULL))),
    CONSTRAINT organization_invitation_scopes_check CHECK (((cardinality(scopes) > 0) AND (array_position(scopes, NULL::character varying) IS NULL) AND (scopes <@ ARRAY['ingest'::character varying, 'read'::character varying, 'admin'::character varying]))),
    CONSTRAINT organization_invitation_token_hash_check CHECK ((octet_length(token_hash) = 32))
);

CREATE TABLE shared.organization_member (
    organization_id uuid NOT NULL,
    user_account_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    role character varying DEFAULT 'admin'::character varying NOT NULL,
    revoked_at timestamp with time zone,
    scopes character varying[] DEFAULT ARRAY['admin'::character varying] NOT NULL,
    CONSTRAINT organization_member_role_check CHECK (((role)::text = ANY ((ARRAY['owner'::character varying, 'admin'::character varying, 'member'::character varying])::text[]))),
    CONSTRAINT organization_member_role_scopes_check CHECK (((((role)::text = ANY ((ARRAY['owner'::character varying, 'admin'::character varying])::text[])) AND ('admin'::text = ANY ((scopes)::text[]))) OR (((role)::text = 'member'::text) AND (NOT ('admin'::text = ANY ((scopes)::text[])))))),
    CONSTRAINT organization_member_scopes_check CHECK (((cardinality(scopes) > 0) AND (array_position(scopes, NULL::character varying) IS NULL) AND (scopes <@ ARRAY['ingest'::character varying, 'read'::character varying, 'admin'::character varying])))
);

CREATE TABLE shared.scheduled_job_run (
    id uuid NOT NULL,
    scope character varying NOT NULL CHECK (scope IN ('test','live')),
    job_name character varying NOT NULL,
    scheduled_at timestamp with time zone NOT NULL,
    status character varying NOT NULL,
    processed_count integer DEFAULT 0 NOT NULL,
    skipped_count integer DEFAULT 0 NOT NULL,
    failed_count integer DEFAULT 0 NOT NULL,
    error_summary character varying,
    started_at timestamp with time zone NOT NULL,
    finished_at timestamp with time zone,
    CONSTRAINT scheduled_job_run_status_check CHECK (((status)::text = ANY ((ARRAY['running'::character varying, 'succeeded'::character varying, 'failed'::character varying])::text[]))),
    CONSTRAINT scheduled_job_run_status_finished_at_check CHECK ((((status)::text = 'running'::text) = (finished_at IS NULL)))
);

CREATE TABLE shared.user_account (
    id uuid NOT NULL,
    email character varying NOT NULL,
    password_hash character varying NOT NULL,
    name character varying NOT NULL COLLATE shared.korean,
    role character varying NOT NULL,
    created_at timestamp with time zone NOT NULL,
    consent_required boolean DEFAULT false NOT NULL,
    CONSTRAINT user_account_role_check CHECK (((role)::text = ANY ((ARRAY['member'::character varying, 'operator'::character varying])::text[])))
);

CREATE TABLE shared.user_session (
    id uuid NOT NULL,
    user_account_id uuid NOT NULL,
    token_hash bytea NOT NULL,
    organization_id uuid,
    created_at timestamp with time zone NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone,
    CONSTRAINT user_session_token_hash_check CHECK ((octet_length(token_hash) = 32))
);

ALTER TABLE ONLY shared.api_key
    ADD CONSTRAINT api_key_key_hash_unique UNIQUE (key_hash);

ALTER TABLE ONLY shared.api_key
    ADD CONSTRAINT api_key_pk PRIMARY KEY (id);

ALTER TABLE ONLY shared.audit_log
    ADD CONSTRAINT audit_log_pk PRIMARY KEY (id);

ALTER TABLE ONLY shared.auth_request_rate_limit
    ADD CONSTRAINT auth_request_rate_limit_pk PRIMARY KEY (scope, identity_hash);

ALTER TABLE ONLY shared.consent
    ADD CONSTRAINT consent_pk PRIMARY KEY (id);

ALTER TABLE ONLY shared.organization_environment
    ADD CONSTRAINT organization_environment_key_mode_unique UNIQUE (organization_id, id, mode);

ALTER TABLE ONLY shared.organization_environment
    ADD CONSTRAINT organization_environment_mode_unique UNIQUE (organization_id, mode);

ALTER TABLE ONLY shared.organization_environment
    ADD CONSTRAINT organization_environment_org_id_unique UNIQUE (organization_id, id);

ALTER TABLE ONLY shared.organization_environment
    ADD CONSTRAINT organization_environment_pk PRIMARY KEY (id);

ALTER TABLE ONLY shared.organization_invitation
    ADD CONSTRAINT organization_invitation_pk PRIMARY KEY (id);

ALTER TABLE ONLY shared.organization_invitation
    ADD CONSTRAINT organization_invitation_token_hash_unique UNIQUE (token_hash);

ALTER TABLE ONLY shared.organization_member
    ADD CONSTRAINT organization_member_pk PRIMARY KEY (organization_id, user_account_id);

ALTER TABLE ONLY shared.organization
    ADD CONSTRAINT organization_pk PRIMARY KEY (id);

ALTER TABLE ONLY shared.scheduled_job_run
    ADD CONSTRAINT scheduled_job_run_job_name_scheduled_at_unique UNIQUE (job_name, scope, scheduled_at);

ALTER TABLE ONLY shared.scheduled_job_run
    ADD CONSTRAINT scheduled_job_run_pk PRIMARY KEY (id);

ALTER TABLE ONLY shared.user_account
    ADD CONSTRAINT user_account_pk PRIMARY KEY (id);

ALTER TABLE ONLY shared.user_session
    ADD CONSTRAINT user_session_pk PRIMARY KEY (id);

ALTER TABLE ONLY shared.user_session
    ADD CONSTRAINT user_session_token_hash_unique UNIQUE (token_hash);

CREATE INDEX audit_log_occurred_id_index ON shared.audit_log USING btree (occurred_at, id);

CREATE INDEX audit_log_organization_id_occurred_at_index ON shared.audit_log USING btree (organization_id, occurred_at);

CREATE INDEX auth_request_rate_limit_expiry_idx ON shared.auth_request_rate_limit USING btree (expires_at);

CREATE INDEX organization_invitation_organization_created_index ON shared.organization_invitation USING btree (organization_id, created_at, id);

CREATE INDEX scheduled_job_run_job_name_started_at ON shared.scheduled_job_run USING btree (job_name, scope, started_at DESC);

CREATE UNIQUE INDEX user_account_email_unique ON shared.user_account USING btree (lower((email)::text));

CREATE TABLE shared.legacy_import (
    singleton boolean PRIMARY KEY DEFAULT true CHECK (singleton),
    bundle_sha256 varchar(64) NOT NULL UNIQUE CHECK (bundle_sha256 ~ '^[0-9a-f]{64}$'),
    source_fingerprint_sha256 varchar(64) NOT NULL CHECK (source_fingerprint_sha256 ~ '^[0-9a-f]{64}$'),
    target_fingerprint_sha256 varchar(64) NOT NULL CHECK (target_fingerprint_sha256 ~ '^[0-9a-f]{64}$'),
    verification_sha256 varchar(64) NOT NULL CHECK (verification_sha256 ~ '^[0-9a-f]{64}$'),
    imported_at timestamptz NOT NULL DEFAULT clock_timestamp()
);
