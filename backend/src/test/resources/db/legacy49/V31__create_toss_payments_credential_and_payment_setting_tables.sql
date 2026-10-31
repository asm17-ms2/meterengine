CREATE TABLE toss_payments_credential (
  organization_id      UUID NOT NULL,
  mode                 VARCHAR NOT NULL
                         CONSTRAINT toss_payments_credential_mode_check
                         CHECK (mode IN ('test', 'live')),
  client_key           VARCHAR NOT NULL,
  secret_key           VARCHAR NOT NULL,
  secret_key_last_four VARCHAR NOT NULL,
  verified_at          TIMESTAMPTZ NOT NULL,
  updated_at           TIMESTAMPTZ NOT NULL,

  CONSTRAINT toss_payments_credential_pk PRIMARY KEY (organization_id, mode),

  CONSTRAINT toss_payments_credential_organization_fk
    FOREIGN KEY (organization_id) REFERENCES organization (id)
);

CREATE TABLE payment_setting (
  organization_id UUID NOT NULL,
  mode            VARCHAR NOT NULL
                    CONSTRAINT payment_setting_mode_check
                    CHECK (mode IN ('test', 'live')),
  updated_at      TIMESTAMPTZ NOT NULL,

  CONSTRAINT payment_setting_pk PRIMARY KEY (organization_id),

  CONSTRAINT payment_setting_organization_fk
    FOREIGN KEY (organization_id) REFERENCES organization (id)
);
