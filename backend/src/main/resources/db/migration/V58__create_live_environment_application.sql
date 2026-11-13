CREATE TABLE live_environment_application (
  id UUID NOT NULL CONSTRAINT live_environment_application_pk PRIMARY KEY,
  organization_id UUID NOT NULL
    CONSTRAINT live_environment_application_organization_fk REFERENCES organization(id),
  business_registration_number VARCHAR NOT NULL
    CONSTRAINT live_environment_application_business_registration_number_check
    CHECK (business_registration_number ~ '^[0-9]{10}$'),
  toss_payments_merchant_id VARCHAR NOT NULL,
  -- 검토 중인 신청만 실결제 키를 갖는다. 승인하면 실제 환경의 키로 옮기고 거절하면 지운다.
  toss_payments_client_key VARCHAR,
  toss_payments_secret_key VARCHAR,
  status VARCHAR NOT NULL
    CONSTRAINT live_environment_application_status_check
    CHECK (status IN ('pending', 'approved', 'rejected')),
  -- operating은 계속사업자, suspended는 휴업자, unchecked는 국세청 조회 결과를 받지 못한 신청이다.
  business_status VARCHAR NOT NULL
    CONSTRAINT live_environment_application_business_status_check
    CHECK (business_status IN ('operating', 'suspended', 'unchecked')),
  tax_type VARCHAR,
  rejection_reason VARCHAR,
  applicant_user_account_id UUID NOT NULL
    CONSTRAINT live_environment_application_applicant_user_account_fk REFERENCES user_account(id),
  created_at TIMESTAMPTZ NOT NULL,
  reviewer_user_account_id UUID
    CONSTRAINT live_environment_application_reviewer_user_account_fk REFERENCES user_account(id),
  reviewed_at TIMESTAMPTZ,
  -- 승인 뒤 토스 응답에서 처음 본 상점아이디와 그 시각. 신청한 값과 다르면 운영자 화면에 알린다.
  observed_toss_payments_merchant_id VARCHAR,
  merchant_id_checked_at TIMESTAMPTZ,
  CONSTRAINT live_environment_application_review_check CHECK (
    (status = 'pending' AND reviewed_at IS NULL AND reviewer_user_account_id IS NULL)
    OR (status <> 'pending' AND reviewed_at IS NOT NULL AND reviewer_user_account_id IS NOT NULL)),
  CONSTRAINT live_environment_application_rejection_reason_check CHECK (
    (status = 'rejected') = (rejection_reason IS NOT NULL)),
  CONSTRAINT live_environment_application_toss_payments_key_check CHECK (
    (status = 'pending') = (toss_payments_client_key IS NOT NULL)
    AND (status = 'pending') = (toss_payments_secret_key IS NOT NULL))
);

CREATE UNIQUE INDEX live_environment_application_pending_unique
  ON live_environment_application(organization_id) WHERE status = 'pending';
CREATE INDEX live_environment_application_organization_index
  ON live_environment_application(organization_id, created_at DESC);
