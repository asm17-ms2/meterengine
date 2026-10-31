ALTER TABLE organization
  ADD COLUMN status VARCHAR NOT NULL DEFAULT 'active'
    CONSTRAINT organization_status_check
    CHECK (status IN ('pending', 'active', 'rejected', 'suspended')),
  ADD COLUMN created_at TIMESTAMPTZ NOT NULL DEFAULT now();

CREATE TABLE user_account (
  id            UUID NOT NULL,
  email         VARCHAR NOT NULL,
  password_hash VARCHAR NOT NULL,
  name          VARCHAR COLLATE korean NOT NULL,
  role          VARCHAR NOT NULL
                  CONSTRAINT user_account_role_check
                  CHECK (role IN ('member', 'operator')),
  created_at    TIMESTAMPTZ NOT NULL,

  CONSTRAINT user_account_pk PRIMARY KEY (id)
);

CREATE UNIQUE INDEX user_account_email_unique ON user_account (lower(email));

CREATE TABLE organization_member (
  organization_id UUID NOT NULL,
  user_account_id UUID NOT NULL,
  created_at      TIMESTAMPTZ NOT NULL,

  CONSTRAINT organization_member_pk PRIMARY KEY (organization_id, user_account_id),

  CONSTRAINT organization_member_organization_fk
    FOREIGN KEY (organization_id) REFERENCES organization (id),

  CONSTRAINT organization_member_user_account_fk
    FOREIGN KEY (user_account_id) REFERENCES user_account (id)
);

CREATE TABLE user_session (
  id              UUID NOT NULL,
  user_account_id UUID NOT NULL,
  token_hash      BYTEA NOT NULL
                    CONSTRAINT user_session_token_hash_check
                    CHECK (octet_length(token_hash) = 32),
  organization_id UUID,
  created_at      TIMESTAMPTZ NOT NULL,
  expires_at      TIMESTAMPTZ NOT NULL,
  revoked_at      TIMESTAMPTZ,

  CONSTRAINT user_session_pk PRIMARY KEY (id),

  CONSTRAINT user_session_token_hash_unique UNIQUE (token_hash),

  CONSTRAINT user_session_user_account_fk
    FOREIGN KEY (user_account_id) REFERENCES user_account (id),

  CONSTRAINT user_session_organization_member_fk
    FOREIGN KEY (organization_id, user_account_id)
    REFERENCES organization_member (organization_id, user_account_id)
);

CREATE TABLE consent (
  id               UUID NOT NULL,
  user_account_id  UUID NOT NULL,
  document         VARCHAR NOT NULL
                     CONSTRAINT consent_document_check
                     CHECK (document IN ('terms_of_service', 'privacy_policy')),
  document_version VARCHAR NOT NULL,
  agreed_at        TIMESTAMPTZ NOT NULL,

  CONSTRAINT consent_pk PRIMARY KEY (id),

  CONSTRAINT consent_user_account_fk
    FOREIGN KEY (user_account_id) REFERENCES user_account (id)
);

CREATE FUNCTION consent_block_mutation() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'consent is append-only: UPDATE/DELETE/TRUNCATE is blocked';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER consent_block_update
  BEFORE UPDATE ON consent
  FOR EACH ROW EXECUTE FUNCTION consent_block_mutation();

CREATE TRIGGER consent_block_delete
  BEFORE DELETE ON consent
  FOR EACH ROW EXECUTE FUNCTION consent_block_mutation();

CREATE TRIGGER consent_block_truncate
  BEFORE TRUNCATE ON consent
  FOR EACH STATEMENT EXECUTE FUNCTION consent_block_mutation();

CREATE TABLE api_key (
  id                    UUID NOT NULL,
  organization_id       UUID NOT NULL,
  mode                  VARCHAR NOT NULL
                          CONSTRAINT api_key_mode_check
                          CHECK (mode IN ('live', 'test')),
  label                 VARCHAR COLLATE korean NOT NULL,
  key_hash              BYTEA NOT NULL
                          CONSTRAINT api_key_key_hash_check
                          CHECK (octet_length(key_hash) = 32),
  last_four             VARCHAR NOT NULL
                          CONSTRAINT api_key_last_four_check
                          CHECK (char_length(last_four) = 4),
  scopes                VARCHAR[] NOT NULL
                          CONSTRAINT api_key_scopes_check
                          CHECK (cardinality(scopes) > 0
                                 AND scopes <@ ARRAY['ingest', 'read', 'admin']::VARCHAR[]),
  rate_limit_per_minute INTEGER
                          CONSTRAINT api_key_rate_limit_per_minute_check
                          CHECK (rate_limit_per_minute > 0),
  created_at            TIMESTAMPTZ NOT NULL,
  expires_at            TIMESTAMPTZ,
  revoked_at            TIMESTAMPTZ,

  CONSTRAINT api_key_pk PRIMARY KEY (id),

  CONSTRAINT api_key_key_hash_unique UNIQUE (key_hash),

  CONSTRAINT api_key_organization_fk
    FOREIGN KEY (organization_id) REFERENCES organization (id)
);
