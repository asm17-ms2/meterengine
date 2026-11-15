-- 카프카 경로에서는 수집 서버가 받은 시각을 컨슈머가 넣는다. 덮어쓰면 저장이 밀린 만큼 받은 시각이 늦어져 집계 마감 판정이 틀린다.
CREATE OR REPLACE FUNCTION event_set_received_at() RETURNS trigger AS $$
BEGIN
  IF NEW.received_at IS NULL THEN
    NEW.received_at := clock_timestamp();
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;
