package com.meterengine.webhook.repository;

import com.meterengine.global.security.BusinessEnvironmentContext;
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

  public List<WebhookEndpointResponse> findByOrganizationId(UUID organizationId) {
    return jdbcTemplate.query(
        "SELECT * FROM webhook_endpoint WHERE organization_id = ? AND environment_scope_id = ? ORDER BY created_at, id",
        (rs, rowNum) -> endpoint(rs),
        organizationId,
        BusinessEnvironmentContext.scopeId());
  }

  public List<WebhookEndpointResponse> findByOrganizationIdAndId(UUID organizationId, UUID id) {
    return jdbcTemplate.query(
        "SELECT * FROM webhook_endpoint WHERE organization_id = ? AND environment_scope_id = ? AND id = ?",
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

  private static WebhookEndpointResponse endpoint(ResultSet rs) throws SQLException {
    return new WebhookEndpointResponse(
        rs.getObject("id", UUID.class),
        rs.getString("url"),
        Arrays.asList((String[]) rs.getArray("event_types").getArray()),
        rs.getBoolean("enabled"),
        rs.getString("description"),
        rs.getObject("created_at", OffsetDateTime.class),
        null,
        rs.getObject("deleted_at", OffsetDateTime.class));
  }
}
