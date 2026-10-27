package com.meterengine.webhook.service;

import com.meterengine.audit.service.AuditLogService;
import com.meterengine.global.crypto.EncryptedStringConverter;
import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.InvalidRequestException;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.webhook.client.WebhookAddressPolicy;
import com.meterengine.webhook.dto.CreateWebhookEndpointRequest;
import com.meterengine.webhook.dto.UpdateWebhookEndpointRequest;
import com.meterengine.webhook.dto.WebhookEndpointResponse;
import com.meterengine.webhook.entity.WebhookEventType;
import com.meterengine.webhook.repository.WebhookEndpointRepository;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WebhookEndpointService {
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final Set<String> EVENT_TYPES =
      Arrays.stream(WebhookEventType.values())
          .map(WebhookEventType::getValue)
          .collect(Collectors.toUnmodifiableSet());
  private final WebhookEndpointRepository webhookEndpointRepository;
  private final WebhookAddressPolicy webhookAddressPolicy;
  private final EncryptedStringConverter encryptedStringConverter;
  private final WebhookEventService webhookEventService;
  private final AuditLogService auditLogService;

  WebhookEndpointService(
      WebhookEndpointRepository webhookEndpointRepository,
      WebhookAddressPolicy webhookAddressPolicy,
      EncryptedStringConverter encryptedStringConverter,
      WebhookEventService webhookEventService,
      AuditLogService auditLogService) {
    this.webhookEndpointRepository = webhookEndpointRepository;
    this.webhookAddressPolicy = webhookAddressPolicy;
    this.encryptedStringConverter = encryptedStringConverter;
    this.webhookEventService = webhookEventService;
    this.auditLogService = auditLogService;
  }

  @Transactional(readOnly = true)
  public List<WebhookEndpointResponse> list(UUID organizationId) {
    return webhookEndpointRepository.findByOrganizationId(organizationId);
  }

  @Transactional(readOnly = true)
  public WebhookEndpointResponse get(UUID organizationId, UUID id) {
    return webhookEndpointRepository.findByOrganizationIdAndId(organizationId, id).stream()
        .findFirst()
        .orElseThrow(() -> new NotFoundException(ErrorCode.WEBHOOK_ENDPOINT_NOT_FOUND));
  }

  @Transactional
  public WebhookEndpointResponse create(UUID organizationId, CreateWebhookEndpointRequest request) {
    validate(request.url(), request.eventTypes());
    if (webhookEndpointRepository.countAndLock(organizationId) >= 10) {
      throw new ConflictException(ErrorCode.WEBHOOK_ENDPOINT_LIMIT_EXCEEDED);
    }
    UUID id = UUID.randomUUID();
    byte[] bytes = new byte[32];
    SECURE_RANDOM.nextBytes(bytes);
    String secret = "whsec_" + Base64.getEncoder().encodeToString(bytes);
    webhookEndpointRepository.insert(
        organizationId,
        id,
        request.url(),
        encryptedStringConverter.convertToDatabaseColumn(secret),
        request.eventTypes().stream().distinct().toList(),
        request.description());
    auditLogService.record(
        organizationId, "webhook_endpoint_created", "webhook_endpoint", id.toString(), Map.of());
    WebhookEndpointResponse response = get(organizationId, id);
    return new WebhookEndpointResponse(
        response.id(),
        response.url(),
        response.eventTypes(),
        response.enabled(),
        response.description(),
        response.createdAt(),
        secret);
  }

  @Transactional
  public WebhookEndpointResponse update(
      UUID organizationId, UUID id, UpdateWebhookEndpointRequest request) {
    get(organizationId, id);
    validate(request.url(), request.eventTypes());
    webhookEndpointRepository.update(
        organizationId,
        id,
        request.url(),
        request.eventTypes().stream().distinct().toList(),
        request.enabled(),
        request.description());
    auditLogService.record(
        organizationId,
        "webhook_endpoint_updated",
        "webhook_endpoint",
        id.toString(),
        Map.of("enabled", request.enabled()));
    return get(organizationId, id);
  }

  @Transactional
  public void delete(UUID organizationId, UUID id) {
    get(organizationId, id);
    webhookEndpointRepository.delete(organizationId, id);
    auditLogService.record(
        organizationId, "webhook_endpoint_deleted", "webhook_endpoint", id.toString(), Map.of());
  }

  @Transactional
  public UUID test(UUID organizationId, UUID id) {
    if (!get(organizationId, id).enabled()) {
      throw new ConflictException(ErrorCode.WEBHOOK_ENDPOINT_DISABLED);
    }
    return webhookEventService.publish(
        organizationId,
        WebhookEventType.WEBHOOK_TEST,
        Map.of("message", "Webhook connection test"),
        id);
  }

  private void validate(String url, List<String> eventTypes) {
    if (!EVENT_TYPES.containsAll(eventTypes)) {
      throw new InvalidRequestException(ErrorCode.INVALID_WEBHOOK_EVENT_TYPE);
    }
    try {
      webhookAddressPolicy.resolve(url);
    } catch (IOException | IllegalArgumentException exception) {
      throw new InvalidRequestException(ErrorCode.INVALID_WEBHOOK_URL);
    }
  }
}
