-- ============================================================================
-- V1: S1 코어 테이블 7개 (MS2-26 ERD v2 확정분)
--
-- 근거: docs/erd/erd.md (S1 ERD v2) + 불변 결정 목록 확정본(Notion, 7/29 비준)
--       + docs/api/openapi.yaml (MS2-27)
-- 범위 밖 (이관, 누락 아님):
--   - draft 인보이스/라인아이템 테이블: MS2-41 착수 시 정의
--   - 롤 분리와 권한(REVOKE/GRANT): 담당 스토리 미정, 확정 후 별도
--     마이그레이션으로 실행 (usage_events 절 끝 주석 참조)
--   - SUM 대상 속성 컬럼, 집계 조회용 인덱스: MS2-39
--
-- 각 절 끝의 COMMENT ON은 docs/erd/generated (tbls 생성물)의 설명 칸이 된다.
-- 짧은 근거만 적고 긴 배경은 절 머리의 블록 주석에 남긴다.
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. 도입사 (결정 0)
-- 모든 테넌트 스코프 테이블이 organization_id FK로 이 테이블을 참조한다.
-- 식별 컬럼은 필요해지는 스토리에서 가산적으로 추가한다 (담당 미정).
-- ----------------------------------------------------------------------------
CREATE TABLE organizations (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid()
);

COMMENT ON TABLE organizations IS
  '도입사. 모든 테넌트 스코프 테이블이 organization_id로 이 테이블을 참조한다 (결정 0)';

-- ----------------------------------------------------------------------------
-- 2. API 키 (MS2-27 확정)
-- Bearer 키가 도입사를 암묵 식별한다. 무중단 회전(구 키와 신 키가 동시에
-- 유효한 기간)을 위해 도입사 1 : 키 N -- organizations의 컬럼이면 회전 불가.
--   - key_hash: 평문 저장 금지. 키는 발급 시 1회만 표시, 서버는 해시만 보관
--   - 행 삭제 금지: 폐기는 revoked_at 기록으로 (감사 추적)
--   - S1은 발급/회전 API 없음 (관리자 직접 발급). 발급 API는 가산적으로 추가
-- ----------------------------------------------------------------------------
CREATE TABLE api_keys (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id UUID NOT NULL REFERENCES organizations(id),
  key_hash        TEXT NOT NULL UNIQUE,
  key_prefix      TEXT NOT NULL,
  environment     TEXT NOT NULL
    CHECK (environment IN ('live', 'test')),
  scope           TEXT NOT NULL
    CHECK (scope IN ('events:write', 'read')),
  expires_at      TIMESTAMPTZ,
  revoked_at      TIMESTAMPTZ,
  last_used_at    TIMESTAMPTZ,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- 유효 키 조회(매 요청 인증 경로)용 인덱스는 key_hash UNIQUE가 겸한다.

COMMENT ON TABLE api_keys IS
  'Bearer 인증 키. 무중단 회전을 위해 도입사 1 : 키 N (organizations의 컬럼이면 회전 불가)';
COMMENT ON COLUMN api_keys.key_hash IS
  '평문 저장 금지. 발급 시 1회만 표시하고 서버는 해시만 보관';
COMMENT ON COLUMN api_keys.key_prefix IS
  '화면 식별용 앞자리 (me_sk_live_ 등)';
COMMENT ON COLUMN api_keys.expires_at IS
  '회전 시 구 키 유예기간. NULL이면 무기한';
COMMENT ON COLUMN api_keys.revoked_at IS
  '즉시 폐기. 행 삭제 대신 이 값을 기록한다 (감사 추적)';
COMMENT ON COLUMN api_keys.last_used_at IS
  '회전 후 구 키가 아직 쓰이는지 판단하는 근거';

-- ----------------------------------------------------------------------------
-- 3. 제어면 멱등키 기록 (MS2-27 공통 규약 4.2)
-- Idempotency-Key 헤더를 받은 변경 요청의 처리 기록.
--   - 완료 후 같은 키 재전송: 저장된 원 응답 재생 (실패 응답도 재생 대상)
--   - 처리 중 같은 키 재전송: 409
--   - 같은 키, 다른 내용(request_fingerprint 불일치): 422 idempotency_key_reuse
--   - 보존: 최소 24시간. 만료 행 정리 방식(배치 등)은 미정 (담당 스토리 확정 필요)
-- response_body는 TEXT다 -- 원 응답을 바이트 그대로 재생해야 하므로
-- 재직렬화가 생기는 JSONB를 쓰지 않는다.
-- ----------------------------------------------------------------------------
CREATE TABLE idempotency_keys (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id     UUID NOT NULL REFERENCES organizations(id),
  endpoint            TEXT NOT NULL,
  idempotency_key     TEXT NOT NULL
    CHECK (idempotency_key <> '' AND length(idempotency_key) <= 255),
  request_fingerprint TEXT NOT NULL,
  status              TEXT NOT NULL
    CHECK (status IN ('processing', 'completed')),
  response_status     INTEGER,
  response_body       TEXT,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

  -- 키 유효 범위는 (도입사, 엔드포인트, 키). 같은 키를 다른 엔드포인트에
  -- 써도 서로 간섭하지 않는다 (MS2-27 공통 규약 4.2)
  CONSTRAINT idempotency_keys_scope_unique
    UNIQUE (organization_id, endpoint, idempotency_key),

  -- 완료 행은 재생할 응답을 반드시 가진다
  CONSTRAINT idempotency_keys_completed_has_response
    CHECK (status <> 'completed'
           OR (response_status IS NOT NULL AND response_body IS NOT NULL))
);

COMMENT ON TABLE idempotency_keys IS
  '제어면 멱등키 처리 기록. 보존 최소 24시간 (MS2-27 공통 규약 4.2)';
COMMENT ON COLUMN idempotency_keys.request_fingerprint IS
  '같은 키에 다른 내용이면 422 idempotency_key_reuse';
COMMENT ON COLUMN idempotency_keys.response_body IS
  '원 응답을 바이트 그대로 재생해야 하므로 재직렬화가 생기는 JSONB를 쓰지 않는다';
COMMENT ON CONSTRAINT idempotency_keys_scope_unique ON idempotency_keys IS
  '키 유효 범위는 (도입사, 엔드포인트, 키). 엔드포인트가 다르면 서로 간섭하지 않는다';
COMMENT ON CONSTRAINT idempotency_keys_completed_has_response ON idempotency_keys IS
  '완료 행은 재생할 응답을 반드시 가진다';

-- ----------------------------------------------------------------------------
-- 4. 고객 (결정 0, 6 + MS2-27)
-- external_id는 도입사가 지은 이름. soft delete 후 같은 이름 재사용 허용 --
-- 부분 유니크 인덱스로 살아있는 행끼리만 유일성을 강제한다.
-- 과거 이벤트의 귀속은 UUID(id)에 고정되므로 재사용해도 안전하다 (결정 1-B).
-- name과 external_id의 길이 상한(1~255자)은 API 경계에서 검증한다 (MS2-27).
-- ----------------------------------------------------------------------------
CREATE TABLE customers (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id UUID NOT NULL REFERENCES organizations(id),
  external_id     TEXT NOT NULL,
  name            TEXT NOT NULL,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at      TIMESTAMPTZ,

  -- 테넌트 교차 참조 차단용 복합 FK(usage_events)의 참조 대상. id가 PK라
  -- 조합의 유일성은 자명하고, FK가 참조하려면 형식상 필요하다 (PR #13 리뷰)
  CONSTRAINT customers_org_id_unique UNIQUE (organization_id, id)
);

CREATE UNIQUE INDEX customers_org_external_id_alive
  ON customers (organization_id, external_id)
  WHERE deleted_at IS NULL;

COMMENT ON TABLE customers IS
  '고객. soft delete 후 external_id 재사용을 허용한다 (결정 6)';
COMMENT ON COLUMN customers.external_id IS
  '도입사가 지은 이름. 살아있는 행끼리만 유일';
COMMENT ON COLUMN customers.deleted_at IS
  'soft delete. 과거 이벤트의 귀속은 id(UUID)에 고정되므로 이름을 재사용해도 안전하다 (결정 1-B)';
COMMENT ON CONSTRAINT customers_org_id_unique ON customers IS
  '복합 FK 참조 대상. id가 PK라 유일성은 자명하고 FK가 참조하려면 형식상 필요하다 (PR #13 리뷰)';
COMMENT ON INDEX customers_org_external_id_alive IS
  '부분 유니크: 살아있는 행끼리만 external_id가 유일하다';

-- ----------------------------------------------------------------------------
-- 5. 미터 (결정 0, 6 + MS2-27)
-- code는 도입사가 지은 카탈로그 이름표. 이벤트가 code를 실어 집계 규칙을 지목한다.
-- SUM의 대상 속성 컬럼: API 표면은 MS2-27이 field_name 필수 입력으로 확정했고,
-- DB 컬럼명과 제약은 MS2-39가 명세와 함께 확정한다 (의도된 이관).
-- 미터 버전화 여부도 MS2-39 착수 시 확정한다.
-- ----------------------------------------------------------------------------
CREATE TABLE meters (
  id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id  UUID NOT NULL REFERENCES organizations(id),
  code             TEXT NOT NULL,
  name             TEXT NOT NULL,
  aggregation_type TEXT NOT NULL
    CHECK (aggregation_type IN ('COUNT', 'SUM')),
  created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
  deleted_at       TIMESTAMPTZ,

  -- 테넌트 교차 참조 차단용 복합 FK(usage_events, price_policies)의 참조 대상
  -- (customers의 같은 제약 참조, PR #13 리뷰)
  CONSTRAINT meters_org_id_unique UNIQUE (organization_id, id)
);

CREATE UNIQUE INDEX meters_org_code_alive
  ON meters (organization_id, code)
  WHERE deleted_at IS NULL;

COMMENT ON TABLE meters IS
  '미터. 이벤트가 code를 실어 집계 규칙을 지목한다 (결정 6)';
COMMENT ON COLUMN meters.code IS
  '도입사가 지은 카탈로그 이름표. 살아있는 행끼리만 유일';
COMMENT ON COLUMN meters.aggregation_type IS
  'SUM 대상 속성 컬럼과 미터 버전화 여부는 MS2-39에서 확정한다';
COMMENT ON COLUMN meters.deleted_at IS
  'soft delete. 과거 이벤트의 귀속은 id(UUID)에 고정되므로 이름을 재사용해도 안전하다 (결정 1-B)';
COMMENT ON CONSTRAINT meters_org_id_unique ON meters IS
  '복합 FK 참조 대상 (customers의 같은 제약 참조, PR #13 리뷰)';
COMMENT ON INDEX meters_org_code_alive IS
  '부분 유니크: 살아있는 행끼리만 code가 유일하다';

-- ----------------------------------------------------------------------------
-- 6. 가격정책 (결정 5, 7 + MS2-27)
-- 단가는 (단가 BIGINT, 묶음 수량 BIGINT) 쌍. "1,000토큰당 4원" = (4, 1000).
-- per-unit은 unit_size = 1의 특수 케이스다.
-- 결정 7 (최소 불변): 단가 변경은 이력이 남아야 한다. 이력 없는 덮어쓰기 금지.
--   버전 row(effective_from) vs 별도 이력 테이블은 MS2-40 착수 시 결정한다.
--   그 전까지 단가 수정이 필요하면 새 정책 행 추가로만 한다.
-- created_at: 행이 불변이므로 갱신 시각이 아니라 생성 시각이다.
--   API 응답의 updated_at(MS2-27)은 현재 유효 행의 created_at으로 채운다.
-- 확장 여지: graduated/volume 등 가격 모델 타입 필드와 currency 컬럼은
--   후속 슬라이스에서 추가 (S1은 per-unit, KRW 고정 -- 2026-07-30 합의로 제외).
-- soft delete 표현은 MS2-40 이력 설계와 함께 확정한다 (S1은 삭제 없음).
-- ----------------------------------------------------------------------------
CREATE TABLE price_policies (
  id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id UUID NOT NULL REFERENCES organizations(id),
  meter_id        UUID NOT NULL,
  unit_price_krw  BIGINT NOT NULL CHECK (unit_price_krw >= 0),
  unit_size       BIGINT NOT NULL DEFAULT 1 CHECK (unit_size >= 1),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

  -- 다른 도입사의 미터에 가격정책이 붙는 것을 DB가 차단한다 (PR #13 리뷰)
  CONSTRAINT price_policies_meter_same_org
    FOREIGN KEY (organization_id, meter_id)
    REFERENCES meters (organization_id, id)
);

COMMENT ON TABLE price_policies IS
  '가격정책. 단가 변경은 이력이 남아야 한다. 이력 없는 덮어쓰기 금지 (결정 7)';
COMMENT ON COLUMN price_policies.unit_price_krw IS
  '묶음 수량당 단가. 1,000토큰당 4원이면 (4, 1000)';
COMMENT ON COLUMN price_policies.unit_size IS
  '묶음 수량. per-unit은 unit_size = 1의 특수 케이스';
COMMENT ON COLUMN price_policies.created_at IS
  '행이 불변이므로 갱신 시각이 아니라 생성 시각';
COMMENT ON CONSTRAINT price_policies_meter_same_org ON price_policies IS
  '다른 도입사의 미터에 가격정책이 붙는 것을 DB가 차단한다 (PR #13 리뷰)';

-- ----------------------------------------------------------------------------
-- 7. 사용량 이벤트 (결정 0, 1, 1-B, 2, 3, 6)
-- raw 청구 근거. append-only이며 아래 가드 트리거로 DB가 강제한다
-- (롤 권한은 롤 분리 확정 후 추가 방어로 실행 -- 절 끝 주석 참조).
--   - 원문 보존: external_customer_id, code는 받은 그대로 (결정 1-B)
--   - 수신 시 해소: customer_id, meter_id는 수신 시 이름 조회로 찾은 UUID,
--     못 찾으면 NULL (미해소 -- 집계 제외, 관리자 화면에 건수 노출)
--   - received_at은 서버가 찍는다. BEFORE INSERT 트리거가 항상 DB 서버
--     시각으로 덮어쓴다 -- 앱이 값을 넣어도 무시 (아래 트리거 주석 참조)
--   - properties 키 정책은 MS2-27 확정: 키 50개, 이름 1~64자, 값은
--     문자열/숫자/불리언/null, 중첩은 저장하되 집계 대상 아님.
--     검증은 API 경계에서 하고 DB는 JSONB 그대로 보존한다
-- ----------------------------------------------------------------------------
CREATE TABLE usage_events (
  id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  organization_id      UUID NOT NULL REFERENCES organizations(id),
  transaction_id       TEXT NOT NULL
    CHECK (transaction_id <> '' AND length(transaction_id) <= 255),
  external_customer_id TEXT NOT NULL,
  customer_id          UUID,
  code                 TEXT NOT NULL,
  meter_id             UUID,
  properties           JSONB NOT NULL DEFAULT '{}',
  occurred_at          TIMESTAMPTZ NOT NULL,
  received_at          TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),

  -- 멱등키 (결정 1): 도입사 안에서 무기한 유일, 기간 윈도우 없음.
  -- 같은 transaction_id 재전송은 이 제약이 거르고 앱은 성공 응답만 반환
  -- (first-write-wins, 결정 4). 응답 코드는 MS2-27 확정: 201 신규 / 200 기존 반환.
  CONSTRAINT usage_events_org_transaction_id_unique
    UNIQUE (organization_id, transaction_id),

  -- 테넌트 교차 참조 차단 (PR #13 리뷰): (도입사, 고객/미터) 조합으로 검사해
  -- 해소 로직에 버그가 있어도 다른 도입사의 고객/미터에 귀속되지 못하게 한다.
  -- 구성 컬럼에 NULL이 있으면 검사를 건너뛰므로(MATCH SIMPLE 기본 동작)
  -- 미해소 이벤트(customer_id/meter_id NULL) 저장에는 영향이 없다.
  CONSTRAINT usage_events_customer_same_org
    FOREIGN KEY (organization_id, customer_id)
    REFERENCES customers (organization_id, id),
  CONSTRAINT usage_events_meter_same_org
    FOREIGN KEY (organization_id, meter_id)
    REFERENCES meters (organization_id, id)
);

-- received_at 강제 (결정 2 "클라이언트 입력 불가", PR #13 리뷰):
-- DEFAULT는 컬럼을 생략한 INSERT에만 적용되어 앱이 값을 넣으면 그대로
-- 저장된다 (ORM이 전체 필드를 INSERT에 넣으면 바로 그 경로). 트리거가
-- 항상 DB 서버 시각으로 덮어써 received_at을 서버의 사실로 만든다.
-- DEFAULT는 트리거가 해제된 경우의 안전망으로 유지한다.
-- 재검토 조건: 수집 경로가 비동기(큐 도입 등)로 바뀌면 앱 도착과 DB 저장의
-- 간격이 커지므로 수신 시각의 의미(앱 수신 vs DB 저장)를 재정의한다.
CREATE FUNCTION usage_events_set_received_at() RETURNS trigger AS $$
BEGIN
  NEW.received_at := clock_timestamp();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER usage_events_set_received_at
  BEFORE INSERT ON usage_events
  FOR EACH ROW EXECUTE FUNCTION usage_events_set_received_at();

-- 가드 트리거 (결정 1-B 재연결 + 결정 3):
-- UPDATE는 customer_id, meter_id의 NULL -> 값 1회 채움만 허용한다.
-- 값 -> 다른 값, 값 -> NULL, 그 외 컬럼 변경은 전부 거부한다.
-- 허용 두 컬럼을 뺀 행 전체를 비교하므로 이후 추가되는 컬럼도 자동으로
-- 보호된다 (PR #13 리뷰: 나열식은 컬럼 추가 시 함수 갱신을 잊으면
-- 그 컬럼만 조용히 수정 가능해지고 기존 테스트로는 잡히지 않는다).
CREATE FUNCTION usage_events_guard_update() RETURNS trigger AS $$
BEGIN
  IF to_jsonb(NEW) - 'customer_id' - 'meter_id'
     IS DISTINCT FROM to_jsonb(OLD) - 'customer_id' - 'meter_id' THEN
    RAISE EXCEPTION 'usage_events is append-only: only customer_id/meter_id may be filled (결정 3)';
  END IF;

  IF OLD.customer_id IS NOT NULL AND NEW.customer_id IS DISTINCT FROM OLD.customer_id THEN
    RAISE EXCEPTION 'customer_id may only be filled once from NULL (결정 1-B)';
  END IF;

  IF OLD.meter_id IS NOT NULL AND NEW.meter_id IS DISTINCT FROM OLD.meter_id THEN
    RAISE EXCEPTION 'meter_id may only be filled once from NULL (결정 1-B)';
  END IF;

  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER usage_events_guard_update
  BEFORE UPDATE ON usage_events
  FOR EACH ROW EXECUTE FUNCTION usage_events_guard_update();

-- 가드 트리거 (결정 3, PR #13 리뷰): DELETE와 TRUNCATE는 전면 차단한다.
-- TRUNCATE는 행 트리거를 타지 않으므로 문장 트리거를 따로 건다.
-- S1 실험 데이터 등 수동 정리(결정 3의 "수동 절차로만 하고 기록")가 필요하면
-- 테이블 소유자가 트리거를 잠시 해제하고 삭제한 뒤 재활성화한다:
--   ALTER TABLE usage_events DISABLE TRIGGER usage_events_guard_delete;
--   DELETE FROM usage_events WHERE ...;
--   ALTER TABLE usage_events ENABLE TRIGGER usage_events_guard_delete;
CREATE FUNCTION usage_events_guard_delete() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'usage_events is append-only: DELETE/TRUNCATE is blocked (결정 3)';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER usage_events_guard_delete
  BEFORE DELETE ON usage_events
  FOR EACH ROW EXECUTE FUNCTION usage_events_guard_delete();

CREATE TRIGGER usage_events_guard_truncate
  BEFORE TRUNCATE ON usage_events
  FOR EACH STATEMENT EXECUTE FUNCTION usage_events_guard_delete();

COMMENT ON TABLE usage_events IS
  'raw 청구 근거. append-only이며 가드 트리거로 DB가 강제한다 (결정 3)';
COMMENT ON COLUMN usage_events.transaction_id IS
  '멱등키. 도입사 안에서 무기한 유일, 기간 윈도우 없음 (결정 1)';
COMMENT ON COLUMN usage_events.external_customer_id IS
  '받은 원문 그대로 보존 (결정 1-B)';
COMMENT ON COLUMN usage_events.customer_id IS
  '수신 시 해소한 UUID. 못 찾으면 NULL (미해소, 집계 제외)';
COMMENT ON COLUMN usage_events.code IS
  '받은 원문 그대로 보존 (결정 1-B)';
COMMENT ON COLUMN usage_events.meter_id IS
  '수신 시 해소한 UUID. 못 찾으면 NULL (미해소, 집계 제외)';
COMMENT ON COLUMN usage_events.properties IS
  '키 정책 검증은 API 경계에서 하고 DB는 원문을 보존한다 (MS2-27)';
COMMENT ON COLUMN usage_events.occurred_at IS
  '발생 시각. 계산 기준 (결정 2)';
COMMENT ON COLUMN usage_events.received_at IS
  '수신 시각. 트리거가 항상 DB 서버 시각으로 덮어쓴다 (결정 2)';
COMMENT ON CONSTRAINT usage_events_org_transaction_id_unique ON usage_events IS
  '재전송은 이 제약이 거른다 (결정 1)';
COMMENT ON CONSTRAINT usage_events_customer_same_org ON usage_events IS
  '테넌트 교차 참조 차단. 구성 컬럼에 NULL이 있으면 검사를 건너뛰므로 미해소 저장에는 영향이 없다 (PR #13 리뷰)';
COMMENT ON CONSTRAINT usage_events_meter_same_org ON usage_events IS
  '테넌트 교차 참조 차단 (PR #13 리뷰)';
COMMENT ON TRIGGER usage_events_set_received_at ON usage_events IS
  'received_at을 항상 DB 서버 시각으로 덮어쓴다 (결정 2)';
COMMENT ON TRIGGER usage_events_guard_update ON usage_events IS
  '허용 두 컬럼을 뺀 행 전체를 비교해 UPDATE를 차단한다 (결정 3)';
COMMENT ON TRIGGER usage_events_guard_delete ON usage_events IS
  'DELETE 차단 (결정 3)';
COMMENT ON TRIGGER usage_events_guard_truncate ON usage_events IS
  'TRUNCATE 차단. 행 트리거를 타지 않아 문장 트리거로 따로 건다 (결정 3)';

-- 롤 권한 (결정 3 -- 롤 분리 후 별도 마이그레이션으로 실행. 담당 스토리는 미정:
-- 현재 로컬/CI 구성은 단일 계정(meterengine)이라 롤 분리 전제가 없다.
-- 전제: app_role은 테이블 소유자가 아님):
--   REVOKE UPDATE, DELETE, TRUNCATE ON usage_events FROM app_role;
--   GRANT UPDATE (customer_id, meter_id) ON usage_events TO app_role;
-- 위 가드 트리거가 1차 차단이고 롤 권한은 추가 방어다 (PR #13 리뷰로 변경:
-- 종전 "DELETE 차단은 롤 권한으로만"에서 트리거 차단 우선으로).
-- 트리거 우회 방지: app_role에는 트리거 disable 권한(테이블 소유권)을 주지 않는다.

-- 집계 조회용 인덱스는 MS2-39 착수 시 접근 패턴 확정 후 추가한다 (가산적).
-- 그때 함께 볼 것: 미해소 건수 조회(MS2-27 확정 unresolved_events_count)는
--   (organization_id, external_customer_id, occurred_at) + customer_id/meter_id
--   IS NULL 패턴이라 부분 인덱스 후보다.
