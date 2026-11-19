package com.meterengine.webhook.controller;

import com.meterengine.global.error.ErrorResponse;
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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
  private static final String ENDPOINT_ID = "웹훅 URL ID(UUID)";
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
  @Operation(summary = "웹훅 URL 목록 조회", description = "현재 업무 환경의 웹훅 URL을 등록한 순서대로 돌려줍니다.")
  public ListWebhookEndpointsResponse listWebhookEndpoints(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(
              description =
                  "보관 상태입니다. active(보관하지 않음), archived(보관함), all 가운데 하나이며 생략하면 active입니다.",
              schema =
                  @Schema(
                      allowableValues = {"active", "archived", "all"},
                      defaultValue = "active"))
          @RequestParam(defaultValue = "active")
          @StorableText
          String status) {
    return new ListWebhookEndpointsResponse(webhookEndpointService.list(organizationId, status));
  }

  @GetMapping("/{id}")
  @ReadAccess
  @Operation(summary = "웹훅 URL 조회", description = "보관한 웹훅 URL도 조회됩니다. 웹훅 시크릿은 싣지 않습니다.")
  public WebhookEndpointResponse getWebhookEndpoint(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = ENDPOINT_ID) @PathVariable UUID id) {
    return webhookEndpointService.get(organizationId, id);
  }

  @PostMapping
  @AdminAccess
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "웹훅 URL 등록",
      description =
          "웹훅 시크릿은 이 응답에서만 한 번 돌려줍니다. 보관하지 않은 웹훅 URL은 업무 환경마다 10개까지 등록할 수 있습니다. HTTPS이고 공개된 주소만 받습니다.")
  public WebhookEndpointResponse createWebhookEndpoint(
      @CurrentOrganizationId UUID organizationId,
      @Valid @RequestBody CreateWebhookEndpointRequest request) {
    return webhookEndpointService.create(organizationId, request);
  }

  @PutMapping("/{id}")
  @AdminAccess
  @Operation(
      summary = "웹훅 URL 수정",
      description = "url, event_types, enabled, description을 모두 보내 한꺼번에 바꿉니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "바꾼 웹훅 URL"),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=resource_archived: 보관한 웹훅 URL입니다. 복원한 뒤 고치세요.")
  })
  public WebhookEndpointResponse updateWebhookEndpoint(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = ENDPOINT_ID) @PathVariable UUID id,
      @Valid @RequestBody UpdateWebhookEndpointRequest request) {
    return webhookEndpointService.update(organizationId, id, request);
  }

  @DeleteMapping("/{id}")
  @AdminAccess
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(
      summary = "웹훅 URL 보관",
      description =
          "전송을 멈추고 꺼진 상태로 보관합니다. 기본 목록에서는 빠지지만 GET /v1/webhook-endpoints/{id}와 status=archived 목록으로 조회됩니다. 지난 전송 기록은 남깁니다. 이미 보관한 웹훅 URL에 다시 요청해도 204입니다.")
  public void deleteWebhookEndpoint(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = ENDPOINT_ID) @PathVariable UUID id) {
    webhookEndpointService.delete(organizationId, id);
  }

  @PostMapping("/{id}/test-events")
  @AdminAccess
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(
      summary = "테스트 웹훅 보내기",
      description =
          "경로의 웹훅 URL로 테스트 웹훅(webhook_test)을 보냅니다. 받을 웹훅에 webhook_test를 고르지 않았어도 보냅니다. 테스트 웹훅을 보내도록 예약하고 바로 webhook_event_id를 돌려주며, 받았는지는 전송 기록에서 확인합니다. 꺼 둔 웹훅 URL이면 409 webhook_endpoint_disabled입니다.")
  public Map<String, UUID> testWebhookEndpoint(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = ENDPOINT_ID) @PathVariable UUID id) {
    return Map.of("webhook_event_id", webhookEndpointService.test(organizationId, id));
  }

  @GetMapping("/{id}/deliveries")
  @ReadAccess
  @Operation(summary = "웹훅 전송 기록 조회", description = "이 웹훅 URL의 전송을 최신순으로 돌려줍니다. 전송마다 시도 기록을 싣습니다.")
  public ListWebhookDeliveriesResponse listWebhookDeliveries(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = ENDPOINT_ID) @PathVariable UUID id,
      @Parameter(description = "0부터 세는 페이지 번호") @RequestParam(defaultValue = "0") @Min(0) int page,
      @Parameter(description = "한 페이지에 담을 개수") @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @Parameter(description = "이 전송 상태만 봅니다. pending(보내는 중), done(성공), failed(실패) 가운데 하나입니다.")
          @RequestParam(required = false)
          @Pattern(regexp = "pending|done|failed") @StorableText
          String status) {
    return webhookDeliveryService.list(organizationId, id, status, page, size);
  }

  @PostMapping("/{id}/deliveries/{delivery_id}/retries")
  @AdminAccess
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(
      summary = "실패한 웹훅 전송 재시도",
      description =
          "실패한 전송을 같은 webhook-id로 다시 보내도록 예약하고 바로 202로 답합니다. 웹훅 URL이 켜져 있어야 합니다. 시도 수는 처음부터 다시 세고, 이전 시도 기록은 남깁니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "202", description = "다시 보내도록 예약했습니다. 본문 없음"),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=webhook_endpoint_not_found: 그런 웹훅 URL이 없습니다.
            code=webhook_delivery_not_found: 그 웹훅 URL에 그런 전송이 없습니다.
            """),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            "code=webhook_delivery_not_retryable: 전송이 failed가 아니거나, 웹훅 URL이 꺼져 있거나 보관됐습니다.")
  })
  public void retryWebhookDelivery(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = ENDPOINT_ID) @PathVariable UUID id,
      @Parameter(description = "다시 보낼 전송 ID(UUID)") @PathVariable("delivery_id") UUID deliveryId) {
    webhookDeliveryService.retry(organizationId, id, deliveryId);
  }

  @PostMapping("/{id}/restorations")
  @AdminAccess
  @Operation(
      summary = "웹훅 URL 복원",
      description = "꺼진 상태로 복원합니다. 주소와 등록 한도를 다시 확인하며 지난 전송 기록은 남깁니다. 보관하지 않은 웹훅 URL이면 그대로 돌려줍니다.")
  public WebhookEndpointResponse restoreWebhookEndpoint(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = ENDPOINT_ID) @PathVariable UUID id) {
    return webhookEndpointService.restore(organizationId, id);
  }
}
