package com.meterengine.customer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.customer.entity.Customer;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

public record CustomerResponse(
    @Schema(description = "고객 ID(UUID)입니다. 사용량 이벤트의 customer_id에 씁니다.") UUID id,
    @Schema(description = "고객 이름입니다. read 권한 없이 받은 재등록 응답이면 null입니다.", nullable = true) String name,
    @Schema(
            description =
                "청구 담당 이메일입니다. 저장만 하며 MeterEngine은 이 주소로 메일을 보내지 않습니다. 등록하지 않았거나 read 권한 없이 받은"
                    + " 재등록 응답이면 null입니다.",
            nullable = true)
        @JsonProperty("billing_email")
        String billingEmail,
    @Schema(description = "등록 시각(ISO 8601)입니다. read 권한 없이 받은 재등록 응답이면 null입니다.", nullable = true)
        @JsonProperty("created_at")
        OffsetDateTime createdAt,
    @Schema(description = "자사 시스템에서 고객을 가리키는 ID입니다. 등록할 때 보내지 않았으면 null입니다.")
        @JsonProperty("external_id")
        String externalId,
    @Schema(
            description = "보관 시각(ISO 8601)입니다. 보관하지 않았거나 read 권한 없이 받은 재등록 응답이면 null입니다.",
            nullable = true)
        @JsonProperty("archived_at")
        OffsetDateTime archivedAt,
    @Schema(description = "고객 정보의 버전입니다. 고칠 때 큰따옴표로 감싸 If-Match에 보냅니다.") long version) {

  public static CustomerResponse from(Customer customer) {
    return new CustomerResponse(
        customer.getId(),
        customer.getName(),
        customer.getBillingEmail(),
        customer.getCreatedAt(),
        customer.getExternalId(),
        customer.getArchivedAt(),
        customer.getVersion());
  }

  // 조회 권한 없이 받는 재등록 응답에는 식별자만 남긴다.
  public CustomerResponse identifiersOnly() {
    return new CustomerResponse(id, null, null, null, externalId, null, version);
  }
}
