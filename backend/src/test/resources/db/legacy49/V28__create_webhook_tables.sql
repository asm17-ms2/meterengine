CREATE TABLE webhook_endpoint (
  id UUID CONSTRAINT webhook_endpoint_pk PRIMARY KEY,
  organization_id UUID NOT NULL CONSTRAINT webhook_endpoint_organization_fk REFERENCES organization(id),
  url VARCHAR(2048) NOT NULL,
  secret VARCHAR NOT NULL,
  event_types TEXT[] NOT NULL CONSTRAINT webhook_endpoint_event_types_check CHECK (cardinality(event_types) > 0),
  enabled BOOLEAN NOT NULL DEFAULT true,
  description VARCHAR(1000),
  created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
  deleted_at TIMESTAMPTZ,
  CONSTRAINT webhook_endpoint_organization_id_unique UNIQUE (organization_id, id)
);

CREATE TABLE webhook_event (
  id UUID CONSTRAINT webhook_event_pk PRIMARY KEY,
  organization_id UUID NOT NULL CONSTRAINT webhook_event_organization_fk REFERENCES organization(id),
  type VARCHAR NOT NULL,
  payload JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
  CONSTRAINT webhook_event_organization_id_unique UNIQUE (organization_id, id)
);

CREATE TABLE webhook_delivery (
  id UUID CONSTRAINT webhook_delivery_pk PRIMARY KEY,
  organization_id UUID NOT NULL CONSTRAINT webhook_delivery_organization_fk REFERENCES organization(id),
  webhook_endpoint_id UUID NOT NULL,
  webhook_event_id UUID NOT NULL,
  status VARCHAR NOT NULL DEFAULT 'pending' CONSTRAINT webhook_delivery_status_check CHECK (status IN ('pending', 'done', 'failed')),
  attempt_count INTEGER NOT NULL DEFAULT 0 CONSTRAINT webhook_delivery_attempt_count_check CHECK (attempt_count >= 0),
  next_attempt_at TIMESTAMPTZ,
  last_attempted_at TIMESTAMPTZ,
  claim_token UUID,
  response_status INTEGER,
  failure_reason VARCHAR,
  created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
  CONSTRAINT webhook_delivery_webhook_endpoint_same_organization_fk FOREIGN KEY (organization_id, webhook_endpoint_id) REFERENCES webhook_endpoint(organization_id, id),
  CONSTRAINT webhook_delivery_webhook_event_same_organization_fk FOREIGN KEY (organization_id, webhook_event_id) REFERENCES webhook_event(organization_id, id),
  CONSTRAINT webhook_delivery_webhook_endpoint_webhook_event_unique UNIQUE (webhook_endpoint_id, webhook_event_id),
  CONSTRAINT webhook_delivery_organization_id_unique UNIQUE (organization_id, id)
);

CREATE INDEX webhook_delivery_pending_next_attempt_at ON webhook_delivery(next_attempt_at) WHERE status = 'pending';
CREATE INDEX webhook_delivery_endpoint_created_at ON webhook_delivery(organization_id, webhook_endpoint_id, created_at DESC, id DESC);

CREATE TABLE webhook_delivery_attempt (
  id UUID CONSTRAINT webhook_delivery_attempt_pk PRIMARY KEY,
  organization_id UUID NOT NULL CONSTRAINT webhook_delivery_attempt_organization_fk REFERENCES organization(id),
  webhook_delivery_id UUID NOT NULL,
  attempted_at TIMESTAMPTZ NOT NULL,
  response_status INTEGER,
  failure_reason VARCHAR CONSTRAINT webhook_delivery_attempt_failure_reason_check CHECK (failure_reason IN ('timeout', 'connection_error', 'unexpected_status', 'blocked_address', 'endpoint_disabled', 'secret_unavailable', 'lease_expired')),
  duration_ms BIGINT NOT NULL CONSTRAINT webhook_delivery_attempt_duration_ms_check CHECK (duration_ms >= 0),
  CONSTRAINT webhook_delivery_attempt_webhook_delivery_same_organization_fk FOREIGN KEY (organization_id, webhook_delivery_id) REFERENCES webhook_delivery(organization_id, id)
);

CREATE INDEX webhook_delivery_attempt_webhook_delivery_lookup ON webhook_delivery_attempt(organization_id, webhook_delivery_id);

CREATE FUNCTION webhook_block_mutation() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'webhook history is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER webhook_event_block_update BEFORE UPDATE ON webhook_event FOR EACH ROW EXECUTE FUNCTION webhook_block_mutation();
CREATE TRIGGER webhook_event_block_delete BEFORE DELETE ON webhook_event FOR EACH ROW EXECUTE FUNCTION webhook_block_mutation();
CREATE TRIGGER webhook_event_block_truncate BEFORE TRUNCATE ON webhook_event FOR EACH STATEMENT EXECUTE FUNCTION webhook_block_mutation();
CREATE TRIGGER webhook_delivery_attempt_block_update BEFORE UPDATE ON webhook_delivery_attempt FOR EACH ROW EXECUTE FUNCTION webhook_block_mutation();
CREATE TRIGGER webhook_delivery_attempt_block_delete BEFORE DELETE ON webhook_delivery_attempt FOR EACH ROW EXECUTE FUNCTION webhook_block_mutation();
CREATE TRIGGER webhook_delivery_attempt_block_truncate BEFORE TRUNCATE ON webhook_delivery_attempt FOR EACH STATEMENT EXECUTE FUNCTION webhook_block_mutation();
