ALTER TABLE invoice ADD COLUMN cutoff_at TIMESTAMPTZ;

-- 확정이 받은 시각 상한을 쓰기 시작한 운영 배포 구간(2026-10-30 15:09:28부터 15:10:20 KST까지)에 지난 달을 확정했고
-- 마감 뒤 받은 이벤트가 있으면 어느 코드가 확정했는지 몰라 멈춘다.
DO $$
DECLARE
  ambiguous bigint;
BEGIN
  SELECT count(*) INTO ambiguous
  FROM invoice i
  WHERE i.finalized_at >= TIMESTAMPTZ '2026-10-30 15:09:28+09'
    AND i.finalized_at < TIMESTAMPTZ '2026-10-30 15:10:20+09'
    AND EXISTS (
      SELECT 1 FROM event e
      WHERE e.organization_id = i.organization_id
        AND e.environment_scope_id = i.environment_scope_id
        AND e.customer_id = i.customer_id
        AND e.occurred_at >= (to_date(i.period, 'YYYY-MM')::timestamp AT TIME ZONE 'Asia/Seoul')
        AND e.occurred_at < ((to_date(i.period, 'YYYY-MM') + INTERVAL '1 month')::timestamp AT TIME ZONE 'Asia/Seoul')
        AND e.received_at >= ((to_date(i.period, 'YYYY-MM') + INTERVAL '1 month')::timestamp + INTERVAL '13 hours') AT TIME ZONE 'Asia/Seoul'
        AND e.received_at < i.finalized_at);
  IF ambiguous > 0 THEN
    RAISE EXCEPTION 'cutoff_at을 정할 수 없는 인보이스 %건', ambiguous;
  END IF;
END $$;

ALTER TABLE invoice DISABLE TRIGGER invoice_block_update;

-- 그 배포 전 확정은 확정 순간까지 받은 이벤트를, 그 뒤 확정은 그 달 마감과 확정 순간 중 이른 시각까지 받은 이벤트를 읽었다.
UPDATE invoice SET cutoff_at = CASE
  WHEN finalized_at < TIMESTAMPTZ '2026-10-30 15:09:28+09' THEN finalized_at
  ELSE LEAST(finalized_at, ((to_date(period, 'YYYY-MM') + INTERVAL '1 month')::timestamp + INTERVAL '13 hours') AT TIME ZONE 'Asia/Seoul')
END;

ALTER TABLE invoice ENABLE TRIGGER invoice_block_update;

ALTER TABLE invoice ALTER COLUMN cutoff_at SET NOT NULL;

ALTER TABLE invoice
  ADD CONSTRAINT invoice_cutoff_at_check CHECK (cutoff_at <= finalized_at);

-- 청구 예정액과 확정이 (도입사, 달)로 인보이스를 찾는다. 있는 인덱스는 달로 거르지 못한다.
CREATE INDEX invoice_organization_period ON invoice (organization_id, period);
