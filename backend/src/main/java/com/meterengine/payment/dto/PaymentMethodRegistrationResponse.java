package com.meterengine.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.metric.service.BillableMetricUsageService;
import com.meterengine.payment.entity.PaymentMethodRegistration;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentMethodRegistrationResponse(
    @Schema(description = "카드 등록 페이지 주소에 넣을 일회용 토큰. 링크를 만든 응답에만 싣고 공개 조회에는 싣지 않는다")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String token,
    @JsonProperty("customer_id") UUID customerId,
    @JsonProperty("customer_name") String customerName,
    @JsonProperty("organization_name") String organizationName,
    @Schema(description = "토스페이먼츠 SDK에 넣을 클라이언트 키") @JsonProperty("client_key") String clientKey,
    @Schema(description = "토스페이먼츠 SDK에 넣을 customerKey. 고객 id와 같다") @JsonProperty("customer_key")
        String customerKey,
    @Schema(
            description = "링크를 만들 때의 결제 모드. test 또는 live",
            allowableValues = {"test", "live"})
        String mode,
    @Schema(description = "링크가 만료되는 시각. 만든 때부터 24시간 뒤다") @JsonProperty("expires_at")
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
