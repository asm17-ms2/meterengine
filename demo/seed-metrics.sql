-- 데모 확장 미터 3개 (MS2-142 mock 산출물의 축소판, 값은 원본과 동일).
-- 전체판은 미터 10개지만 데모를 단순하게 하려고 3개만 쓴다 (2026-08-14 결정).
--   input-tokens / output-tokens: llm_request 이벤트 하나가 미터 두 개에 잡히는 것을 보여준다
--   network-egress: 소수 수량과 예정액 절사를 보여준다
-- (기본 시드의 token-usage는 backend의 R__seed.sql이 항상 만든다)
--
-- TODO(MS2-157, MS2-159): 미터/가격 정책 등록 API가 생기면 psql 주입 대신 API 등록으로 전환한다.
INSERT INTO billable_metric
  (organization_id, code, name, event_type, aggregation, target_property, unit_price) VALUES
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'input-tokens', '입력 토큰', 'llm_request', 'SUM', 'input_tokens', 0.5),
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'output-tokens', '출력 토큰', 'llm_request', 'SUM', 'output_tokens', 2.5),
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'network-egress', '외부 전송량', 'network_traffic', 'SUM', 'egress_gb', 120.0)
ON CONFLICT (organization_id, code) DO UPDATE SET
  name            = EXCLUDED.name,
  event_type      = EXCLUDED.event_type,
  aggregation     = EXCLUDED.aggregation,
  target_property = EXCLUDED.target_property,
  unit_price      = EXCLUDED.unit_price;
