package com.meterengine.webhook.service;

import com.meterengine.audit.service.AuditLogService;
import com.meterengine.global.crypto.EncryptedStringConverter;
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
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookDeliveryService {
  private static final Logger logger = LoggerFactory.getLogger(WebhookDeliveryService.class);
  private static final long RUN_BUDGET_NANOS = Duration.ofSeconds(15).toNanos();
  private final WebhookDeliveryRepository webhookDeliveryRepository;
  private final WebhookEndpointService webhookEndpointService;
  private final EncryptedStringConverter encryptedStringConverter;
  private final WebhookSignatureService webhookSignatureService;
  private final WebhookClient webhookClient;
  private final AuditLogService auditLogService;
  private final MeterRegistry meterRegistry;
  private final LongSupplier nanoTime;
  private final AtomicLong backlogCount = new AtomicLong();
  private final AtomicLong backlogAgeSeconds = new AtomicLong();

  @Autowired
  WebhookDeliveryService(
      WebhookDeliveryRepository webhookDeliveryRepository,
      WebhookEndpointService webhookEndpointService,
      EncryptedStringConverter encryptedStringConverter,
      WebhookSignatureService webhookSignatureService,
      WebhookClient webhookClient,
      AuditLogService auditLogService,
      MeterRegistry meterRegistry) {
    this(
        webhookDeliveryRepository,
        webhookEndpointService,
        encryptedStringConverter,
        webhookSignatureService,
        webhookClient,
        auditLogService,
        meterRegistry,
        System::nanoTime);
  }

  WebhookDeliveryService(
      WebhookDeliveryRepository webhookDeliveryRepository,
      WebhookEndpointService webhookEndpointService,
      EncryptedStringConverter encryptedStringConverter,
      WebhookSignatureService webhookSignatureService,
      WebhookClient webhookClient,
      AuditLogService auditLogService,
      MeterRegistry meterRegistry,
      LongSupplier nanoTime) {
    this.webhookDeliveryRepository = webhookDeliveryRepository;
    this.webhookEndpointService = webhookEndpointService;
    this.encryptedStringConverter = encryptedStringConverter;
    this.webhookSignatureService = webhookSignatureService;
    this.webhookClient = webhookClient;
    this.auditLogService = auditLogService;
    this.meterRegistry = meterRegistry;
    this.nanoTime = nanoTime;
    Gauge.builder("meterengine.webhook.delivery.backlog", backlogCount, AtomicLong::get)
        .register(meterRegistry);
    Gauge.builder("meterengine.webhook.delivery.backlog.age", backlogAgeSeconds, AtomicLong::get)
        .baseUnit("seconds")
        .register(meterRegistry);
  }

  // 스크레이프 경로에서 DB를 읽지 않도록 발송 작업 틱마다 보낼 때가 지난 대기 전송을 세어 둔다.
  public void measure() {
    WebhookBacklog backlog = webhookDeliveryRepository.backlog();
    backlogCount.set(backlog.dueCount());
    backlogAgeSeconds.set(backlog.oldestDueAgeSeconds());
  }

  public ScheduledJobResult deliver() {
    countExpiredLeases();
    int processed = 0;
    int skipped = 0;
    int failed = 0;
    long started = nanoTime.getAsLong();
    for (int index = 0; index < 100 && nanoTime.getAsLong() - started < RUN_BUDGET_NANOS; index++) {
      Optional<WebhookClaim> candidate = webhookDeliveryRepository.claim();
      if (candidate.isEmpty()) {
        break;
      }
      WebhookClaim claim = candidate.get();
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
    int expired = webhookDeliveryRepository.expireLeases();
    if (expired == 0) {
      return;
    }
    meterRegistry
        .counter("meterengine.webhook.delivery", "result", "failed", "reason", "lease_expired")
        .increment(expired);
    logger.warn("{} webhook deliveries failed because their last attempt lease expired", expired);
  }

  private Tally record(WebhookClaim claim, WebhookOutcome outcome, Tally recorded) {
    Optional<String> status = webhookDeliveryRepository.complete(claim, outcome);
    if (status.isEmpty()) {
      return Tally.SKIPPED;
    }
    String reason = outcome.failureReason() == null ? "none" : outcome.failureReason();
    String result = "pending".equals(status.get()) ? "retry" : status.get();
    meterRegistry
        .counter("meterengine.webhook.delivery", "result", result, "reason", reason)
        .increment();
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
    webhookEndpointService.get(organizationId, endpointId);
    if (webhookDeliveryRepository.retry(organizationId, endpointId, id) == 0) {
      if (!webhookDeliveryRepository.exists(organizationId, endpointId, id))
        throw new NotFoundException(ErrorCode.WEBHOOK_DELIVERY_NOT_FOUND);
      throw new ConflictException(ErrorCode.WEBHOOK_DELIVERY_NOT_RETRYABLE);
    }
    auditLogService.record(
        organizationId, "webhook_delivery_retried", "webhook_delivery", id.toString(), Map.of());
  }

  private enum Tally {
    PROCESSED,
    SKIPPED,
    FAILED
  }
}
