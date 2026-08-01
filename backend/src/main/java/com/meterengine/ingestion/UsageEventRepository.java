package com.meterengine.ingestion;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * usage_events 저장소.
 *
 * <p>JPA 엔티티 대신 SQL을 직접 쓴다: id와 received_at은 DB가 생성하고 (MS2-26 ERD, received_at은 트리거가 클라이언트 입력도
 * 덮어쓴다), 행은 append-only라 영속성 컨텍스트의 변경 추적이 설 자리가 없다.
 */
@Repository
public class UsageEventRepository {

  private static final String COLUMNS =
      "id, organization_id, transaction_id, external_customer_id, customer_id, "
          + "code, meter_id, properties, occurred_at, received_at";

  private final JdbcClient jdbc;
  private final ObjectMapper objectMapper;

  public UsageEventRepository(JdbcClient jdbc, ObjectMapper objectMapper) {
    this.jdbc = jdbc;
    this.objectMapper = objectMapper;
  }

  /** 삽입 후 DB가 생성한 값(id, received_at)을 포함한 저장 행을 돌려준다. 멱등키 충돌 시 {@code DuplicateKeyException}. */
  public UsageEvent insert(NewUsageEvent event) {
    return jdbc.sql(
            "INSERT INTO usage_events "
                + "(organization_id, transaction_id, external_customer_id, code, properties, occurred_at) "
                + "VALUES (:organizationId, :transactionId, :externalCustomerId, :code, "
                + "CAST(:properties AS jsonb), :occurredAt) "
                + "RETURNING "
                + COLUMNS)
        .param("organizationId", event.organizationId())
        .param("transactionId", event.transactionId())
        .param("externalCustomerId", event.externalCustomerId())
        .param("code", event.code())
        .param("properties", writeProperties(event.properties()))
        .param("occurredAt", event.occurredAt())
        .query(this::mapRow)
        .single();
  }

  public Optional<UsageEvent> findByTransactionId(UUID organizationId, String transactionId) {
    return jdbc.sql(
            "SELECT "
                + COLUMNS
                + " FROM usage_events "
                + "WHERE organization_id = :organizationId AND transaction_id = :transactionId")
        .param("organizationId", organizationId)
        .param("transactionId", transactionId)
        .query(this::mapRow)
        .optional();
  }

  private UsageEvent mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new UsageEvent(
        rs.getObject("id", UUID.class),
        rs.getObject("organization_id", UUID.class),
        rs.getString("transaction_id"),
        rs.getString("external_customer_id"),
        rs.getObject("customer_id", UUID.class),
        rs.getString("code"),
        rs.getObject("meter_id", UUID.class),
        readProperties(rs.getString("properties")),
        toUtc(rs.getObject("occurred_at", OffsetDateTime.class)),
        toUtc(rs.getObject("received_at", OffsetDateTime.class)));
  }

  /** 시각은 UTC로 정규화해 노출한다 (MS2-26 결정 2: UTC 저장, KST 귀속은 조회 쪽 일). */
  private static OffsetDateTime toUtc(OffsetDateTime value) {
    return value.withOffsetSameInstant(ZoneOffset.UTC);
  }

  private String writeProperties(Map<String, Object> properties) {
    try {
      return objectMapper.writeValueAsString(properties);
    } catch (JacksonException e) {
      throw new IllegalArgumentException("properties를 JSON으로 직렬화할 수 없다", e);
    }
  }

  private Map<String, Object> readProperties(String json) {
    try {
      return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
    } catch (JacksonException e) {
      throw new IllegalStateException("저장된 properties JSON을 읽을 수 없다", e);
    }
  }
}
