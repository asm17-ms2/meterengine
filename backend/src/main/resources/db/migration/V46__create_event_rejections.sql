ALTER TABLE event ADD COLUMN request_id UUID;
ALTER TABLE event ADD COLUMN actor_type VARCHAR CHECK (actor_type IN ('api_key', 'user_account', 'system'));
ALTER TABLE event ADD COLUMN actor_id UUID;
ALTER TABLE event ADD CONSTRAINT event_actor_identity_check CHECK (
  CASE WHEN actor_type IS NULL OR actor_type = 'system' THEN actor_id IS NULL
       ELSE actor_id IS NOT NULL END);

CREATE TABLE event_rejection_recording_start (
  singleton BOOLEAN CONSTRAINT event_rejection_recording_start_pk PRIMARY KEY DEFAULT TRUE CHECK (singleton),
  started_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
INSERT INTO event_rejection_recording_start DEFAULT VALUES;

CREATE TABLE event_rejection (
  id UUID CONSTRAINT event_rejection_pk PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id UUID NOT NULL CONSTRAINT event_rejection_organization_fk REFERENCES organization(id),
  environment_id UUID,
  transaction_id VARCHAR(255),
  customer_id UUID,
  type VARCHAR(255),
  occurred_at TIMESTAMPTZ,
  error_code VARCHAR NOT NULL CHECK (error_code IN ('customer_not_found', 'customer_archived', 'event_too_old', 'validation_error')),
  error_fields JSONB NOT NULL DEFAULT '[]' CHECK (jsonb_typeof(error_fields) = 'array'),
  request_id UUID,
  actor_type VARCHAR NOT NULL CHECK (actor_type IN ('api_key', 'user_account', 'system')),
  actor_id UUID,
  batch_index INTEGER CHECK (batch_index BETWEEN 0 AND 99),
  recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
  CONSTRAINT event_rejection_environment_fk FOREIGN KEY (organization_id, environment_id)
    REFERENCES organization_environment (organization_id, id)
);
CREATE INDEX event_rejection_search_index ON event_rejection (organization_id, environment_id, recorded_at DESC, id DESC);
CREATE INDEX event_rejection_customer_index ON event_rejection (organization_id, environment_id, customer_id, recorded_at DESC);

CREATE FUNCTION event_rejection_block_mutation() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'event_rejection is append-only: UPDATE/DELETE/TRUNCATE is blocked';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER event_rejection_block_update BEFORE UPDATE ON event_rejection
  FOR EACH ROW EXECUTE FUNCTION event_rejection_block_mutation();
CREATE TRIGGER event_rejection_block_delete BEFORE DELETE ON event_rejection
  FOR EACH ROW EXECUTE FUNCTION event_rejection_block_mutation();
CREATE TRIGGER event_rejection_block_truncate BEFORE TRUNCATE ON event_rejection
  FOR EACH STATEMENT EXECUTE FUNCTION event_rejection_block_mutation();
