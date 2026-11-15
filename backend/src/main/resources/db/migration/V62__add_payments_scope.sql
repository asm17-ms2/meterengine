-- 결제 요청과 취소는 admin에서 떼어 payments 권한으로 받는다. 지금 admin을 가진 키, 구성원, 초대는 결제를 계속 할 수 있게 payments를 더한다.
ALTER TABLE api_key DROP CONSTRAINT api_key_scopes_check;
ALTER TABLE api_key ADD CONSTRAINT api_key_scopes_check CHECK (
  cardinality(scopes) > 0
  AND scopes <@ ARRAY['ingest', 'read', 'admin', 'payments']::VARCHAR[]);

ALTER TABLE organization_member DROP CONSTRAINT organization_member_scopes_check;
ALTER TABLE organization_member ADD CONSTRAINT organization_member_scopes_check CHECK (
  cardinality(scopes) > 0 AND array_position(scopes, NULL) IS NULL
  AND scopes <@ ARRAY['ingest', 'read', 'admin', 'payments']::VARCHAR[]);
ALTER TABLE organization_member ALTER COLUMN scopes SET DEFAULT ARRAY['admin', 'payments']::VARCHAR[];

ALTER TABLE organization_invitation DROP CONSTRAINT organization_invitation_scopes_check;
ALTER TABLE organization_invitation ADD CONSTRAINT organization_invitation_scopes_check CHECK (
  cardinality(scopes) > 0 AND array_position(scopes, NULL) IS NULL
  AND scopes <@ ARRAY['ingest', 'read', 'admin', 'payments']::VARCHAR[]);
ALTER TABLE organization_invitation ALTER COLUMN scopes SET DEFAULT ARRAY['admin', 'payments']::VARCHAR[];

UPDATE api_key SET scopes = array_append(scopes, 'payments')
  WHERE 'admin' = ANY(scopes) AND NOT ('payments' = ANY(scopes));
UPDATE organization_member SET scopes = array_append(scopes, 'payments')
  WHERE 'admin' = ANY(scopes) AND NOT ('payments' = ANY(scopes));
UPDATE organization_invitation SET scopes = array_append(scopes, 'payments')
  WHERE 'admin' = ANY(scopes) AND NOT ('payments' = ANY(scopes));
