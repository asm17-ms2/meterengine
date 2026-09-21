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
  (organization_id, code, name, event_type, aggregation, target_property) VALUES
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'token-usage', '토큰 사용량',
   'chat_completion', 'SUM', 'token')
ON CONFLICT (organization_id, code) DO UPDATE SET
  name            = EXCLUDED.name,
  event_type      = EXCLUDED.event_type,
  aggregation     = EXCLUDED.aggregation,
  target_property = EXCLUDED.target_property;

INSERT INTO price_policy (organization_id, billable_metric_code) VALUES
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'token-usage')
ON CONFLICT (organization_id, billable_metric_code) DO UPDATE SET
  dimension_properties = EXCLUDED.dimension_properties;

INSERT INTO price_rate (organization_id, billable_metric_code, dimension_values, unit_price) VALUES
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'token-usage', '{}', 0.007)
ON CONFLICT (organization_id, billable_metric_code, dimension_values) DO UPDATE SET
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
  (organization_id, code, name, event_type, aggregation, target_property) VALUES
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'input-tokens', '입력 토큰',
   'llm_request', 'SUM', 'input_tokens'),
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'output-tokens', '출력 토큰',
   'llm_request', 'SUM', 'output_tokens'),
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'network-egress', '외부 전송량',
   'network_traffic', 'SUM', 'egress_gb')
ON CONFLICT (organization_id, code) DO UPDATE SET
  name            = EXCLUDED.name,
  event_type      = EXCLUDED.event_type,
  aggregation     = EXCLUDED.aggregation,
  target_property = EXCLUDED.target_property;

INSERT INTO price_policy (organization_id, billable_metric_code) VALUES
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'input-tokens'),
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'output-tokens'),
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'network-egress')
ON CONFLICT (organization_id, billable_metric_code) DO UPDATE SET
  dimension_properties = EXCLUDED.dimension_properties;

INSERT INTO price_rate (organization_id, billable_metric_code, dimension_values, unit_price) VALUES
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'input-tokens', '{}', 0.007),
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'output-tokens', '{}', 0.035),
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'network-egress', '{}', 120.0)
ON CONFLICT (organization_id, billable_metric_code, dimension_values) DO UPDATE SET
  unit_price = EXCLUDED.unit_price;

INSERT INTO billable_metric
  (organization_id, code, name, event_type, aggregation, target_property) VALUES
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'cache-read-tokens', '캐시 읽기 토큰',
   'llm_request', 'SUM', 'cache_read_tokens'),
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'cache-creation-tokens', '캐시 생성 토큰',
   'llm_request', 'SUM', 'cache_creation_tokens')
ON CONFLICT (organization_id, code) DO UPDATE SET
  name            = EXCLUDED.name,
  event_type      = EXCLUDED.event_type,
  aggregation     = EXCLUDED.aggregation,
  target_property = EXCLUDED.target_property;

INSERT INTO price_policy (organization_id, billable_metric_code) VALUES
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'cache-read-tokens'),
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'cache-creation-tokens')
ON CONFLICT (organization_id, billable_metric_code) DO UPDATE SET
  dimension_properties = EXCLUDED.dimension_properties;

INSERT INTO price_rate (organization_id, billable_metric_code, dimension_values, unit_price) VALUES
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'cache-read-tokens', '{}', 0.0007),
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'cache-creation-tokens', '{}', 0.00875)
ON CONFLICT (organization_id, billable_metric_code, dimension_values) DO UPDATE SET
  unit_price = EXCLUDED.unit_price;
