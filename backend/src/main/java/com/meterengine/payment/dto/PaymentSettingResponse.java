package com.meterengine.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.metric.service.BillableMetricUsageService;
import com.meterengine.payment.entity.PaymentSetting;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

public record PaymentSettingResponse(
    @Schema(
            description = "결제에 쓰는 모드. test 또는 live",
            allowableValues = {"test", "live"})
        String mode,
    @Schema(description = "모드를 마지막으로 바꾼 시각. 한 번도 바꾸지 않았으면 null") @JsonProperty("updated_at")
        OffsetDateTime updatedAt) {

  public static PaymentSettingResponse from(PaymentSetting setting) {
    return new PaymentSettingResponse(
        setting.getMode(),
        setting
            .getUpdatedAt()
            .atZoneSameInstant(BillableMetricUsageService.BILLING_ZONE)
            .toOffsetDateTime());
  }
}
