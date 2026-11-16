package com.meterengine.liveenvironment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.liveenvironment.entity.LiveEnvironmentApplication;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

public record LiveEnvironmentApplicationResponse(
    @Schema(description = "신청 ID(UUID)") UUID id,
    @Schema(description = "신청한 조직 ID(UUID)") @JsonProperty("organization_id") UUID organizationId,
    @Schema(description = "신청한 조직 이름") @JsonProperty("organization_name") String organizationName,
    @Schema(description = "사업자등록번호(숫자 10자리)") @JsonProperty("business_registration_number")
        String businessRegistrationNumber,
    @Schema(description = "신청한 토스페이먼츠 상점아이디(MID)") @JsonProperty("toss_payments_merchant_id")
        String tossPaymentsMerchantId,
    @Schema(
            description = "신청 상태입니다. pending은 확인 중, approved는 승인, rejected는 거절입니다.",
            allowableValues = {"pending", "approved", "rejected"})
        String status,
    @Schema(
            description =
                "신청할 때 국세청 상태조회로 본 사업자 상태입니다. operating은 계속사업자, suspended는 휴업자, unchecked는"
                    + " 조회 결과를 받지 못해 자동 확인을 못 한 신청입니다.",
            allowableValues = {"operating", "suspended", "unchecked"})
        @JsonProperty("business_status")
        String businessStatus,
    @Schema(description = "국세청이 돌려준 과세유형 명칭입니다. 조회 결과를 받지 못했으면 null입니다.", nullable = true)
        @JsonProperty("tax_type")
        String taxType,
    @Schema(description = "거절 사유입니다. rejected일 때만 있습니다.", nullable = true)
        @JsonProperty("rejection_reason")
        String rejectionReason,
    @Schema(description = "신청 시각(ISO 8601)") @JsonProperty("created_at") OffsetDateTime createdAt,
    @Schema(description = "승인하거나 거절한 시각(ISO 8601)입니다. 검토 전이면 null입니다.", nullable = true)
        @JsonProperty("reviewed_at")
        OffsetDateTime reviewedAt,
    @Schema(
            description = "승인 뒤 토스페이먼츠 결제 응답에서 처음 본 상점아이디입니다. 아직 실결제가 없으면 null입니다.",
            nullable = true)
        @JsonProperty("observed_toss_payments_merchant_id")
        String observedTossPaymentsMerchantId,
    @Schema(description = "상점아이디를 견준 시각(ISO 8601)입니다. 아직 실결제가 없으면 null입니다.", nullable = true)
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
