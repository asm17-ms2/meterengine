ALTER TABLE invoice ADD COLUMN display_number BIGINT GENERATED ALWAYS AS IDENTITY
  CONSTRAINT invoice_display_number_unique UNIQUE;

CREATE TABLE invoice_view_link (
  id UUID NOT NULL CONSTRAINT invoice_view_link_pk PRIMARY KEY,
  organization_id UUID NOT NULL,
  invoice_id UUID NOT NULL,
  token_hash BYTEA NOT NULL
    CONSTRAINT invoice_view_link_token_hash_unique UNIQUE
    CONSTRAINT invoice_view_link_token_hash_check CHECK (octet_length(token_hash) = 32),
  mode VARCHAR NOT NULL
    CONSTRAINT invoice_view_link_mode_check CHECK (mode IN ('test', 'live')),
  created_at TIMESTAMPTZ NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL
    CONSTRAINT invoice_view_link_expires_at_check CHECK (expires_at > created_at),
  revoked_at TIMESTAMPTZ,
  CONSTRAINT invoice_view_link_invoice_same_organization_fk
    FOREIGN KEY (organization_id, invoice_id) REFERENCES invoice (organization_id, id)
);

CREATE INDEX invoice_view_link_organization_invoice_created_index
  ON invoice_view_link(organization_id, invoice_id, created_at DESC, id DESC);

ALTER TABLE audit_log DROP CONSTRAINT audit_log_actor_type_check;
ALTER TABLE audit_log ADD CONSTRAINT audit_log_actor_type_check
  CHECK (actor_type IN ('user_account', 'api_key', 'system', 'anonymous', 'invoice_view_link'));
