-- 가입만 앱이 NULL로 넣어 실제 환경을 잠근다. SQL로 직접 넣는 조직(시드, 운영 스크립트)은 열린 것으로 둔다.
ALTER TABLE organization ADD COLUMN live_environment_opened_at TIMESTAMPTZ DEFAULT now();

-- 승인을 기다리던 조직은 테스트 환경만 열린 채로 바로 쓰게 한다. 거절된 조직은 상태를 그대로 둔다.
UPDATE organization SET live_environment_opened_at = NULL WHERE status IN ('pending', 'rejected');
UPDATE organization SET status = 'active' WHERE status = 'pending';
