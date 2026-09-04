-- ============================================================================
-- 시드 데이터 (MS2-125, 스토리 MS2-121 첫 슬라이스)
--
-- 이번 슬라이스는 고객/미터/단가 등록 API를 만들지 않는다. 데이터가 들어오는 통로가
-- 이 시드뿐이라, 수집(MS2-130)과 조회(MS2-124)가 붙을 기준 데이터를 여기서 만든다.
--
-- Flyway 자리에 두는 이유: 마이그레이션과 함께 자동 적용돼야 MS2-128의 "단일 명령으로
-- 클린 환경(마이그레이션 + 시드) 실행"이 성립한다.
--
-- 반복 마이그레이션(R__)인 이유 두 가지.
--   1) 시드는 고쳐 쓰는 파일이다. 단가와 고객 이름은 앞으로 바뀐다. V__는 한 번 적용되면
--      체크섬 감시 때문에 파일을 못 고치고, 고치면 불일치로 기동이 실패한다. 바꿀 때마다
--      새 버전 파일을 만들어야 한다. R__은 체크섬이 바뀌면 다시 적용해서 파일이 정본이 된다
--   2) 시드는 스키마가 다 갖춰진 뒤에 들어가야 한다. R__은 항상 모든 V__ 뒤에 실행된다.
--      V2__ 시드였다면 클린 DB 재구축 시 V1 -> V2(시드) -> V3(스키마 변경) 순서가 되어,
--      V3가 컬럼을 바꾸면 옛 시드가 그 자리에서 깨진다
--
-- ON CONFLICT가 DO UPDATE인 이유: R__의 의미가 "파일이 곧 상태"라서다. Flyway 공식 문서가
-- 반복 마이그레이션의 멱등성 예시로 드는 CREATE OR REPLACE는 "아무것도 안 하기"가 아니라
-- "파일 내용과 일치시키기"다. 데이터에서 그 짝이 DO UPDATE다. DO NOTHING으로 두면 파일을
-- 고쳐도 기존 행이 남아 에러도 경고도 없이 무시된다(실측). 행 수는 늘지 않으므로 인수 조건
-- "두 번 실행해도 중복 생성되지 않는다"는 그대로 만족한다.
--
-- 운영 적용 여부(MS2-166에서 결정): 이 시드는 운영 DB에도 그대로 적용한다. 제외하지 않은
--   이유는 이번 스토리(MS2-145)의 목표가 배포된 데모이고, 프론트가 조회할 도입사와
--   demo/ CLI, MS2-169의 hook 이벤트 전송이 모두 여기 '데모 도입사'를 기준으로 돌기 때문이다.
--   운영에 실제 고객 데이터가 들어오는 시점에는 프로파일별 spring.flyway.locations로 분리한다.
-- ============================================================================

-- id를 고정하는 이유 두 가지
--   1) 인증을 뺐기 때문에(8/10 결정) 도입사 ID를 요청 헤더로 보낸다. curl 테스트와
--      프론트엔드(MS2-127)가 이 값을 그대로 쓰므로 실행할 때마다 달라지면 안 된다
--   2) id를 생략하면 DEFAULT gen_random_uuid()가 매번 새 행을 만든다. 충돌 자체가 나지
--      않아 ON CONFLICT가 발동하지 못한다. 값을 박아야 재실행이 성립한다
--      (V1의 PK는 중복 행을 막아 주고, ON CONFLICT는 재실행이 에러로 죽지 않게 한다.
--       층이 달라 둘 다 필요하다. ON CONFLICT를 빼고 두 번 돌리면 PK 위반으로 실패한다)
--
-- 값은 UUID 생성기로 뽑은 실제 v4다 (26-08-10 데일리 스크럼 결정). 00000000-...-0001
-- 같은 값은 읽기는 쉬우나 버전 자리가 0이라 v1~v8 어디에도 속하지 않고, 실제 데이터와
-- 형태가 달라 오해를 부른다. 대신 값만 보고는 역할을 알 수 없으므로 행마다 주석을 단다.

-- 이 id가 요청 헤더(X-Organization-Id)에 실려 오는 값이다.
INSERT INTO organization (id, name) VALUES
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', '데모 도입사')
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

-- 고객이 2명인 이유: MS2-124 인수 조건에 "이벤트 없는 고객은 사용량 0, 금액 0"이 있다.
-- 한 명뿐이면 그 케이스를 만들 수 없어서, 이벤트를 받는 고객과 받지 않는 고객을 함께 둔다.
-- (아래 "데모 확장" 절에 고객 3곳이 더 있다. 여기 둘은 기본 시드로 항상 필요한 쪽이다)
--
-- created_at을 여기서 적지 않는 이유(MS2-171): V3의 DEFAULT clock_timestamp()가 채운다.
-- 그래서 이 두 행의 created_at은 고객이 등록된 시각이 아니라 이 DB를 만든 시각이다.
-- 아래 ON CONFLICT의 갱신 대상에도 일부러 넣지 않았다. 넣으면 시드를 다시 돌릴 때마다
-- 두 고객이 "방금 등록됐다"로 바뀐다 (organization_id를 뺀 것과 같은 이유).
INSERT INTO customer (id, organization_id, name) VALUES
  -- 이벤트를 받는 고객. 수집 API 테스트와 데모가 이 id로 이벤트를 보낸다
  ('a728e7b6-d82b-4f3c-a960-a66a02794c1d',
   'd7cee55d-8c82-4afc-b996-6749d8b26a4e', '아크메 주식회사'),
  -- 이벤트가 없는 고객. 사용량 0, 금액 0으로 보이는지 확인하는 쪽이다
  ('252339bc-d5f8-472d-b5d6-ed8554049450',
   'd7cee55d-8c82-4afc-b996-6749d8b26a4e', '베타 스튜디오')
-- organization_id는 일부러 갱신 대상에서 뺐다. 고객이 도입사를 옮기는 것은 테넌트 경계
-- 변경이라 시드 재실행이 조용히 할 일이 아니다. 실제로도 그 고객의 이벤트가 한 건이라도
-- 있으면 복합 FK usage_event_customer_same_org가 막아 migrate가 실패한다(실측). 시드에서
-- 도입사를 바꿔야 할 일이 생기면 그때 별도 마이그레이션으로 다룬다.
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

-- 각 값의 근거
--   event_type      이벤트가 미터를 지목하는 매칭 키. SchemaConstraintTest가 쓰는 값과
--                   맞춰서 테스트와 시드가 따로 놀지 않게 한다
--   code            매칭에 쓰이지 않는 표시용 식별자라, 이 미터가 재는 대상을 그대로 적는다
--   target_property SUM 집계가 properties에서 읽을 키
--
-- target_property가 token이 아닌 미터는 아래 "데모 확장" 절에 있다.
INSERT INTO billable_metric
  (organization_id, code, name, event_type, aggregation, target_property) VALUES
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'token-usage', '토큰 사용량',
   'chat_completion', 'SUM', 'token')
ON CONFLICT (organization_id, code) DO UPDATE SET
  name            = EXCLUDED.name,
  event_type      = EXCLUDED.event_type,
  aggregation     = EXCLUDED.aggregation,
  target_property = EXCLUDED.target_property;

-- 가격 정책과 단가 (MS2-158에서 billable_metric.unit_price를 분리한 자리)
--
-- 정책은 무차원('{}')이다. 모델별 단가 같은 차원은 다차원 후속 스토리에서 이 두 행의
-- 값만 바꿔 켠다 (dimension_properties에 키를 선언하고 조합별 rate 행을 추가).
--
-- 단가는 Anthropic 공시가에서 역산한다(MS2-169). Claude Opus 5 입력 $5/MTok을
-- 1 MTok = 100만 토큰, 1달러 1,400원으로 환산하면 토큰당 0.007원이다
-- (5 x 1400 / 1,000,000). 아래 미터들도 같은 방식으로 계산했다.
--
-- 근거 없는 값을 쓰지 않는 이유는 이 데모의 주장 자체가 "실제로 쓴 만큼 이만큼
-- 청구된다"이기 때문이다. 임의의 단가면 화면의 숫자가 아무것도 말하지 못한다.
--
-- ON CONFLICT 대상이 도입사/고객 시드와 달리 고정 id가 아닌 이유: 두 테이블의 PK가
-- 자연 키(도입사, 미터, 조합)라 값 자체로 충돌이 성립한다. id를 박아야 재실행이
-- 성립하던 organization/customer와 층이 다르다.
INSERT INTO price_policy (organization_id, billable_metric_code) VALUES
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'token-usage')
ON CONFLICT (organization_id, billable_metric_code) DO UPDATE SET
  dimension_properties = EXCLUDED.dimension_properties;

INSERT INTO price_rate (organization_id, billable_metric_code, dimension_values, unit_price) VALUES
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'token-usage', '{}', 0.007)
ON CONFLICT (organization_id, billable_metric_code, dimension_values) DO UPDATE SET
  unit_price = EXCLUDED.unit_price;


-- ============================================================================
-- 데모 확장 (MS2-166에서 편입)
--
-- MS2-142 mock 산출물의 축소판이다. demo/sample-events.csv가 이 고객 id와 event_type으로
-- 이벤트를 보내므로, 이것이 없으면 그 CSV는 100건 전부 미등록 고객으로 거절된다.
--
-- 원래는 demo/seed-customers.sql과 demo/seed-metrics.sql을 psql로 직접 주입했다. 여기로
-- 옮긴 이유는 MS2-164가 RDS에 개발자가 직접 붙지 못하게 잠그기 때문이다. 주입 경로가
-- 사라지면 배포된 환경에서는 데모 CSV를 영영 쓸 수 없게 된다. 고객 등록 API(MS2-155)로
-- 넣는 방법도 검토했지만, 서버가 id를 발급하는 구조라 CSV의 고정 customer_id와 맞지 않는다.
--
-- 미터가 3개인 이유 (2026-08-14 결정, 전체판은 10개다)
--   input-tokens / output-tokens: llm_request 이벤트 하나가 미터 두 개에 잡히는 것을 보여준다
--   network-egress: 소수 수량과 예정액 절사를 보여준다
--
-- 실제 고객 데이터가 들어오는 시점에는 이 절만 프로파일별 spring.flyway.locations로 떼어낸다.
-- 위쪽 기본 시드와 섞지 않고 절을 나눠 둔 것이 그때를 위한 준비다.
-- ============================================================================

-- id는 고객 이름에서 uuid5로 만든 결정론적 값이라 MS2-142 원본과 항상 같다.
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


-- ============================================================================
-- Claude Code 사용량 (MS2-169)
--
-- demo/otel_bridge.py가 보내는 이벤트를 받을 미터다. event_type이 llm_request라
-- 위 "데모 확장"의 input-tokens/output-tokens와 같은 이벤트를 함께 잰다.
-- 이벤트 하나가 미터 넷에 잡히는 셈이고, 그 자체가 미터링 엔진의 동작을 보여준다.
--
-- 캐시 미터가 필요한 이유는 실측 때문이다. Claude Code 요청 한 건을 재 보면
-- input_tokens 2, output_tokens 75인데 cache_read_tokens 33661,
-- cache_creation_tokens 23672였다. 토큰 수로도 비용으로도 대부분이 캐시 쪽이라,
-- 캐시를 빼면 그 요청에서 잴 것이 사실상 없다.
--
-- 단가는 위와 같이 Claude Opus 5 공시가에서 역산했다 (1달러 1,400원).
--   캐시 쓰기(5분) $6.25/MTok -> 0.00875원   캐시 읽기 $0.50/MTok -> 0.0007원
-- 캐시 쓰기가 5분 기준인 이유는 OTel의 cache_creation_tokens가 5분과 1시간을
-- 구분하지 않아서다. 기본값인 5분 쪽을 쓴다.
-- 소수 넷째 자리가 성립하는 것은 unit_price가 NUMERIC이어서다(V2).
--
-- 이 미터들도 무차원('{}')이다. 모델별 단가를 켜려면 dimension_properties에
-- 'model'을 선언하고 조합별 rate를 추가해야 하는데, 지금은 넣어도 읽히지 않는다.
-- DraftInvoiceService가 단가를 얻는 유일한 통로가 PriceRateRepository의
-- findBaseUnitPrices이고 그것이 dimension_values='{}' 행만 읽기 때문이다.
-- 차원별 조회는 MS2-178이 붙인다. 브리지가 properties에 model을 실어 보내므로
-- 그때 필요한 것은 rate 행과 계산 로직뿐이다.
-- ============================================================================

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
