package com.meterengine.webhook.controller;

import com.meterengine.global.security.AdminAccess;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.EnvironmentReady;
import com.meterengine.global.security.ReadAccess;
import com.meterengine.global.validation.StorableText;
import com.meterengine.webhook.dto.CreateWebhookEndpointRequest;
import com.meterengine.webhook.dto.ListWebhookDeliveriesResponse;
import com.meterengine.webhook.dto.ListWebhookEndpointsResponse;
import com.meterengine.webhook.dto.UpdateWebhookEndpointRequest;
import com.meterengine.webhook.dto.WebhookEndpointResponse;
import com.meterengine.webhook.service.WebhookDeliveryService;
import com.meterengine.webhook.service.WebhookEndpointService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@EnvironmentReady
@RequestMapping("/v1/webhook-endpoints")
public class WebhookEndpointController {
  private final WebhookEndpointService webhookEndpointService;
  private final WebhookDeliveryService webhookDeliveryService;

  WebhookEndpointController(
      WebhookEndpointService webhookEndpointService,
      WebhookDeliveryService webhookDeliveryService) {
    this.webhookEndpointService = webhookEndpointService;
    this.webhookDeliveryService = webhookDeliveryService;
  }

  @GetMapping
  @ReadAccess
  @Operation(summary = "웹훅 엔드포인트 목록 조회")
  public ListWebhookEndpointsResponse listWebhookEndpoints(
      @CurrentOrganizationId UUID organizationId,
      @RequestParam(defaultValue = "active") @StorableText String status) {
    return new ListWebhookEndpointsResponse(webhookEndpointService.list(organizationId, status));
  }

  @GetMapping("/{id}")
  @ReadAccess
  @Operation(summary = "웹훅 엔드포인트 조회")
  public WebhookEndpointResponse getWebhookEndpoint(
      @CurrentOrganizationId UUID organizationId, @PathVariable UUID id) {
    return webhookEndpointService.get(organizationId, id);
  }

  @PostMapping
  @AdminAccess
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "웹훅 엔드포인트 등록",
      description = "서명 비밀값은 이 응답에서만 한 번 돌려준다. 등록 한도는 도입사의 환경마다 10개다. 운영에서는 HTTPS와 공개 주소만 허용한다.")
  public WebhookEndpointResponse createWebhookEndpoint(
      @CurrentOrganizationId UUID organizationId,
      @Valid @RequestBody CreateWebhookEndpointRequest request) {
    return webhookEndpointService.create(organizationId, request);
  }

  @PutMapping("/{id}")
  @AdminAccess
  @Operation(summary = "웹훅 엔드포인트 수정")
  public WebhookEndpointResponse updateWebhookEndpoint(
      @CurrentOrganizationId UUID organizationId,
      @PathVariable UUID id,
      @Valid @RequestBody UpdateWebhookEndpointRequest request) {
    return webhookEndpointService.update(organizationId, id, request);
  }

  @DeleteMapping("/{id}")
  @AdminAccess
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "웹훅 엔드포인트 삭제", description = "발송을 중지하고 목록에서 숨긴다. 과거 전송 기록은 보존한다.")
  public void deleteWebhookEndpoint(
      @CurrentOrganizationId UUID organizationId, @PathVariable UUID id) {
    webhookEndpointService.delete(organizationId, id);
  }

  @PostMapping("/{id}/test-events")
  @AdminAccess
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(
      summary = "웹훅 시험 이벤트 발행",
      description = "선택한 엔드포인트에 webhook_test를 발송 대기 상태로 만든다. 수신 여부는 전송 기록에서 확인한다.")
  public Map<String, UUID> testWebhookEndpoint(
      @CurrentOrganizationId UUID organizationId, @PathVariable UUID id) {
    return Map.of("webhook_event_id", webhookEndpointService.test(organizationId, id));
  }

  @GetMapping("/{id}/deliveries")
  @ReadAccess
  @Operation(summary = "웹훅 전송 기록 조회")
  public ListWebhookDeliveriesResponse listWebhookDeliveries(
      @CurrentOrganizationId UUID organizationId,
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @RequestParam(required = false) @Pattern(regexp = "pending|done|failed") @StorableText
          String status) {
    return webhookDeliveryService.list(organizationId, id, status, page, size);
  }

  @PostMapping("/{id}/deliveries/{delivery_id}/retries")
  @AdminAccess
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(
      summary = "실패한 웹훅 전송 재시도",
      description = "실패한 전송을 같은 webhook-id로 다시 대기시킨다. 엔드포인트가 활성 상태여야 한다. 이전 시도 이력은 보존한다.")
  public void retryWebhookDelivery(
      @CurrentOrganizationId UUID organizationId,
      @PathVariable UUID id,
      @PathVariable("delivery_id") UUID deliveryId) {
    webhookDeliveryService.retry(organizationId, id, deliveryId);
  }

  @PostMapping("/{id}/restorations")
  @AdminAccess
  @Operation(summary = "웹훅 엔드포인트 복원", description = "비활성 상태로 복원한다. 주소와 등록 한도를 확인하며 과거 전송 기록을 유지한다.")
  public WebhookEndpointResponse restoreWebhookEndpoint(
      @CurrentOrganizationId UUID organizationId, @PathVariable UUID id) {
    return webhookEndpointService.restore(organizationId, id);
  }
}
