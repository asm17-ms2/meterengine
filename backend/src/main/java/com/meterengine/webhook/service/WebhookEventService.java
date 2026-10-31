package com.meterengine.webhook.service;

import com.meterengine.global.database.DatabaseScopes;
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
  @org.springframework.beans.factory.annotation.Autowired
  private com.meterengine.global.database.CutoverPolicy cutover;

  private final WebhookEventRepository webhookEventRepository;
  private final JsonMapper jsonMapper;

  WebhookEventService(WebhookEventRepository webhookEventRepository, JsonMapper jsonMapper) {
    this.webhookEventRepository = webhookEventRepository;
    this.jsonMapper = jsonMapper;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public UUID publish(UUID organizationId, WebhookEventType type, Map<String, Object> data) {
    cutover.requireWritable();
    return publish(organizationId, type, data, null);
  }

  @Transactional(propagation = Propagation.MANDATORY)
  public UUID publish(
      UUID organizationId, WebhookEventType type, Map<String, Object> data, UUID endpointId) {
    cutover.requireWritable();
    UUID id = UUID.randomUUID();
    String payload =
        jsonMapper.writeValueAsString(
            Map.of(
                "environment",
                switch (DatabaseScopes.current()) {
                  case TEST -> "test";
                  case LIVE -> "live";
                  case SHARED ->
                      throw new IllegalStateException(
                          "webhook events require a business database scope");
                },
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
