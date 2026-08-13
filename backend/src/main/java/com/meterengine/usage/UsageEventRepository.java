package com.meterengine.usage;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 사용량 이벤트 저장 (MS2-130)과 집계 조회 (MS2-129).
 *
 * <p>JPA를 쓰지 않는다. usage_event는 append-only 트리거가 걸려 있어 영속성 컨텍스트의 dirty checking이 UPDATE를 내보내는 순간 예외가
 * 난다. ON CONFLICT DO NOTHING은 save()로 표현할 수 없고, PK가 복합이라 @IdClass도 필요하다. 얻을 것보다 우회할 것이 많다.
 */
@Repository
class UsageEventRepository {

  private final JdbcTemplate jdbc;

  UsageEventRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * 이벤트를 저장한다. 이미 같은 (도입사, transaction_id)가 있으면 아무것도 하지 않는다.
   *
   * <p>PK가 (organization_id, transaction_id)라 ON CONFLICT DO NOTHING 한 문장이 멱등을 만든다. 단일 문장이라 동시에 같은
   * 키가 와도 DB가 직렬화한다. 조회 후 삽입으로 나누면 그 사이가 경합 구간이 된다.
   *
   * <p>occurredAt은 요청의 timestamp 필드다. 경계에서 이름이 바뀐다.
   *
   * @return 저장했으면 1, 이미 있어서 건너뛰었으면 0
   */
  int insertIfAbsent(
      UUID organizationId,
      String transactionId,
      UUID customerId,
      String eventType,
      String propertiesJson,
      OffsetDateTime occurredAt) {
    return jdbc.update(
        """
        INSERT INTO usage_event
          (organization_id, transaction_id, customer_id, event_type, properties, occurred_at)
        VALUES (?, ?, ?, ?, ?::jsonb, ?)
        ON CONFLICT (organization_id, transaction_id) DO NOTHING
        """,
        organizationId,
        transactionId,
        customerId,
        eventType,
        propertiesJson,
        occurredAt);
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
  Map<UUID, BigDecimal> sumByCustomer(
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
