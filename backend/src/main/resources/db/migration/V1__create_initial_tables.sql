-- ============================================================================
-- V1: 초기 4테이블 (MS2-123, 스토리 MS2-121 첫 슬라이스)
--
-- 범위 밖 (이관, 누락 아님):
--   - API 키 저장(organization.api_key 등): 인증 자체가 이번 슬라이스에서 제외
--   - billable_metric.group_key, 연월일 전용 일자 컬럼: 검토 후 제외
--   - 인보이스/집계 스냅샷 테이블: 후속 슬라이스에서 정의
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. 도입사
-- 모든 테넌트 스코프 테이블이 organization_id FK로 이 테이블을 참조한다.
-- ----------------------------------------------------------------------------
CREATE TABLE organization (
  id   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  name VARCHAR NOT NULL
);

-- ----------------------------------------------------------------------------
-- 2. 과금 지표
-- 이벤트의 event_type이 이 테이블의 event_type과 매칭되어 집계 규칙과 단가를
-- 지목한다 (FK 없는 논리 매칭 -- 이벤트는 원문 보존이 우선이라 지표 변경에
-- 영향받지 않는다).
-- unit_price는 소수 단가(토큰당 1원 미만)를 허용하기 위해 NUMERIC이다.
-- 금액 계산의 정수 연산 보장은 계산 로직 소관 (스토리 MS2-121 팀 정책).
-- target_property는 SUM 집계가 properties에서 읽을 키 이름이라 COUNT면 없다.
-- ----------------------------------------------------------------------------
CREATE TABLE billable_metric (
  organization_id UUID NOT NULL REFERENCES organization(id),
  code            VARCHAR NOT NULL,
  name            VARCHAR NOT NULL,
  event_type      VARCHAR NOT NULL,
  aggregation     VARCHAR NOT NULL,
  target_property VARCHAR,
  unit_price      NUMERIC NOT NULL CHECK (unit_price >= 0),

  PRIMARY KEY (organization_id, code)
);

-- ----------------------------------------------------------------------------
-- 3. 고객
-- 이벤트는 customer의 id(uuid)를 직접 실어 보낸다 (이름 해소 없음).
-- ----------------------------------------------------------------------------
CREATE TABLE customer (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id UUID NOT NULL REFERENCES organization(id),
  name            VARCHAR NOT NULL,

  -- 테넌트 교차 참조 차단용 복합 FK(usage_event)의 참조 대상. id가 PK라
  -- 조합의 유일성은 자명하고, FK가 참조하려면 형식상 필요하다
  CONSTRAINT customer_org_id_unique UNIQUE (organization_id, id)
);

-- ----------------------------------------------------------------------------
-- 4. 사용량 이벤트
-- raw 청구 근거. PK가 (organization_id, transaction_id)라 같은 도입사 안에서
-- 같은 transaction_id의 재전송은 PK 위반으로 걸러진다 (멱등, 기간 윈도우 없음).
-- 사용량 값(token 등)은 별도 컬럼 없이 properties에 담고, 집계가
-- billable_metric.target_property로 꺼내 읽는다.
-- received_at은 서버가 찍는다. DEFAULT는 컬럼을 생략한 INSERT에만 적용되므로
-- 강제는 후속 트리거가 맡고, DEFAULT는 트리거 해제 시의 안전망이다.
-- ----------------------------------------------------------------------------
CREATE TABLE usage_event (
  organization_id UUID NOT NULL REFERENCES organization(id),
  transaction_id  VARCHAR NOT NULL,
  customer_id     UUID NOT NULL,
  event_type      VARCHAR NOT NULL,
  properties      JSONB NOT NULL DEFAULT '{}',
  occurred_at     TIMESTAMPTZ NOT NULL,
  received_at     TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),

  PRIMARY KEY (organization_id, transaction_id),

  -- 테넌트 교차 참조 차단: (도입사, 고객) 조합으로 검사해 귀속 로직에 버그가
  -- 있어도 다른 도입사의 고객에 이벤트가 귀속되지 못하게 한다 (스토리 MS2-121
  -- 팀 정책 "도입사 스코프 자체는 뺄 수 없다"의 DB 강제)
  CONSTRAINT usage_event_customer_same_org
    FOREIGN KEY (organization_id, customer_id)
    REFERENCES customer (organization_id, id)
);

-- received_at 강제 (스토리 MS2-121 팀 정책 "클라이언트 값은 무시한다"):
-- DEFAULT는 컬럼을 생략한 INSERT에만 적용되어 앱이 값을 넣으면 그대로
-- 저장된다 (ORM이 전체 필드를 INSERT에 넣으면 바로 그 경로). 트리거가
-- 항상 DB 서버 시각으로 덮어써 received_at을 서버의 사실로 만든다.
-- DEFAULT는 트리거가 해제된 경우의 안전망으로 유지한다.
CREATE FUNCTION usage_event_set_received_at() RETURNS trigger AS $$
BEGIN
  NEW.received_at := clock_timestamp();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER usage_event_set_received_at
  BEFORE INSERT ON usage_event
  FOR EACH ROW EXECUTE FUNCTION usage_event_set_received_at();

-- 불변 가드 (스토리 MS2-121 팀 정책 "저장된 이벤트는 UPDATE/DELETE 되지
-- 않는다. DB가 막는다"): raw 청구 근거는 append-only다. 정정이 필요하면
-- 수정이 아니라 새 이벤트로 한다.
-- TRUNCATE는 행 트리거를 타지 않으므로 문장 트리거를 따로 건다.
CREATE FUNCTION usage_event_block_mutation() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'usage_event is append-only: UPDATE/DELETE/TRUNCATE is blocked';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER usage_event_block_update
  BEFORE UPDATE ON usage_event
  FOR EACH ROW EXECUTE FUNCTION usage_event_block_mutation();

CREATE TRIGGER usage_event_block_delete
  BEFORE DELETE ON usage_event
  FOR EACH ROW EXECUTE FUNCTION usage_event_block_mutation();

CREATE TRIGGER usage_event_block_truncate
  BEFORE TRUNCATE ON usage_event
  FOR EACH STATEMENT EXECUTE FUNCTION usage_event_block_mutation();
