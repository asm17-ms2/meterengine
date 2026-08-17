package com.meterengine.metric.repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 고객별 사용량 합산 (MS2-129). 집계 서비스가 미터 하나의 고객별 합을 얻는 통로다.
 *
 * <p>JPA를 쓰지 않는다. usage_event는 append-only 트리거가 걸려 있어 엔티티 매핑 없이 관리되는 테이블이고, properties(jsonb)에서
 * 파라미터로 고른 키를 합산하는 이 쿼리는 JPQL로 표현할 수 없다.
 */
@Repository
public class UsageAggregationRepository {

  private final JdbcTemplate jdbc;

  UsageAggregationRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * 기간 안의 이벤트를 고객별로 합산한다 (MS2-129).
   *
   * <p>합산 대상은 properties에서 targetProperty 키로 꺼낸 값이다. 어느 키인지는 미터의 target_property가 정한다.
   *
   * <p><b>기간은 반열린 구간 [start, end)다.</b> occurred_at이 TIMESTAMPTZ라 이 비교는 절대 시각으로 이뤄진다. 그래서 호출자가 KST
   * 월 경계를 넘겨주면 2026-08-31T23:59:59+09:00은 8월에, 2026-09-01T00:00:00+09:00은 9월에 귀속되고, 같은 순간을 UTC 표기로
   * 보낸 이벤트도 결과가 같다 (하위작업 인수 기준). SQL에서 AT TIME ZONE으로 월을 뽑지 않는 이유는 컬럼에 함수를 씌우면 나중에 붙일 인덱스를 타지 못하고
   * 서버 timezone 설정에 결과가 흔들리기 때문이다.
   *
   * <p><b>jsonb_typeof 필터가 숫자가 아닌 값을 걸러낸다.</b> 수집 API는 properties의 내용을 검증하지 않으므로 token이 문자열이거나 아예
   * 없는 이벤트가 저장돼 있을 수 있다. 필터가 없으면 그런 이벤트 한 건이 numeric 캐스팅에서 터져 조회 전체가 500이 된다 (MS2-129 팀 결정: 합계에서
   * 제외).
   *
   * <p>{@code ?::text} 캐스팅은 {@code ->>}에 text/integer 오버로드가 있어서다. 파라미터 타입을 명시하지 않으면 어느 연산자인지 모호해진다.
   *
   * @return 합이 0보다 큰 고객만이 아니라 이벤트가 한 건이라도 잡힌 고객의 합. 이벤트가 없는 고객은 키 자체가 없으므로 호출자가 0으로 채운다
   */
  public Map<UUID, BigDecimal> sumByCustomer(
      UUID organizationId,
      String eventType,
      String targetProperty,
      OffsetDateTime start,
      OffsetDateTime end) {
    List<Map.Entry<UUID, BigDecimal>> rows =
        jdbc.query(
            """
            SELECT customer_id, SUM((properties ->> ?::text)::numeric) AS quantity
            FROM usage_event
            WHERE organization_id = ?
              AND event_type = ?
              AND occurred_at >= ?
              AND occurred_at < ?
              AND jsonb_typeof(properties -> ?::text) = 'number'
            GROUP BY customer_id
            """,
            (rs, rowNum) ->
                Map.entry(rs.getObject("customer_id", UUID.class), rs.getBigDecimal("quantity")),
            targetProperty,
            organizationId,
            eventType,
            start,
            end,
            targetProperty);

    Map<UUID, BigDecimal> sums = new HashMap<>();
    rows.forEach(row -> sums.put(row.getKey(), row.getValue()));
    return sums;
  }
}
