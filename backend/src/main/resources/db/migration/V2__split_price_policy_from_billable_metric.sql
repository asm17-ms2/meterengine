-- ============================================================================
-- V2: 요금을 billable_metric에서 가격 정책/단가 테이블로 분리 (MS2-158, 스토리 MS2-147)
--
-- 목표 형태는 2026-08-19 팀 합의(다차원 가격 정책 대비 2테이블)다. 이번 슬라이스는
-- 스키마만 그 형태로 만들고 동작은 바꾸지 않는다. 모든 정책은 무차원('{}')이고
-- 미터당 단가는 '{}' 조합 1행이라, 계산 결과와 API 응답은 V1과 동일하다.
--
-- 범위 밖 (이관, 누락 아님):
--   - 차원을 실제로 쓰는 계산(축별 집계, 조합당 인보이스 라인): 다차원 후속 스토리
--   - 등록 API와 dimension_values 키 집합 검증: MS2-157 (JSONB 키 오타는 DB가
--     못 잡으므로 등록 경계의 앱 검증이 방어선이다)
--   - 단가 변경 이력(effective_from 류), 와일드카드/부분 매칭, 통화, 구간제: 미정
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. 가격 정책 (미터당 1행)
-- 가격에 관한 사실의 앵커다. dimension_properties는 이 미터의 가격을 가르는
-- 이벤트 속성 키의 집합이고(무차원이면 '{}'), 배열이지만 순서에 의미를 두지
-- 않는다 -- 단가 행이 키-값(JSONB)이라 위치 대응이 필요 없다.
-- PK가 (organization_id, metric_code)라 미터당 정책이 1개로 강제되고, 같은 두
-- 열의 복합 FK가 미터 존재와 테넌트 경계를 함께 강제한다
-- (V1 usage_event_customer_same_org와 같은 패턴).
-- ----------------------------------------------------------------------------
CREATE TABLE price_policy (
  organization_id      UUID NOT NULL REFERENCES organization(id),
  metric_code          VARCHAR NOT NULL,
  dimension_properties TEXT[] NOT NULL DEFAULT '{}',

  PRIMARY KEY (organization_id, metric_code),

  CONSTRAINT price_policy_metric_same_org
    FOREIGN KEY (organization_id, metric_code)
    REFERENCES billable_metric (organization_id, code)
);

-- ----------------------------------------------------------------------------
-- 2. 단가 (조합당 1행)
-- dimension_values는 {"model": "opus5"} 같은 키-값 조합이고, 무차원 미터와
-- 기본 단가는 빈 객체 '{}'다. JSONB가 PK에 들어가므로 조합 유일성이 구조로
-- 보장되고, JSONB는 키 순서를 정규화하므로 표기만 다른 같은 조합도 막힌다.
-- FK 과녁이 billable_metric이 아니라 price_policy인 이유: 단가는 축 선언 없이
-- 해석할 수 없어서, 선언 없는 단가는 계산이 도달하지 못하는 죽은 행이 되고
-- 조용히 기본 단가로 과금된다. FK가 그 상태를 입력 시점의 실패로 바꾼다.
-- 미터 존재와 same-org는 policy의 FK를 통해 이행적으로 보장된다.
-- unit_price가 NUMERIC인 이유는 V1과 같다(소수 단가 허용, 정수 연산 보장은
-- 계산 로직 소관 -- 스토리 MS2-121 팀 정책).
-- ----------------------------------------------------------------------------
CREATE TABLE price_rate (
  organization_id  UUID NOT NULL REFERENCES organization(id),
  metric_code      VARCHAR NOT NULL,
  dimension_values JSONB NOT NULL,
  unit_price       NUMERIC NOT NULL CHECK (unit_price >= 0),

  PRIMARY KEY (organization_id, metric_code, dimension_values),

  CONSTRAINT price_rate_policy_fk
    FOREIGN KEY (organization_id, metric_code)
    REFERENCES price_policy (organization_id, metric_code)
);

-- ----------------------------------------------------------------------------
-- 3. 기존 단가 이관
-- 미터마다 무차원 정책과 '{}' 조합 단가 1행을 만든다. 값은 그대로 옮기므로
-- 계산 결과가 바뀌지 않는다.
-- ----------------------------------------------------------------------------
INSERT INTO price_policy (organization_id, metric_code)
SELECT organization_id, code FROM billable_metric;

INSERT INTO price_rate (organization_id, metric_code, dimension_values, unit_price)
SELECT organization_id, code, '{}'::jsonb, unit_price FROM billable_metric;

-- ----------------------------------------------------------------------------
-- 4. 옛 자리 제거
-- 단가의 정본이 price_rate로 옮겨졌으므로 남겨 두면 정본이 둘이 된다.
-- ----------------------------------------------------------------------------
ALTER TABLE billable_metric DROP COLUMN unit_price;
