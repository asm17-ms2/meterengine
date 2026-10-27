package com.meterengine.webhook.service;

import com.meterengine.audit.service.AuditLogService;
import com.meterengine.global.crypto.EncryptedStringConverter;
import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.scheduling.dto.ScheduledJobResult;
import com.meterengine.webhook.client.WebhookClient;
import com.meterengine.webhook.dto.ListWebhookDeliveriesResponse;
import com.meterengine.webhook.dto.WebhookClaim;
import com.meterengine.webhook.dto.WebhookOutcome;
import com.meterengine.webhook.repository.WebhookDeliveryRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookDeliveryService {
  private static final long RUN_BUDGET_NANOS = Duration.ofSeconds(15).toNanos();
  private final WebhookDeliveryRepository webhookDeliveryRepository;
  private final WebhookEndpointService webhookEndpointService;
  private final EncryptedStringConverter encryptedStringConverter;
  private final WebhookSignatureService webhookSignatureService;
  private final WebhookClient webhookClient;
  private final AuditLogService auditLogService;
  private final MeterRegistry meterRegistry;
  private final LongSupplier nanoTime;

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
  }

  public ScheduledJobResult deliver() {
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
      WebhookOutcome outcome;
      if (!claim.enabled() || claim.deleted()) {
        outcome = new WebhookOutcome(null, "endpoint_disabled", 0);
      } else {
        String secret = encryptedStringConverter.convertToEntityAttribute(claim.secret());
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String signature =
            webhookSignatureService.sign(
                secret, claim.webhookEventId().toString(), timestamp, claim.payload());
        outcome =
            webhookClient.send(
                claim.url(),
                claim.webhookEventId().toString(),
                timestamp,
                signature,
                claim.payload());
      }
      if (!webhookDeliveryRepository.complete(claim, outcome)) {
        skipped++;
        continue;
      }
      boolean terminal =
          claim.attemptCount() >= 6
              || "blocked_address".equals(outcome.failureReason())
              || "endpoint_disabled".equals(outcome.failureReason());
      String result = outcome.isSuccessful() ? "done" : terminal ? "failed" : "retry";
      meterRegistry.counter("meterengine.webhook.delivery", "result", result).increment();
      if (outcome.isSuccessful()) {
        processed++;
      } else {
        failed++;
      }
    }
    return new ScheduledJobResult(processed, skipped, failed);
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
      throw new ConflictException(ErrorCode.WEBHOOK_DELIVERY_NOT_RETRYABLE);
    }
    auditLogService.record(
        organizationId, "webhook_delivery_retried", "webhook_delivery", id.toString(), Map.of());
  }
}
