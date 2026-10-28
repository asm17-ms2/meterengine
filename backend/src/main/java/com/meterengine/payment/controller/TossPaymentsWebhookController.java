package com.meterengine.payment.controller;

import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.security.PublicAccess;
import com.meterengine.payment.service.TossPaymentsWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TossPaymentsWebhookController {

  private final TossPaymentsWebhookService tossPaymentsWebhookService;

  TossPaymentsWebhookController(TossPaymentsWebhookService tossPaymentsWebhookService) {
    this.tossPaymentsWebhookService = tossPaymentsWebhookService;
  }

  @PostMapping("/v1/toss-payments-webhooks/{organization_id}")
  @PublicAccess
  @Operation(
      summary = "토스페이먼츠 웹훅 수신",
      description =
          """
          토스페이먼츠 개발자센터에 도입사마다 등록하는 웹훅 주소다. 인증하지 않는 공개 API다.
          본문은 믿지 않는다. eventType이 PAYMENT_STATUS_CHANGED일 때만 data.orderId로 그 도입사의 결제 시도를 찾고, 시도 결제 모드의 키로 토스페이먼츠에 결제를 다시 조회해 반영한다.
          다시 조회한 결제가 DONE이면 pending인 시도를 done으로, ABORTED나 EXPIRED면 failed로 바꾼다. 시도에 pending인 취소가 있고 조회한 cancels에 맞는 취소가 있으면 그 취소를 done으로 바꾼다.
          본문을 JSON으로 읽을 수 없거나, 다른 eventType이거나, 그런 시도가 없으면 바꾸지 않고 200이다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "받았다. 본문은 없다"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=validation_error: 경로의 organization_id가 UUID가 아니다"),
    @ApiResponse(
        responseCode = "500",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=internal_server_error: 토스페이먼츠 재조회 결과를 확인하지 못했다. 토스페이먼츠가 다시 보낸다")
  })
  public void receiveTossPaymentsWebhook(
      @Parameter(description = "웹훅을 등록한 도입사의 ID.") @PathVariable("organization_id")
          UUID organizationId,
      @Valid @RequestBody(required = false) String body) {
    tossPaymentsWebhookService.receive(organizationId, body);
  }
}
