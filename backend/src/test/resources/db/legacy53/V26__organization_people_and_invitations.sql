ALTER TABLE organization_member ADD COLUMN role VARCHAR NOT NULL DEFAULT 'admin'
  CONSTRAINT organization_member_role_check CHECK (role IN ('owner', 'admin'));

WITH first_members AS (
  SELECT organization_id, user_account_id,
         row_number() OVER (PARTITION BY organization_id ORDER BY created_at, user_account_id) AS position
  FROM organization_member
)
UPDATE organization_member member SET role = 'owner'
FROM first_members first_member
WHERE first_member.position = 1
  AND member.organization_id = first_member.organization_id
  AND member.user_account_id = first_member.user_account_id;

ALTER TABLE user_account ADD COLUMN consent_required BOOLEAN NOT NULL DEFAULT false;

CREATE TABLE organization_invitation (
  id UUID NOT NULL CONSTRAINT organization_invitation_pk PRIMARY KEY,
  organization_id UUID NOT NULL CONSTRAINT organization_invitation_organization_fk REFERENCES organization(id),
  email VARCHAR NOT NULL,
  token_hash BYTEA NOT NULL
    CONSTRAINT organization_invitation_token_hash_unique UNIQUE
    CONSTRAINT organization_invitation_token_hash_check CHECK (octet_length(token_hash) = 32),
  created_at TIMESTAMPTZ NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL
    CONSTRAINT organization_invitation_expires_at_check CHECK (expires_at > created_at),
  accepted_at TIMESTAMPTZ,
  revoked_at TIMESTAMPTZ,
  CONSTRAINT organization_invitation_resolution_check CHECK (accepted_at IS NULL OR revoked_at IS NULL)
);

CREATE INDEX organization_invitation_organization_created_index
  ON organization_invitation(organization_id, created_at, id);
CREATE INDEX audit_log_occurred_id_index ON audit_log(occurred_at, id);

ALTER TABLE organization_member ADD COLUMN revoked_at TIMESTAMPTZ;
ALTER TABLE audit_log DROP CONSTRAINT audit_log_organization_fk;
ALTER TABLE audit_log DROP CONSTRAINT audit_log_actor_type_check;
ALTER TABLE audit_log DROP CONSTRAINT audit_log_actor_id_check;
ALTER TABLE audit_log ADD CONSTRAINT audit_log_actor_type_check
  CHECK (actor_type IN ('user_account', 'api_key', 'system', 'anonymous'));
ALTER TABLE audit_log ADD CONSTRAINT audit_log_actor_id_check
  CHECK ((actor_type IN ('system', 'anonymous')) = (actor_id IS NULL));
