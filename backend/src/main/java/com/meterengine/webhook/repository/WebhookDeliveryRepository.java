package com.meterengine.webhook.repository;

import com.meterengine.global.security.BusinessEnvironmentContext;
import com.meterengine.webhook.dto.WebhookBacklog;
import com.meterengine.webhook.dto.WebhookClaim;
import com.meterengine.webhook.dto.WebhookDeliveryResponse;
import com.meterengine.webhook.dto.WebhookDeliveryResponse.Attempt;
import com.meterengine.webhook.dto.WebhookOutcome;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class WebhookDeliveryRepository {
  private static final int MAX_ATTEMPTS = 6;
  private static final long[] RETRY_SECONDS = {60, 300, 1800, 7200, 28800};
  private final JdbcTemplate jdbcTemplate;

  WebhookDeliveryRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  // 마지막 시도의 선점이 끝나도록 결과가 없던 전송을 실패로 끝내고 그 건수를 돌려준다.
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public int expireLeases() {
    return jdbcTemplate.update(
        "UPDATE webhook_delivery SET status = 'failed', failure_reason = 'lease_expired', next_attempt_at = NULL WHERE status = 'pending' AND attempt_count >= ? AND next_attempt_at <= clock_timestamp()",
        MAX_ATTEMPTS);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Optional<WebhookClaim> claim() {
    return claim(List.of());
  }

  // 이번 차례에 이미 꺼낸 도입사 환경은 뒤로 미뤄, 한 도입사가 쌓은 전송이 큐를 독차지하지 못하게 한다.
  public Optional<WebhookClaim> claim(List<WebhookClaim> servedThisTurn) {
    UUID[] servedOrganizations =
        servedThisTurn.stream().map(WebhookClaim::organizationId).toArray(UUID[]::new);
    UUID[] servedScopes =
        servedThisTurn.stream().map(WebhookDeliveryRepository::scope).toArray(UUID[]::new);
    return jdbcTemplate
        .query(
            """
        WITH picked AS (
          SELECT id FROM webhook_delivery WHERE status = 'pending'
            AND attempt_count < ? AND next_attempt_at <= clock_timestamp()
          ORDER BY (organization_id, environment_scope_id) IN (SELECT * FROM unnest(?::uuid[], ?::uuid[])),
            next_attempt_at, id
          LIMIT 1 FOR UPDATE SKIP LOCKED
        ), claimed AS (
          UPDATE webhook_delivery d SET attempt_count = attempt_count + 1,
            last_attempted_at = clock_timestamp(), next_attempt_at = clock_timestamp() + interval '2 minutes',
            claim_token = gen_random_uuid()
          FROM picked WHERE d.id = picked.id RETURNING d.*
        )
        SELECT c.*, e.url, e.secret, e.enabled, e.deleted_at, v.payload::text AS payload
        FROM claimed c JOIN webhook_endpoint e ON e.organization_id = c.organization_id AND e.environment_scope_id = c.environment_scope_id AND e.id = c.webhook_endpoint_id
        JOIN webhook_event v ON v.organization_id = c.organization_id AND v.environment_scope_id = c.environment_scope_id AND v.id = c.webhook_event_id
        """,
            (rs, rowNum) ->
                new WebhookClaim(
                    rs.getObject("id", UUID.class),
                    rs.getObject("organization_id", UUID.class),
                    rs.getObject("webhook_event_id", UUID.class),
                    rs.getObject("claim_token", UUID.class),
                    rs.getString("url"),
                    rs.getString("secret"),
                    rs.getString("payload"),
                    rs.getBoolean("enabled"),
                    rs.getObject("deleted_at") != null,
                    rs.getInt("attempt_count"),
                    rs.getObject("last_attempted_at", OffsetDateTime.class),
                    rs.getObject("environment_id", UUID.class)),
            MAX_ATTEMPTS,
            servedOrganizations,
            servedScopes)
        .stream()
        .findFirst();
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public Optional<String> complete(WebhookClaim claim, WebhookOutcome outcome) {
    boolean terminal =
        outcome.isSuccessful()
            || claim.attemptCount() >= MAX_ATTEMPTS
            || "blocked_address".equals(outcome.failureReason())
            || "endpoint_disabled".equals(outcome.failureReason());
    String status = outcome.isSuccessful() ? "done" : terminal ? "failed" : "pending";
    OffsetDateTime nextAttemptAt =
        terminal
            ? null
            : OffsetDateTime.now()
                .plusSeconds(
                    RETRY_SECONDS[Math.min(claim.attemptCount() - 1, RETRY_SECONDS.length - 1)]);
    int changed =
        jdbcTemplate.update(
            "UPDATE webhook_delivery SET status = ?, response_status = ?, failure_reason = ?, next_attempt_at = ?, claim_token = NULL WHERE id = ? AND organization_id = ? AND environment_scope_id = ? AND claim_token = ? AND status = 'pending'",
            status,
            outcome.responseStatus(),
            outcome.failureReason(),
            nextAttemptAt,
            claim.id(),
            claim.organizationId(),
            scope(claim),
            claim.claimToken());
    if (changed == 0) {
      return Optional.empty();
    }
    jdbcTemplate.update(
        "INSERT INTO webhook_delivery_attempt (id, organization_id, environment_id, webhook_delivery_id, attempted_at, response_status, failure_reason, duration_ms) VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
        UUID.randomUUID(),
        claim.organizationId(),
        claim.environmentId(),
        claim.id(),
        claim.lastAttemptedAt(),
        outcome.responseStatus(),
        outcome.failureReason(),
        outcome.durationMs());
    return Optional.of(status);
  }

  public WebhookBacklog backlog() {
    return jdbcTemplate.queryForObject(
        "SELECT count(*), COALESCE(floor(extract(epoch FROM clock_timestamp() - min(next_attempt_at))), 0) FROM webhook_delivery WHERE status = 'pending' AND next_attempt_at <= clock_timestamp()",
        (rs, rowNum) -> new WebhookBacklog(rs.getLong(1), rs.getLong(2)));
  }

  public long countByEndpoint(UUID organizationId, UUID endpointId, String status) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM webhook_delivery WHERE organization_id = ? AND environment_scope_id = ? AND webhook_endpoint_id = ? AND (?::varchar IS NULL OR status = ?)",
        Long.class,
        organizationId,
        BusinessEnvironmentContext.scopeId(),
        endpointId,
        status,
        status);
  }

  public List<WebhookDeliveryResponse> findByEndpoint(
      UUID organizationId, UUID endpointId, String status, int page, int size) {
    UUID scopeId = BusinessEnvironmentContext.scopeId();
    List<WebhookDeliveryResponse> deliveries =
        jdbcTemplate.query(
            """
            SELECT d.*, e.type FROM webhook_delivery d
            JOIN webhook_event e ON e.organization_id = d.organization_id AND e.environment_scope_id = d.environment_scope_id AND e.id = d.webhook_event_id
            WHERE d.organization_id = ? AND d.environment_scope_id = ? AND d.webhook_endpoint_id = ? AND (?::varchar IS NULL OR d.status = ?)
            ORDER BY d.created_at DESC, d.id DESC LIMIT ? OFFSET ?
            """,
            (rs, rowNum) ->
                new WebhookDeliveryResponse(
                    rs.getObject("id", UUID.class),
                    rs.getObject("webhook_event_id", UUID.class),
                    rs.getString("type"),
                    rs.getString("status"),
                    rs.getInt("attempt_count"),
                    rs.getObject("next_attempt_at", OffsetDateTime.class),
                    rs.getObject("last_attempted_at", OffsetDateTime.class),
                    (Integer) rs.getObject("response_status"),
                    rs.getString("failure_reason"),
                    rs.getObject("created_at", OffsetDateTime.class),
                    List.of()),
            organizationId,
            scopeId,
            endpointId,
            status,
            status,
            size,
            (long) page * size);
    if (deliveries.isEmpty()) {
      return deliveries;
    }
    Map<UUID, List<Attempt>> attempts =
        attempts(
            organizationId, scopeId, deliveries.stream().map(WebhookDeliveryResponse::id).toList());
    return deliveries.stream()
        .map(
            delivery ->
                new WebhookDeliveryResponse(
                    delivery.id(),
                    delivery.webhookEventId(),
                    delivery.type(),
                    delivery.status(),
                    delivery.attemptCount(),
                    delivery.nextAttemptAt(),
                    delivery.lastAttemptedAt(),
                    delivery.responseStatus(),
                    delivery.failureReason(),
                    delivery.createdAt(),
                    attempts.getOrDefault(delivery.id(), List.of())))
        .toList();
  }

  public int retry(UUID organizationId, UUID endpointId, UUID id) {
    return jdbcTemplate.update(
        "UPDATE webhook_delivery d SET status = 'pending', attempt_count = 0, next_attempt_at = clock_timestamp(), claim_token = NULL WHERE d.organization_id = ? AND d.environment_scope_id = ? AND d.webhook_endpoint_id = ? AND d.id = ? AND d.status = 'failed' AND EXISTS (SELECT 1 FROM webhook_endpoint e WHERE e.organization_id = d.organization_id AND e.environment_scope_id = d.environment_scope_id AND e.id = d.webhook_endpoint_id AND e.enabled AND e.deleted_at IS NULL)",
        organizationId,
        BusinessEnvironmentContext.scopeId(),
        endpointId,
        id);
  }

  public boolean exists(UUID organizationId, UUID endpointId, UUID id) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            "SELECT EXISTS (SELECT 1 FROM webhook_delivery WHERE organization_id = ? AND environment_scope_id = ? AND webhook_endpoint_id = ? AND id = ?)",
            Boolean.class,
            organizationId,
            BusinessEnvironmentContext.scopeId(),
            endpointId,
            id));
  }

  private static UUID scope(WebhookClaim claim) {
    return claim.environmentId() == null ? new UUID(0, 0) : claim.environmentId();
  }

  private Map<UUID, List<Attempt>> attempts(
      UUID organizationId, UUID scopeId, List<UUID> deliveryIds) {
    Map<UUID, List<Attempt>> attempts = new HashMap<>();
    jdbcTemplate.query(
        "SELECT * FROM webhook_delivery_attempt WHERE organization_id = ? AND environment_scope_id = ? AND webhook_delivery_id = ANY(?) ORDER BY attempted_at, id",
        preparedStatement -> {
          preparedStatement.setObject(1, organizationId);
          preparedStatement.setObject(2, scopeId);
          preparedStatement.setArray(
              3,
              preparedStatement
                  .getConnection()
                  .createArrayOf("uuid", deliveryIds.toArray(UUID[]::new)));
        },
        rs -> {
          attempts
              .computeIfAbsent(
                  rs.getObject("webhook_delivery_id", UUID.class), id -> new ArrayList<>())
              .add(
                  new Attempt(
                      rs.getObject("attempted_at", OffsetDateTime.class),
                      (Integer) rs.getObject("response_status"),
                      rs.getString("failure_reason"),
                      rs.getLong("duration_ms")));
        });
    return attempts;
  }
}
