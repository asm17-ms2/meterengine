package com.meterengine.liveenvironment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.liveenvironment.entity.LiveEnvironmentApplication;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

public record LiveEnvironmentApplicationResponse(
    UUID id,
    @JsonProperty("organization_id") UUID organizationId,
    @JsonProperty("organization_name") String organizationName,
    @JsonProperty("business_registration_number") String businessRegistrationNumber,
    @JsonProperty("toss_payments_merchant_id") String tossPaymentsMerchantId,
    @Schema(
            description = "신청 상태",
            allowableValues = {"pending", "approved", "rejected"})
        String status,
    @Schema(
            description =
                "신청할 때 국세청 상태조회로 본 사업자 상태. operating은 계속사업자, suspended는 휴업자, unchecked는 조회"
                    + " 결과를 받지 못해 자동 확인을 못 한 신청이다",
            allowableValues = {"operating", "suspended", "unchecked"})
        @JsonProperty("business_status")
        String businessStatus,
    @Schema(description = "국세청이 돌려준 과세유형 명칭. 조회 결과를 받지 못했으면 null", nullable = true)
        @JsonProperty("tax_type")
        String taxType,
    @Schema(description = "거절 사유. rejected일 때만 있다", nullable = true)
        @JsonProperty("rejection_reason")
        String rejectionReason,
    @JsonProperty("created_at") OffsetDateTime createdAt,
    @Schema(description = "승인하거나 거절한 시각. 검토 전이면 null", nullable = true) @JsonProperty("reviewed_at")
        OffsetDateTime reviewedAt,
    @Schema(description = "승인 뒤 토스페이먼츠 결제 응답에서 처음 본 상점아이디. 아직 실제 결제가 없으면 null", nullable = true)
        @JsonProperty("observed_toss_payments_merchant_id")
        String observedTossPaymentsMerchantId,
    @Schema(description = "상점아이디를 견준 시각. 아직 실제 결제가 없으면 null", nullable = true)
        @JsonProperty("merchant_id_checked_at")
        OffsetDateTime merchantIdCheckedAt) {

  public static LiveEnvironmentApplicationResponse from(
      LiveEnvironmentApplication application, String organizationName) {
    return new LiveEnvironmentApplicationResponse(
        application.getId(),
        application.getOrganizationId(),
        organizationName,
        application.getBusinessRegistrationNumber(),
        application.getTossPaymentsMerchantId(),
        application.getStatus(),
        application.getBusinessStatus(),
        application.getTaxType(),
        application.getRejectionReason(),
        application.getCreatedAt(),
        application.getReviewedAt(),
        application.getObservedTossPaymentsMerchantId(),
        application.getMerchantIdCheckedAt());
  }
}
