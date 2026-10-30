INSERT INTO organization (id, name) VALUES
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', '데모 도입사')
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

INSERT INTO customer (id, organization_id, name) VALUES
  ('a728e7b6-d82b-4f3c-a960-a66a02794c1d',
   'd7cee55d-8c82-4afc-b996-6749d8b26a4e', '아크메 주식회사'),
  ('252339bc-d5f8-472d-b5d6-ed8554049450',
   'd7cee55d-8c82-4afc-b996-6749d8b26a4e', '베타 스튜디오')
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

INSERT INTO billable_metric
  (organization_id, code, name, event_type, aggregation, target_property,
   dimension_properties) VALUES
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'token-usage', '토큰 사용량',
   'chat_completion', 'sum', 'token', '{}')
ON CONFLICT (organization_id, environment_scope_id, code) DO UPDATE SET
  name                 = EXCLUDED.name,
  event_type           = EXCLUDED.event_type,
  aggregation          = EXCLUDED.aggregation,
  percentile           = NULL,
  target_property      = EXCLUDED.target_property,
  dimension_properties = EXCLUDED.dimension_properties;

INSERT INTO price_rate (organization_id, billable_metric_code, dimension_values, unit_price)
SELECT seed.organization_id::uuid, seed.billable_metric_code, seed.dimension_values::jsonb, seed.unit_price
FROM (VALUES
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'token-usage', '{}', 0.007)) AS seed(organization_id, billable_metric_code, dimension_values, unit_price)
WHERE NOT EXISTS (
  SELECT 1 FROM price_rate archived WHERE archived.organization_id = seed.organization_id::uuid AND archived.environment_id IS NULL
    AND archived.billable_metric_code = seed.billable_metric_code
    AND archived.dimension_values = seed.dimension_values::jsonb
    AND archived.effective_from = '2000-01' AND archived.archived_at IS NOT NULL
)
ON CONFLICT (organization_id, environment_scope_id, billable_metric_code, dimension_values, effective_from) WHERE customer_id IS NULL AND archived_at IS NULL DO UPDATE SET
  unit_price = EXCLUDED.unit_price;

INSERT INTO customer (id, organization_id, name) VALUES
  ('35bc8d12-9d38-57ab-bc9b-bbd35d779a26',
   'd7cee55d-8c82-4afc-b996-6749d8b26a4e', '이슬비랩스'),
  ('008cd6a7-6ff9-505d-9421-747e7d2d62aa',
   'd7cee55d-8c82-4afc-b996-6749d8b26a4e', '도담헬스'),
  ('8c525322-2712-5b5f-aa1a-435a7ff9fe97',
   'd7cee55d-8c82-4afc-b996-6749d8b26a4e', '한들물류')
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

INSERT INTO billable_metric
  (organization_id, code, name, event_type, aggregation, target_property,
   dimension_properties) VALUES
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'input-tokens', '입력 토큰',
   'llm_request', 'sum', 'input_tokens', '{model}'),
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'output-tokens', '출력 토큰',
   'llm_request', 'sum', 'output_tokens', '{model}'),
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'network-egress', '외부 전송량',
   'network_traffic', 'sum', 'egress_gb', '{}')
ON CONFLICT (organization_id, environment_scope_id, code) DO UPDATE SET
  name                 = EXCLUDED.name,
  event_type           = EXCLUDED.event_type,
  aggregation          = EXCLUDED.aggregation,
  percentile           = NULL,
  target_property      = EXCLUDED.target_property,
  dimension_properties = EXCLUDED.dimension_properties;

UPDATE price_rate SET archived_at = coalesce(archived_at, now()),
  archived_from = coalesce(archived_from, to_char((now() AT TIME ZONE 'Asia/Seoul') + interval '1 month', 'YYYY-MM'))
WHERE organization_id = 'd7cee55d-8c82-4afc-b996-6749d8b26a4e'
  AND environment_id IS NULL
  AND billable_metric_code IN ('input-tokens', 'output-tokens')
  AND dimension_values = '{}';

INSERT INTO price_rate (organization_id, billable_metric_code, dimension_values, unit_price)
SELECT seed.organization_id::uuid, seed.billable_metric_code, seed.dimension_values::jsonb, seed.unit_price
FROM (VALUES
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'input-tokens', '{"model": "claude-fable-5"}', 0.0159),
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'input-tokens', '{"model": "claude-fable-5-1"}', 0.0159),
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'input-tokens', '{"model": "claude-haiku-4-5"}', 0.00159),
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'input-tokens', '{"model": "claude-haiku-4-5-20251001"}', 0.00159),
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'input-tokens', '{"model": "claude-haiku-5-5"}', 0.000159),
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'input-tokens', '{"model": "claude-opus-5"}', 0.00795),
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'input-tokens', '{"model": "claude-opus-5-5"}', 0.00636),
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'input-tokens', '{"model": "claude-sonnet-5"}', 0.00318),
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'input-tokens', '{"model": "claude-sonnet-5-5"}', 0.00318),
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'output-tokens', '{"model": "claude-fable-5"}', 0.0795),
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'output-tokens', '{"model": "claude-fable-5-1"}', 0.0795),
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'output-tokens', '{"model": "claude-haiku-4-5"}', 0.00795),
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'output-tokens', '{"model": "claude-haiku-4-5-20251001"}', 0.00795),
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'output-tokens', '{"model": "claude-haiku-5-5"}', 0.000795),
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'output-tokens', '{"model": "claude-opus-5"}', 0.03975),
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'output-tokens', '{"model": "claude-opus-5-5"}', 0.0318),
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'output-tokens', '{"model": "claude-sonnet-5"}', 0.0159),
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'output-tokens', '{"model": "claude-sonnet-5-5"}', 0.0159),
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'network-egress', '{}', 120.0)) AS seed(organization_id, billable_metric_code, dimension_values, unit_price)
WHERE NOT EXISTS (
  SELECT 1 FROM price_rate archived WHERE archived.organization_id = seed.organization_id::uuid AND archived.environment_id IS NULL
    AND archived.billable_metric_code = seed.billable_metric_code
    AND archived.dimension_values = seed.dimension_values::jsonb
    AND archived.effective_from = '2000-01' AND archived.archived_at IS NOT NULL
)
ON CONFLICT (organization_id, environment_scope_id, billable_metric_code, dimension_values, effective_from) WHERE customer_id IS NULL AND archived_at IS NULL DO UPDATE SET
  unit_price = EXCLUDED.unit_price;

INSERT INTO billable_metric
  (organization_id, code, name, event_type, aggregation, target_property,
   dimension_properties) VALUES
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'cache-read-tokens', '캐시 읽기 토큰',
   'llm_request', 'sum', 'cache_read_tokens', '{}'),
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'cache-creation-tokens', '캐시 생성 토큰',
   'llm_request', 'sum', 'cache_creation_tokens', '{}')
ON CONFLICT (organization_id, environment_scope_id, code) DO UPDATE SET
  name                 = EXCLUDED.name,
  event_type           = EXCLUDED.event_type,
  aggregation          = EXCLUDED.aggregation,
  percentile           = NULL,
  target_property      = EXCLUDED.target_property,
  dimension_properties = EXCLUDED.dimension_properties;

INSERT INTO price_rate (organization_id, billable_metric_code, dimension_values, unit_price)
SELECT seed.organization_id::uuid, seed.billable_metric_code, seed.dimension_values::jsonb, seed.unit_price
FROM (VALUES
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'cache-read-tokens', '{}', 0.0007),
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'cache-creation-tokens', '{}', 0.00875)) AS seed(organization_id, billable_metric_code, dimension_values, unit_price)
WHERE NOT EXISTS (
  SELECT 1 FROM price_rate archived WHERE archived.organization_id = seed.organization_id::uuid AND archived.environment_id IS NULL
    AND archived.billable_metric_code = seed.billable_metric_code
    AND archived.dimension_values = seed.dimension_values::jsonb
    AND archived.effective_from = '2000-01' AND archived.archived_at IS NOT NULL
)
ON CONFLICT (organization_id, environment_scope_id, billable_metric_code, dimension_values, effective_from) WHERE customer_id IS NULL AND archived_at IS NULL DO UPDATE SET
  unit_price = EXCLUDED.unit_price;
