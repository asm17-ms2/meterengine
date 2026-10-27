package com.meterengine.webhook.service;

import com.meterengine.webhook.entity.WebhookEventType;
import com.meterengine.webhook.repository.WebhookEventRepository;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

@Service
public class WebhookEventService {
  private final WebhookEventRepository webhookEventRepository;
  private final JsonMapper jsonMapper;

  WebhookEventService(WebhookEventRepository webhookEventRepository, JsonMapper jsonMapper) {
    this.webhookEventRepository = webhookEventRepository;
    this.jsonMapper = jsonMapper;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public UUID publish(UUID organizationId, WebhookEventType type, Map<String, Object> data) {
    return publish(organizationId, type, data, null);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public UUID publish(
      UUID organizationId, WebhookEventType type, Map<String, Object> data, UUID endpointId) {
    UUID id = UUID.randomUUID();
    String payload =
        jsonMapper.writeValueAsString(
            Map.of(
                "type",
                type.getValue(),
                "timestamp",
                OffsetDateTime.now().toString(),
                "data",
                data));
    webhookEventRepository.insert(organizationId, id, type.getValue(), payload, endpointId);
    return id;
  }
}
