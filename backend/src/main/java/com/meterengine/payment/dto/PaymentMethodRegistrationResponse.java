package com.meterengine.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.metric.service.BillableMetricUsageService;
import com.meterengine.payment.entity.PaymentMethodRegistration;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentMethodRegistrationResponse(
    @Schema(description = "카드 등록 페이지 주소에 넣을 일회용 token입니다. 링크를 만든 응답에만 싣고 공개 조회에는 싣지 않습니다.")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String token,
    @Schema(description = "카드를 등록할 고객 ID(UUID)") @JsonProperty("customer_id") UUID customerId,
    @Schema(description = "카드를 등록할 고객의 이름") @JsonProperty("customer_name") String customerName,
    @Schema(description = "카드 등록을 요청한 조직의 이름") @JsonProperty("organization_name")
        String organizationName,
    @Schema(description = "토스페이먼츠 SDK에 넣을 클라이언트 키") @JsonProperty("client_key") String clientKey,
    @Schema(description = "토스페이먼츠 SDK에 넣을 customerKey입니다. 고객 ID와 같습니다.")
        @JsonProperty("customer_key")
        String customerKey,
    @Schema(
            description = "링크를 만들 때의 결제 모드(test 또는 live)",
            allowableValues = {"test", "live"})
        String mode,
    @Schema(description = "링크가 만료되는 시각(KST, +09:00)입니다. 만든 때부터 24시간 뒤입니다.")
        @JsonProperty("expires_at")
        OffsetDateTime expiresAt) {

  public static PaymentMethodRegistrationResponse from(
      String token,
      PaymentMethodRegistration registration,
      String customerName,
      String organizationName,
      String clientKey) {
    return new PaymentMethodRegistrationResponse(
        token,
        registration.getCustomerId(),
        customerName,
        organizationName,
        clientKey,
        registration.getCustomerId().toString(),
        registration.getMode(),
        registration
            .getExpiresAt()
            .atZoneSameInstant(BillableMetricUsageService.BILLING_ZONE)
            .toOffsetDateTime());
  }
}
