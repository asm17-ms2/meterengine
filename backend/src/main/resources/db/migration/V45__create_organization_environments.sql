CREATE TABLE organization_environment (
  id UUID CONSTRAINT organization_environment_pk PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id UUID NOT NULL CONSTRAINT organization_environment_organization_fk REFERENCES organization(id),
  mode VARCHAR NOT NULL CHECK (mode IN ('test', 'live')),
  CONSTRAINT organization_environment_nonlegacy_id_check CHECK (id <> '00000000-0000-0000-0000-000000000000'::UUID),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT organization_environment_mode_unique UNIQUE (organization_id, mode),
  CONSTRAINT organization_environment_org_id_unique UNIQUE (organization_id, id),
  CONSTRAINT organization_environment_key_mode_unique UNIQUE (organization_id, id, mode)
);

INSERT INTO organization_environment (organization_id, mode)
SELECT id, environment.mode FROM organization CROSS JOIN (VALUES ('test'), ('live')) environment(mode);

CREATE FUNCTION organization_create_environments() RETURNS trigger AS $$
BEGIN
  INSERT INTO organization_environment (organization_id, mode) VALUES (NEW.id, 'test'), (NEW.id, 'live');
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER organization_create_environments AFTER INSERT ON organization
FOR EACH ROW EXECUTE FUNCTION organization_create_environments();

ALTER TABLE api_key ADD COLUMN environment_id UUID;
ALTER TABLE api_key ADD CONSTRAINT api_key_environment_mode_fk
  FOREIGN KEY (organization_id, environment_id, mode)
  REFERENCES organization_environment (organization_id, id, mode);

ALTER TABLE customer ADD COLUMN environment_id UUID;
ALTER TABLE customer ADD CONSTRAINT customer_environment_fk
  FOREIGN KEY (organization_id, environment_id) REFERENCES organization_environment (organization_id, id);
ALTER TABLE customer ADD COLUMN environment_scope_id UUID GENERATED ALWAYS AS
  (COALESCE(environment_id, '00000000-0000-0000-0000-000000000000'::UUID)) STORED NOT NULL;
ALTER TABLE customer ADD CONSTRAINT customer_environment_id_unique
  UNIQUE (organization_id, environment_scope_id, id);
DROP INDEX customer_organization_external_id_unique;
CREATE UNIQUE INDEX customer_environment_external_id_unique
  ON customer (organization_id, environment_scope_id, external_id) WHERE external_id IS NOT NULL;

ALTER TABLE event ADD COLUMN environment_id UUID;
ALTER TABLE event ADD CONSTRAINT event_environment_fk
  FOREIGN KEY (organization_id, environment_id) REFERENCES organization_environment (organization_id, id) NOT VALID;
ALTER TABLE event ADD COLUMN environment_scope_id UUID GENERATED ALWAYS AS
  (COALESCE(environment_id, '00000000-0000-0000-0000-000000000000'::UUID)) STORED NOT NULL;
ALTER TABLE event DROP CONSTRAINT event_pk;
ALTER TABLE event ADD CONSTRAINT event_pk PRIMARY KEY (organization_id, environment_scope_id, transaction_id);
ALTER TABLE event ADD CONSTRAINT event_customer_same_environment_fk
  FOREIGN KEY (organization_id, environment_scope_id, customer_id)
  REFERENCES customer (organization_id, environment_scope_id, id) NOT VALID;

ALTER TABLE audit_log ADD COLUMN environment_id UUID;
ALTER TABLE audit_log ADD CONSTRAINT audit_log_environment_fk
  FOREIGN KEY (organization_id, environment_id) REFERENCES organization_environment (organization_id, id);
