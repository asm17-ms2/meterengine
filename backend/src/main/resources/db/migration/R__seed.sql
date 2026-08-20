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
-- TODO(배포 시점): 이 시드는 배포 환경에도 함께 적용된다. 8/10 플래닝에서 이번
--   스프린트는 로컬 데모만 하기로 해서 지금은 문제가 없다. 배포가 생기는 스프린트에서
--   운영 프로파일 제외를 검토한다.
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
-- TODO(후속 스토리): target_property가 token이 아닌 미터가 생기면 행을 추가한다.
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
-- 단가는 토큰당 0.5원. MS2-128의 "100건 중 80건 반영" 검증에서 토큰 500짜리
-- 이벤트면 40,000토큰에 20,000원이라 기댓값이 암산된다.
--
-- ON CONFLICT 대상이 도입사/고객 시드와 달리 고정 id가 아닌 이유: 두 테이블의 PK가
-- 자연 키(도입사, 미터, 조합)라 값 자체로 충돌이 성립한다. id를 박아야 재실행이
-- 성립하던 organization/customer와 층이 다르다.
INSERT INTO price_policy (organization_id, metric_code) VALUES
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'token-usage')
ON CONFLICT (organization_id, metric_code) DO UPDATE SET
  dimension_properties = EXCLUDED.dimension_properties;

INSERT INTO price_rate (organization_id, metric_code, dimension_values, unit_price) VALUES
  ('d7cee55d-8c82-4afc-b996-6749d8b26a4e', 'token-usage', '{}', 0.5)
ON CONFLICT (organization_id, metric_code, dimension_values) DO UPDATE SET
  unit_price = EXCLUDED.unit_price;
