-- 데모 도입사의 테스트 환경에 넣는다. 예전 시드가 legacy에 넣은 행은 건드리지 않는다.
INSERT INTO organization (id, name) VALUES
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', '데모 도입사')
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

INSERT INTO customer (id, organization_id, environment_id, name)
SELECT seed.id::uuid, environment.organization_id, environment.id, seed.name
FROM organization_environment environment
CROSS JOIN (VALUES
  ('a728e7b6-d82b-4f3c-a960-a66a02794c1d', '아크메 주식회사'),
  ('252339bc-d5f8-472d-b5d6-ed8554049450', '베타 스튜디오'),
  ('35bc8d12-9d38-57ab-bc9b-bbd35d779a26', '이슬비랩스'),
  ('008cd6a7-6ff9-505d-9421-747e7d2d62aa', '도담헬스'),
  ('8c525322-2712-5b5f-aa1a-435a7ff9fe97', '한들물류')) AS seed(id, name)
WHERE environment.organization_id = 'd7cee55d-8c82-4afc-b996-6749d8b26a4e'
  AND environment.mode = 'test'
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name
WHERE customer.environment_id = EXCLUDED.environment_id;

INSERT INTO billable_metric
  (organization_id, environment_id, environment_scope_id, code, name, event_type, aggregation,
   target_property, dimension_properties)
SELECT environment.organization_id, environment.id, environment.id, seed.code, seed.name,
  seed.event_type, seed.aggregation, seed.target_property, seed.dimension_properties::text[]
FROM organization_environment environment
CROSS JOIN (VALUES
  ('token-usage', '토큰 사용량', 'chat_completion', 'sum', 'token', '{}'),
  ('input-tokens', '입력 토큰', 'llm_request', 'sum', 'input_tokens', '{model}'),
  ('output-tokens', '출력 토큰', 'llm_request', 'sum', 'output_tokens', '{model}'),
  ('network-egress', '외부 전송량', 'network_traffic', 'sum', 'egress_gb', '{}'),
  ('cache-read-tokens', '캐시 읽기 토큰', 'llm_request', 'sum', 'cache_read_tokens', '{}'),
  ('cache-creation-tokens', '캐시 생성 토큰', 'llm_request', 'sum', 'cache_creation_tokens', '{}'))
  AS seed(code, name, event_type, aggregation, target_property, dimension_properties)
WHERE environment.organization_id = 'd7cee55d-8c82-4afc-b996-6749d8b26a4e'
  AND environment.mode = 'test'
ON CONFLICT (organization_id, environment_scope_id, code) DO UPDATE SET
  name                 = EXCLUDED.name,
  event_type           = EXCLUDED.event_type,
  aggregation          = EXCLUDED.aggregation,
  percentile           = NULL,
  target_property      = EXCLUDED.target_property,
  dimension_properties = EXCLUDED.dimension_properties;

UPDATE price_rate SET archived_at = coalesce(archived_at, now()),
  archived_from = coalesce(archived_from, to_char((now() AT TIME ZONE 'Asia/Seoul') + interval '1 month', 'YYYY-MM'))
FROM organization_environment environment
WHERE environment.organization_id = 'd7cee55d-8c82-4afc-b996-6749d8b26a4e'
  AND environment.mode = 'test'
  AND price_rate.organization_id = environment.organization_id
  AND price_rate.environment_id = environment.id
  AND price_rate.billable_metric_code IN ('input-tokens', 'output-tokens')
  AND price_rate.dimension_values = '{}';

INSERT INTO price_rate (organization_id, environment_id, billable_metric_code, dimension_values, unit_price)
SELECT environment.organization_id, environment.id, seed.billable_metric_code,
  seed.dimension_values::jsonb, seed.unit_price
FROM organization_environment environment
CROSS JOIN (VALUES
  ('token-usage', '{}', 0.007),
  ('input-tokens', '{"model": "claude-fable-5"}', 0.0159),
  ('input-tokens', '{"model": "claude-fable-5-1"}', 0.0159),
  ('input-tokens', '{"model": "claude-haiku-4-5"}', 0.00159),
  ('input-tokens', '{"model": "claude-haiku-4-5-20251001"}', 0.00159),
  ('input-tokens', '{"model": "claude-haiku-5-5"}', 0.000159),
  ('input-tokens', '{"model": "claude-opus-5"}', 0.00795),
  ('input-tokens', '{"model": "claude-opus-5-5"}', 0.00636),
  ('input-tokens', '{"model": "claude-sonnet-5"}', 0.00318),
  ('input-tokens', '{"model": "claude-sonnet-5-5"}', 0.00318),
  ('output-tokens', '{"model": "claude-fable-5"}', 0.0795),
  ('output-tokens', '{"model": "claude-fable-5-1"}', 0.0795),
  ('output-tokens', '{"model": "claude-haiku-4-5"}', 0.00795),
  ('output-tokens', '{"model": "claude-haiku-4-5-20251001"}', 0.00795),
  ('output-tokens', '{"model": "claude-haiku-5-5"}', 0.000795),
  ('output-tokens', '{"model": "claude-opus-5"}', 0.03975),
  ('output-tokens', '{"model": "claude-opus-5-5"}', 0.0318),
  ('output-tokens', '{"model": "claude-sonnet-5"}', 0.0159),
  ('output-tokens', '{"model": "claude-sonnet-5-5"}', 0.0159),
  ('network-egress', '{}', 120.0),
  ('cache-read-tokens', '{}', 0.0007),
  ('cache-creation-tokens', '{}', 0.00875)) AS seed(billable_metric_code, dimension_values, unit_price)
WHERE environment.organization_id = 'd7cee55d-8c82-4afc-b996-6749d8b26a4e'
  AND environment.mode = 'test'
  AND NOT EXISTS (
    SELECT 1 FROM price_rate archived
    WHERE archived.organization_id = environment.organization_id
      AND archived.environment_id = environment.id
      AND archived.billable_metric_code = seed.billable_metric_code
      AND archived.dimension_values = seed.dimension_values::jsonb
      AND archived.effective_from = '2000-01' AND archived.archived_at IS NOT NULL)
ON CONFLICT (organization_id, environment_scope_id, billable_metric_code, dimension_values, effective_from)
  WHERE customer_id IS NULL AND archived_at IS NULL DO UPDATE SET
  unit_price = EXCLUDED.unit_price;
