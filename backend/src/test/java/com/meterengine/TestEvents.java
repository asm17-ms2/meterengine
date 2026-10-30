package com.meterengine;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class TestEvents {

  private TestEvents() {}

  // received_at은 트리거가 저장 순간으로 덮어쓰므로, 받은 시각을 정하려면 그 트리거를 잠시 끈다.
  public static void insert(
      JdbcTemplate jdbcTemplate,
      UUID organizationId,
      String transactionId,
      UUID customerId,
      String type,
      String propertiesJson,
      OffsetDateTime occurredAt,
      OffsetDateTime receivedAt) {
    jdbcTemplate.execute("ALTER TABLE event DISABLE TRIGGER event_set_received_at");
    try {
      jdbcTemplate.update(
          """
          INSERT INTO event
            (organization_id, transaction_id, customer_id, type, properties, occurred_at,
             received_at)
          VALUES (?, ?, ?, ?, ?::jsonb, ?, ?)
          """,
          organizationId,
          transactionId,
          customerId,
          type,
          propertiesJson,
          occurredAt,
          receivedAt);
    } finally {
      jdbcTemplate.execute("ALTER TABLE event ENABLE TRIGGER event_set_received_at");
    }
  }

  // received_at 트리거를 끈 채 넣기를 돌린다. 넣는 SQL이 received_at을 직접 정한다.
  public static void withGivenReceivedAt(JdbcTemplate jdbcTemplate, Runnable insert) {
    jdbcTemplate.execute("ALTER TABLE event DISABLE TRIGGER event_set_received_at");
    try {
      insert.run();
    } finally {
      jdbcTemplate.execute("ALTER TABLE event ENABLE TRIGGER event_set_received_at");
    }
  }

  // 토큰 미터용 chat_completion 이벤트를 받은 시각을 정해 넣는다.
  public static void insertToken(
      JdbcTemplate jdbcTemplate,
      UUID organizationId,
      String transactionId,
      UUID customerId,
      int token,
      String occurredAt,
      String receivedAt) {
    insert(
        jdbcTemplate,
        organizationId,
        transactionId,
        customerId,
        "chat_completion",
        "{\"model\":\"opus-5\",\"token\":%d}".formatted(token),
        OffsetDateTime.parse(occurredAt),
        OffsetDateTime.parse(receivedAt));
  }
}
