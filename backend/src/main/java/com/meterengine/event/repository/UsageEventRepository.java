package com.meterengine.event.repository;

import com.meterengine.event.dto.UsageEventRow;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 사용량 이벤트 저장 (MS2-130), 로그 조회 (MS2-131).
 *
 * <p>JPA를 쓰지 않는다. usage_event는 append-only 트리거가 걸려 있어 영속성 컨텍스트의 dirty checking이 UPDATE를 내보내는 순간 예외가
 * 난다. ON CONFLICT DO NOTHING은 save()로 표현할 수 없고, PK가 복합이라 @IdClass도 필요하다. 얻을 것보다 우회할 것이 많다.
 */
@Repository
public class UsageEventRepository {

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
  public int insertIfAbsent(
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
   * 기간 안의 이벤트를 최신순 한 페이지만큼 읽는다 (MS2-131).
   *
   * <p><b>정렬이 두 키다.</b> {@code occurred_at}은 클라이언트가 준 값이라 같은 값이 흔하고, DB는 동점 사이의 순서를 약속하지 않는다. 그대로
   * 두면 호출마다 순서가 달라져 1페이지에서 본 행이 2페이지에 또 나오고 다른 행은 사라진다. 에러도 안 나고 눈으로도 안 잡힌다. {@code
   * transaction_id}는 PK 구성요소라 도입사 안에서 유일함이 DB로 보장돼, tiebreaker에 필요한 결정성을 준다.
   *
   * <p><b>기간은 반열린 구간 [start, end)다.</b> {@link
   * com.meterengine.metric.repository.UsageAggregationRepository#sumByCustomer}와 같은 방식으로, 호출자가 KST
   * 월 경계를 계산해 넘긴다. 두 API가 같은 달을 다르게 자르면 화면 숫자가 어긋난다.
   *
   * <p><b>customer는 LEFT JOIN이다.</b> 복합 FK가 있어 짝이 없을 수 없지만, 조인 실패가 행을 통째로 삼키는 쪽보다 이름만 비는 쪽이 낫다.
   *
   * <p>{@code properties}는 {@code ::text}로 꺼내 파싱하지 않고 그대로 응답에 싣는다.
   *
   * @param customerId null이면 고객을 좁히지 않는다
   * @param eventType null이거나 공백뿐이면 종류를 좁히지 않는다
   */
  public List<UsageEventRow> findPage(
      UUID organizationId,
      UUID customerId,
      String eventType,
      OffsetDateTime start,
      OffsetDateTime end,
      int page,
      int size) {
    List<Object> params = new ArrayList<>();
    String where = buildWhere(organizationId, customerId, eventType, start, end, params);
    params.add(size);
    params.add((long) page * size);

    return jdbc.query(
        """
        SELECT e.transaction_id, e.customer_id, c.name AS customer_name, e.event_type,
               e.properties::text AS properties, e.occurred_at, e.received_at
        FROM usage_event e
        LEFT JOIN customer c
          ON c.organization_id = e.organization_id AND c.id = e.customer_id
        """
            + where
            + """
        ORDER BY e.occurred_at DESC, e.transaction_id DESC
        LIMIT ? OFFSET ?
        """,
        (rs, rowNum) ->
            new UsageEventRow(
                rs.getString("transaction_id"),
                rs.getObject("customer_id", UUID.class),
                rs.getString("customer_name"),
                rs.getString("event_type"),
                rs.getString("properties"),
                rs.getObject("occurred_at", OffsetDateTime.class),
                rs.getObject("received_at", OffsetDateTime.class)),
        params.toArray());
  }

  /**
   * 같은 조건에 걸리는 전체 건수 (MS2-131).
   *
   * <p>화면이 마지막 페이지 번호를 그리려면 페이지 하나가 아니라 총량이 필요하다. 조인은 안 건다. 이름으로 거르지 않으므로 셀 때는 필요 없다.
   */
  public long count(
      UUID organizationId,
      UUID customerId,
      String eventType,
      OffsetDateTime start,
      OffsetDateTime end) {
    List<Object> params = new ArrayList<>();
    String where = buildWhere(organizationId, customerId, eventType, start, end, params);

    Long total =
        jdbc.queryForObject(
            "SELECT count(*) FROM usage_event e " + where, Long.class, params.toArray());
    return total == null ? 0 : total;
  }

  /**
   * 두 쿼리가 같은 조건을 보도록 WHERE 절을 한 곳에서 만든다.
   *
   * <p>목록과 건수가 조건이 어긋나면 페이지 번호는 있는데 그 페이지가 비는 식으로 조용히 깨진다.
   *
   * <p>선택 필터를 {@code (? IS NULL OR col = ?)}로 쓰지 않고 절을 빼는 이유는, 그 형태가 파라미터 타입을 모호하게 만들어 UUID와 text에
   * 명시적 캐스팅을 요구하기 때문이다. 값은 전부 바인딩 파라미터라 문자열을 이어 붙여도 주입 경로가 생기지 않는다.
   *
   * <p><b>조건은 usage_event 별칭 {@code e}만 쓴다.</b> {@link #count}는 조인 없이 이 절을 붙이므로, 여기에 고객 이름 검색 같은
   * {@code c.} 조건을 더하면 count 쪽이 missing FROM-clause로 터진다. 조인이 필요한 조건은 두 쿼리에 함께 넣어야 한다.
   */
  private String buildWhere(
      UUID organizationId,
      UUID customerId,
      String eventType,
      OffsetDateTime start,
      OffsetDateTime end,
      List<Object> params) {
    StringBuilder where =
        new StringBuilder(
            "WHERE e.organization_id = ? AND e.occurred_at >= ? AND e.occurred_at < ?");
    params.add(organizationId);
    params.add(start);
    params.add(end);

    if (customerId != null) {
      where.append(" AND e.customer_id = ?");
      params.add(customerId);
    }
    // 빈 문자열을 필터로 받지 않는다. customer_id와 month는 스프링이 빈 값을 null로 바꾸는데
    // event_type만 String이라 ""가 그대로 내려와, FE가 필터를 비우며 빈 값을 보내면
    // event_type = '' 조건이 걸려 데이터가 있는데도 화면이 빈다.
    if (eventType != null && !eventType.isBlank()) {
      where.append(" AND e.event_type = ?");
      params.add(eventType);
    }
    return where.append('\n').toString();
  }
}
