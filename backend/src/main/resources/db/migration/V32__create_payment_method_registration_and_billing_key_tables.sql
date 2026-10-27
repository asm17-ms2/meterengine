CREATE TABLE payment_method_registration (
  id              UUID NOT NULL,
  organization_id UUID NOT NULL,
  customer_id     UUID NOT NULL,
  token_hash      VARCHAR NOT NULL,
  mode            VARCHAR NOT NULL
                    CONSTRAINT payment_method_registration_mode_check
                    CHECK (mode IN ('test', 'live')),
  created_at      TIMESTAMPTZ NOT NULL,
  expires_at      TIMESTAMPTZ NOT NULL,
  used_at         TIMESTAMPTZ,

  CONSTRAINT payment_method_registration_pk PRIMARY KEY (id),

  CONSTRAINT payment_method_registration_token_hash_unique UNIQUE (token_hash),

  CONSTRAINT payment_method_registration_customer_same_organization_fk
    FOREIGN KEY (organization_id, customer_id)
    REFERENCES customer (organization_id, id)
    ON DELETE CASCADE
);

CREATE TABLE billing_key (
  organization_id          UUID NOT NULL,
  customer_id              UUID NOT NULL,
  mode                     VARCHAR NOT NULL
                             CONSTRAINT billing_key_mode_check
                             CHECK (mode IN ('test', 'live')),
  billing_key              VARCHAR NOT NULL,
  card_issuer_code         VARCHAR NOT NULL,
  card_number              VARCHAR NOT NULL,
  authenticated_at         TIMESTAMPTZ NOT NULL,
  consent_document_version VARCHAR NOT NULL,
  consented_at             TIMESTAMPTZ NOT NULL,

  CONSTRAINT billing_key_pk PRIMARY KEY (organization_id, customer_id, mode),

  CONSTRAINT billing_key_customer_same_organization_fk
    FOREIGN KEY (organization_id, customer_id)
    REFERENCES customer (organization_id, id)
    ON DELETE CASCADE
);
