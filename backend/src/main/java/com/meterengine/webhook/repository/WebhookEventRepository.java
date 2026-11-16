package com.meterengine.webhook.repository;

import com.meterengine.global.security.BusinessEnvironmentContext;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WebhookEventRepository {
  private final JdbcTemplate jdbcTemplate;

  WebhookEventRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  // GREATEST는 NULL을 건너뛰어, deliverAt이 없거나 지났으면 지금 보낼 차례가 된다.
  public void insert(
      UUID organizationId,
      UUID id,
      String type,
      String payload,
      UUID endpointId,
      OffsetDateTime deliverAt) {
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
        SELECT gen_random_uuid(), organization_id, environment_id, id, ?, GREATEST(?::timestamptz, clock_timestamp())
        FROM webhook_endpoint
        WHERE organization_id = ? AND environment_scope_id = ? AND enabled AND deleted_at IS NULL
          AND ((?::uuid IS NOT NULL AND id = ?::uuid) OR (?::uuid IS NULL AND ? = ANY(event_types)))
        """,
        id,
        deliverAt,
        organizationId,
        BusinessEnvironmentContext.scopeId(),
        endpointId,
        endpointId,
        endpointId,
        type);
  }
}
