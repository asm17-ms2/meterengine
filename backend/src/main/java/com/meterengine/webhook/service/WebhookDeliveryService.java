package com.meterengine.webhook.service;

import com.meterengine.audit.service.AuditLogService;
import com.meterengine.global.crypto.EncryptedStringConverter;
import com.meterengine.global.database.DatabaseScope;
import com.meterengine.global.database.DatabaseScopes;
import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.scheduling.dto.ScheduledJobResult;
import com.meterengine.webhook.client.WebhookClient;
import com.meterengine.webhook.dto.ListWebhookDeliveriesResponse;
import com.meterengine.webhook.dto.WebhookBacklog;
import com.meterengine.webhook.dto.WebhookClaim;
import com.meterengine.webhook.dto.WebhookOutcome;
import com.meterengine.webhook.repository.WebhookDeliveryRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookDeliveryService {
  @org.springframework.beans.factory.annotation.Autowired
  private com.meterengine.global.database.CutoverPolicy cutover;

  private static final Logger logger = LoggerFactory.getLogger(WebhookDeliveryService.class);
  private static final long RUN_BUDGET_NANOS = Duration.ofSeconds(15).toNanos();
  private final WebhookDeliveryRepository webhookDeliveryRepository;
  private final WebhookEndpointService webhookEndpointService;
  private final EncryptedStringConverter encryptedStringConverter;
  private final WebhookSignatureService webhookSignatureService;
  private final WebhookClient webhookClient;
  private final AuditLogService auditLogService;
  private final WebhookDeliveryMetrics webhookDeliveryMetrics;
  private final LongSupplier nanoTime;
  private final Map<String, WebhookBacklog> backlogByEnvironment = new java.util.HashMap<>();

  @Autowired
  WebhookDeliveryService(
      WebhookDeliveryRepository webhookDeliveryRepository,
      WebhookEndpointService webhookEndpointService,
      EncryptedStringConverter encryptedStringConverter,
      WebhookSignatureService webhookSignatureService,
      WebhookClient webhookClient,
      AuditLogService auditLogService,
      WebhookDeliveryMetrics webhookDeliveryMetrics) {
    this(
        webhookDeliveryRepository,
        webhookEndpointService,
        encryptedStringConverter,
        webhookSignatureService,
        webhookClient,
        auditLogService,
        webhookDeliveryMetrics,
        System::nanoTime);
  }

  WebhookDeliveryService(
      WebhookDeliveryRepository webhookDeliveryRepository,
      WebhookEndpointService webhookEndpointService,
      EncryptedStringConverter encryptedStringConverter,
      WebhookSignatureService webhookSignatureService,
      WebhookClient webhookClient,
      AuditLogService auditLogService,
      WebhookDeliveryMetrics webhookDeliveryMetrics,
      LongSupplier nanoTime) {
    this.webhookDeliveryRepository = webhookDeliveryRepository;
    this.webhookEndpointService = webhookEndpointService;
    this.encryptedStringConverter = encryptedStringConverter;
    this.webhookSignatureService = webhookSignatureService;
    this.webhookClient = webhookClient;
    this.auditLogService = auditLogService;
    this.webhookDeliveryMetrics = webhookDeliveryMetrics;
    this.nanoTime = nanoTime;
  }

  public synchronized void measure() {
    if (cutover.readOnly()) return;
    String environment = scopeName();
    WebhookBacklog measured =
        webhookDeliveryRepository
            .backlog()
            .getOrDefault(environment, new WebhookBacklog(0, 0, 0, 0));
    backlogByEnvironment.put(environment, measured);
    webhookDeliveryMetrics.updateBacklog(Map.copyOf(backlogByEnvironment));
  }

  public ScheduledJobResult deliver() {
    if (cutover.readOnly()) return new ScheduledJobResult(0, 0, 0);
    scopeName();
    countExpiredLeases();
    int processed = 0;
    int skipped = 0;
    int failed = 0;
    long started = nanoTime.getAsLong();
    List<WebhookClaim> turn = new ArrayList<>();
    for (int index = 0; index < 100 && nanoTime.getAsLong() - started < RUN_BUDGET_NANOS; index++) {
      Optional<WebhookClaim> candidate = webhookDeliveryRepository.claim(turn);
      if (candidate.isEmpty()) {
        break;
      }
      WebhookClaim claim = candidate.get();
      if (turn.stream().anyMatch(served -> served.sameOrganizationEnvironment(claim))) turn.clear();
      turn.add(claim);
      Tally tally;
      try {
        tally = attempt(claim);
      } catch (RuntimeException exception) {
        logger.warn(
            "Webhook delivery {} of organization {} could not be attempted",
            claim.id(),
            claim.organizationId(),
            exception);
        tally = Tally.FAILED;
      }
      switch (tally) {
        case PROCESSED -> processed++;
        case SKIPPED -> skipped++;
        case FAILED -> failed++;
      }
    }
    return new ScheduledJobResult(processed, skipped, failed);
  }

  private Tally attempt(WebhookClaim claim) {
    if (!claim.enabled() || claim.deleted()) {
      return record(claim, new WebhookOutcome(null, "endpoint_disabled", 0), Tally.PROCESSED);
    }
    String timestamp = Long.toString(Instant.now().getEpochSecond());
    String signature;
    try {
      signature =
          webhookSignatureService.sign(
              encryptedStringConverter.convertToEntityAttribute(claim.secret()),
              claim.webhookEventId().toString(),
              timestamp,
              claim.payload());
    } catch (RuntimeException exception) {
      logger.warn(
          "Webhook delivery {} of organization {} could not be signed",
          claim.id(),
          claim.organizationId(),
          exception);
      return record(claim, new WebhookOutcome(null, "secret_unavailable", 0), Tally.FAILED);
    }
    return record(
        claim,
        webhookClient.send(
            claim.url(), claim.webhookEventId().toString(), timestamp, signature, claim.payload()),
        Tally.PROCESSED);
  }

  private void countExpiredLeases() {
    Map<String, Integer> expired = webhookDeliveryRepository.expireLeases();
    if (expired.isEmpty()) {
      return;
    }
    expired.forEach(webhookDeliveryMetrics::countExpiredLeases);
    logger.warn(
        "{} webhook deliveries failed because their last attempt lease expired",
        expired.values().stream().mapToInt(Integer::intValue).sum());
  }

  private Tally record(WebhookClaim claim, WebhookOutcome outcome, Tally recorded) {
    Optional<String> status = webhookDeliveryRepository.complete(claim, outcome);
    if (status.isEmpty()) {
      return Tally.SKIPPED;
    }
    String reason = outcome.failureReason() == null ? "none" : outcome.failureReason();
    String result = "pending".equals(status.get()) ? "retry" : status.get();
    String environment = scopeName();
    webhookDeliveryMetrics.countAttempt(result, environment, claim.isFirstAttempt(), outcome);
    webhookDeliveryMetrics.recordResponse(environment, outcome);
    if ("done".equals(result)) {
      webhookDeliveryMetrics.recordDelivered(
          environment, Duration.between(claim.eventCreatedAt().toInstant(), Instant.now()));
    }
    if ("failed".equals(result)) {
      logger.warn(
          "Webhook delivery {} of organization {} failed: {} (status {})",
          claim.id(),
          claim.organizationId(),
          reason,
          outcome.responseStatus());
    }
    return recorded;
  }

  @Transactional(readOnly = true)
  public ListWebhookDeliveriesResponse list(
      UUID organizationId, UUID endpointId, String status, int page, int size) {
    webhookEndpointService.get(organizationId, endpointId);
    return new ListWebhookDeliveriesResponse(
        page,
        size,
        webhookDeliveryRepository.countByEndpoint(organizationId, endpointId, status),
        webhookDeliveryRepository.findByEndpoint(organizationId, endpointId, status, page, size));
  }

  @Transactional
  public void retry(UUID organizationId, UUID endpointId, UUID id) {
    cutover.requireWritable();
    webhookEndpointService.get(organizationId, endpointId);
    if (webhookDeliveryRepository.retry(organizationId, endpointId, id) == 0) {
      if (!webhookDeliveryRepository.exists(organizationId, endpointId, id))
        throw new NotFoundException(ErrorCode.WEBHOOK_DELIVERY_NOT_FOUND);
      throw new ConflictException(ErrorCode.WEBHOOK_DELIVERY_NOT_RETRYABLE);
    }
    auditLogService.record(
        organizationId, "webhook_delivery_retried", "webhook_delivery", id.toString(), Map.of());
  }

  private static String scopeName() {
    DatabaseScope scope = DatabaseScopes.current();
    if (scope == DatabaseScope.SHARED)
      throw new IllegalStateException("webhook delivery requires a business database scope");
    return scope.name().toLowerCase(java.util.Locale.ROOT);
  }

  private enum Tally {
    PROCESSED,
    SKIPPED,
    FAILED
  }
}
