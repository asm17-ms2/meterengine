package com.meterengine.usage;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 사용량 이벤트 저장 (MS2-130).
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
}
