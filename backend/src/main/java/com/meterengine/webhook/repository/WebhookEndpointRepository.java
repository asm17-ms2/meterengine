package com.meterengine.webhook.repository;

import com.meterengine.global.security.BusinessEnvironmentContext;
import com.meterengine.global.validation.ArchiveStatus;
import com.meterengine.webhook.dto.WebhookEndpointResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WebhookEndpointRepository {
  private final JdbcTemplate jdbcTemplate;

  WebhookEndpointRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  // 새 전송은 아직 시도하지 않았을 수 있어, 전송 생성 순서가 아니라 기록된 마지막 시도의 결과로 판단한다.
  private static final String LATEST_ATTEMPT_FAILED =
      "COALESCE((SELECT a.failure_reason IS NOT NULL FROM webhook_delivery d"
          + " JOIN webhook_delivery_attempt a ON a.organization_id = d.organization_id"
          + " AND a.webhook_delivery_id = d.id"
          + " WHERE d.organization_id = e.organization_id AND d.webhook_endpoint_id = e.id"
          + " ORDER BY a.attempted_at DESC, a.id DESC LIMIT 1), false) AS latest_attempt_failed";

  // 상태를 SQL에서 먼저 걸러, 기본 목록이 개수 제한 밖인 보관 주소의 최근 시도까지 계산하지 않게 한다.
  public List<WebhookEndpointResponse> findByOrganizationId(
      UUID organizationId, ArchiveStatus status) {
    return jdbcTemplate.query(
        "SELECT e.*, "
            + LATEST_ATTEMPT_FAILED
            + " FROM webhook_endpoint e WHERE organization_id = ? AND environment_scope_id = ?"
            + archiveCondition(status)
            + " ORDER BY created_at, id",
        (rs, rowNum) -> endpoint(rs),
        organizationId,
        BusinessEnvironmentContext.scopeId());
  }

  public List<WebhookEndpointResponse> findByOrganizationIdAndId(UUID organizationId, UUID id) {
    return jdbcTemplate.query(
        "SELECT e.*, "
            + LATEST_ATTEMPT_FAILED
            + " FROM webhook_endpoint e WHERE organization_id = ? AND environment_scope_id = ? AND id = ?",
        (rs, rowNum) -> endpoint(rs),
        organizationId,
        BusinessEnvironmentContext.scopeId(),
        id);
  }

  public int countAndLock(UUID organizationId) {
    jdbcTemplate.queryForObject(
        "SELECT id FROM organization WHERE id = ? FOR UPDATE", UUID.class, organizationId);
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM webhook_endpoint WHERE organization_id = ? AND environment_scope_id = ? AND deleted_at IS NULL",
        Integer.class,
        organizationId,
        BusinessEnvironmentContext.scopeId());
  }

  public boolean existsByEventType(UUID organizationId, String eventType) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM webhook_endpoint WHERE organization_id = ? AND environment_scope_id = ? AND enabled AND deleted_at IS NULL AND ? = ANY(event_types))",
            Boolean.class,
            organizationId,
            BusinessEnvironmentContext.scopeId(),
            eventType));
  }

  public void insert(
      UUID organizationId,
      UUID id,
      String url,
      String secret,
      List<String> eventTypes,
      String description) {
    jdbcTemplate.update(
        "INSERT INTO webhook_endpoint (id, organization_id, environment_id, url, secret, event_types, description) VALUES (?, ?, ?, ?, ?, ?::text[], ?)",
        id,
        organizationId,
        BusinessEnvironmentContext.environmentId(),
        url,
        secret,
        "{" + String.join(",", eventTypes) + "}",
        description);
  }

  public void update(
      UUID organizationId,
      UUID id,
      String url,
      List<String> eventTypes,
      boolean enabled,
      String description) {
    jdbcTemplate.update(
        "UPDATE webhook_endpoint SET url = ?, event_types = ?::text[], enabled = ?, description = ? WHERE organization_id = ? AND environment_scope_id = ? AND id = ? AND deleted_at IS NULL",
        url,
        "{" + String.join(",", eventTypes) + "}",
        enabled,
        description,
        organizationId,
        BusinessEnvironmentContext.scopeId(),
        id);
  }

  public void delete(UUID organizationId, UUID id) {
    jdbcTemplate.update(
        "UPDATE webhook_endpoint SET enabled = false, deleted_at = clock_timestamp() WHERE organization_id = ? AND environment_scope_id = ? AND id = ? AND deleted_at IS NULL",
        organizationId,
        BusinessEnvironmentContext.scopeId(),
        id);
  }

  public void restore(UUID organizationId, UUID id) {
    jdbcTemplate.update(
        "UPDATE webhook_endpoint SET deleted_at = NULL, enabled = false WHERE organization_id = ? AND environment_scope_id = ? AND id = ?",
        organizationId,
        BusinessEnvironmentContext.scopeId(),
        id);
  }

  private static String archiveCondition(ArchiveStatus status) {
    return switch (status) {
      case ACTIVE -> " AND deleted_at IS NULL";
      case ARCHIVED -> " AND deleted_at IS NOT NULL";
      case ALL -> "";
    };
  }

  private static WebhookEndpointResponse endpoint(ResultSet rs) throws SQLException {
    return new WebhookEndpointResponse(
        rs.getObject("id", UUID.class),
        rs.getString("url"),
        Arrays.asList((String[]) rs.getArray("event_types").getArray()),
        rs.getBoolean("enabled"),
        rs.getString("description"),
        rs.getObject("created_at", OffsetDateTime.class),
        null,
        rs.getObject("deleted_at", OffsetDateTime.class),
        rs.getBoolean("latest_attempt_failed"));
  }
}
