CREATE TABLE audit_log (
  id              UUID NOT NULL,
  occurred_at     TIMESTAMPTZ NOT NULL,
  organization_id UUID,
  actor_type      VARCHAR NOT NULL
                    CONSTRAINT audit_log_actor_type_check
                    CHECK (actor_type IN ('user_account', 'api_key', 'system')),
  actor_id        UUID,
  action          VARCHAR NOT NULL
                    CONSTRAINT audit_log_action_check
                    CHECK (action ~ '^[a-z]+(_[a-z]+)*$'),
  target_type     VARCHAR,
  target_id       VARCHAR,
  detail          JSONB NOT NULL DEFAULT '{}',

  CONSTRAINT audit_log_pk PRIMARY KEY (id),

  CONSTRAINT audit_log_organization_fk
    FOREIGN KEY (organization_id) REFERENCES organization (id),

  CONSTRAINT audit_log_actor_id_check
    CHECK ((actor_type = 'system') = (actor_id IS NULL))
);

CREATE INDEX audit_log_organization_id_occurred_at_index
  ON audit_log (organization_id, occurred_at);

CREATE FUNCTION audit_log_block_mutation() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'audit_log is append-only: UPDATE/DELETE/TRUNCATE is blocked';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_log_block_update
  BEFORE UPDATE ON audit_log
  FOR EACH ROW EXECUTE FUNCTION audit_log_block_mutation();

CREATE TRIGGER audit_log_block_delete
  BEFORE DELETE ON audit_log
  FOR EACH ROW EXECUTE FUNCTION audit_log_block_mutation();

CREATE TRIGGER audit_log_block_truncate
  BEFORE TRUNCATE ON audit_log
  FOR EACH STATEMENT EXECUTE FUNCTION audit_log_block_mutation();
