CREATE TABLE auth_request_rate_limit (
  scope VARCHAR(32) NOT NULL CHECK (scope IN ('login_ip', 'login_account', 'signup_ip', 'signup_account')),
  identity_hash BYTEA NOT NULL CHECK (octet_length(identity_hash) = 32),
  attempts BIGINT NOT NULL CHECK (attempts > 0),
  expires_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT auth_request_rate_limit_pk PRIMARY KEY (scope, identity_hash)
);

CREATE INDEX auth_request_rate_limit_expiry_idx ON auth_request_rate_limit (expires_at);
