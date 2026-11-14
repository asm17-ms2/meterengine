-- 실제 환경 이용 신청 시도를 조직마다 세는 범위를 더한다.
ALTER TABLE auth_request_rate_limit DROP CONSTRAINT auth_request_rate_limit_scope_check;
ALTER TABLE auth_request_rate_limit ADD CONSTRAINT auth_request_rate_limit_scope_check CHECK (
  scope IN (
    'login_ip', 'login_account', 'signup_ip', 'signup_account',
    'email_verification_ip', 'email_verification_account',
    'live_environment_application'));
