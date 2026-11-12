-- 이미 있는 계정과 SQL로 직접 넣는 계정(시드, 운영 스크립트)은 인증된 것으로 둔다. 가입만 앱이 NULL로 넣는다.
ALTER TABLE user_account ADD COLUMN email_verified_at TIMESTAMPTZ DEFAULT now();
UPDATE user_account SET email_verified_at = created_at;

CREATE TABLE email_verification (
  id UUID NOT NULL CONSTRAINT email_verification_pk PRIMARY KEY,
  user_account_id UUID NOT NULL
    CONSTRAINT email_verification_user_account_fk REFERENCES user_account(id),
  token_hash BYTEA NOT NULL
    CONSTRAINT email_verification_token_hash_unique UNIQUE
    CONSTRAINT email_verification_token_hash_check CHECK (octet_length(token_hash) = 32),
  created_at TIMESTAMPTZ NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL
    CONSTRAINT email_verification_expires_at_check CHECK (expires_at > created_at),
  used_at TIMESTAMPTZ
);

CREATE INDEX email_verification_user_account_index ON email_verification(user_account_id);

ALTER TABLE auth_request_rate_limit DROP CONSTRAINT auth_request_rate_limit_scope_check;
ALTER TABLE auth_request_rate_limit ADD CONSTRAINT auth_request_rate_limit_scope_check CHECK (
  scope IN (
    'login_ip', 'login_account', 'signup_ip', 'signup_account',
    'email_verification_ip', 'email_verification_account'));
