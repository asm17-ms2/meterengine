CREATE TABLE organization (
  id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name VARCHAR NOT NULL
);

CREATE TABLE billable_metric (
  organization_id UUID NOT NULL REFERENCES organization(id),
  code            VARCHAR NOT NULL,
  name            VARCHAR NOT NULL,
  event_type      VARCHAR NOT NULL,
  aggregation     VARCHAR NOT NULL,
  target_property VARCHAR,
  unit_price      NUMERIC NOT NULL CHECK (unit_price >= 0),

  PRIMARY KEY (organization_id, code)
);

CREATE TABLE customer (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id UUID NOT NULL REFERENCES organization(id),
  name            VARCHAR NOT NULL,

  CONSTRAINT customer_org_id_unique UNIQUE (organization_id, id)
);

CREATE TABLE usage_event (
  organization_id UUID NOT NULL REFERENCES organization(id),
  transaction_id  VARCHAR NOT NULL,
  customer_id     UUID NOT NULL,
  event_type      VARCHAR NOT NULL,
  properties      JSONB NOT NULL DEFAULT '{}',
  occurred_at     TIMESTAMPTZ NOT NULL,
  received_at     TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),

  PRIMARY KEY (organization_id, transaction_id),

  CONSTRAINT usage_event_customer_same_org
    FOREIGN KEY (organization_id, customer_id)
    REFERENCES customer (organization_id, id)
);

CREATE FUNCTION usage_event_set_received_at() RETURNS trigger AS $$
BEGIN
  NEW.received_at := clock_timestamp();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER usage_event_set_received_at
  BEFORE INSERT ON usage_event
  FOR EACH ROW EXECUTE FUNCTION usage_event_set_received_at();

CREATE FUNCTION usage_event_block_mutation() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'usage_event is append-only: UPDATE/DELETE/TRUNCATE is blocked';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER usage_event_block_update
  BEFORE UPDATE ON usage_event
  FOR EACH ROW EXECUTE FUNCTION usage_event_block_mutation();

CREATE TRIGGER usage_event_block_delete
  BEFORE DELETE ON usage_event
  FOR EACH ROW EXECUTE FUNCTION usage_event_block_mutation();

CREATE TRIGGER usage_event_block_truncate
  BEFORE TRUNCATE ON usage_event
  FOR EACH STATEMENT EXECUTE FUNCTION usage_event_block_mutation();
