package com.meterengine.webhook.repository;

import com.meterengine.global.security.BusinessEnvironmentContext;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WebhookEventRepository {
  private final JdbcTemplate jdbcTemplate;

  WebhookEventRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void insert(UUID organizationId, UUID id, String type, String payload, UUID endpointId) {
    jdbcTemplate.update(
        "INSERT INTO webhook_event (id, organization_id, environment_id, type, payload) VALUES (?, ?, ?, ?, ?::jsonb)",
        id,
        organizationId,
        BusinessEnvironmentContext.environmentId(),
        type,
        payload);
    jdbcTemplate.update(
        """
        INSERT INTO webhook_delivery (id, organization_id, environment_id, webhook_endpoint_id, webhook_event_id, next_attempt_at)
        SELECT gen_random_uuid(), organization_id, environment_id, id, ?, clock_timestamp()
        FROM webhook_endpoint
        WHERE organization_id = ? AND environment_scope_id = ? AND enabled AND deleted_at IS NULL
          AND ((?::uuid IS NOT NULL AND id = ?::uuid) OR (?::uuid IS NULL AND ? = ANY(event_types)))
        """,
        id,
        organizationId,
        BusinessEnvironmentContext.scopeId(),
        endpointId,
        endpointId,
        endpointId,
        type);
  }
}
