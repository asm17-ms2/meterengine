ALTER TABLE customer ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE billable_metric ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

CREATE TABLE idempotent_request (
  organization_id UUID NOT NULL,
  environment_id UUID,
  environment_scope VARCHAR NOT NULL,
  operation VARCHAR NOT NULL,
  key_hash VARCHAR(64) NOT NULL,
  request_hash VARCHAR(64) NOT NULL,
  response_status INTEGER,
  response_body TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
  CONSTRAINT idempotent_request_organization_fk FOREIGN KEY (organization_id) REFERENCES organization(id),
  CONSTRAINT idempotent_request_pk PRIMARY KEY (organization_id, environment_scope, operation, key_hash),
  CONSTRAINT idempotent_request_environment_fk FOREIGN KEY (organization_id, environment_id)
    REFERENCES organization_environment (organization_id, id),
  CONSTRAINT idempotent_request_response_check CHECK ((response_status IS NULL) = (response_body IS NULL)),
  CONSTRAINT idempotent_request_scope_check CHECK (environment_scope = coalesce(environment_id::text, 'legacy'))
);
