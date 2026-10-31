CREATE TABLE billing_key (
  organization_id  UUID NOT NULL,
  customer_id      UUID NOT NULL,
  billing_key      VARCHAR NOT NULL,
  card_issuer_code VARCHAR NOT NULL,
  card_number      VARCHAR NOT NULL,
  authenticated_at TIMESTAMPTZ NOT NULL,

  CONSTRAINT billing_key_pk PRIMARY KEY (organization_id, customer_id),

  CONSTRAINT billing_key_customer_same_organization_fk
    FOREIGN KEY (organization_id, customer_id)
    REFERENCES customer (organization_id, id)
    ON DELETE CASCADE
);
