package com.meterengine.organization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.organization.entity.Organization;
import io.swagger.v3.oas.annotations.media.Schema;

public record SupplierResponse(
    @Schema(description = "사업자등록번호(숫자 10자리)입니다. 저장하지 않았으면 null입니다.")
        @JsonProperty("business_registration_number")
        String businessRegistrationNumber,
    @Schema(description = "대표자명입니다. 저장하지 않았으면 null입니다.") @JsonProperty("representative_name")
        String representativeName,
    @Schema(description = "사업장 주소입니다. 저장하지 않았으면 null입니다.") @JsonProperty("business_address")
        String businessAddress) {
  public static SupplierResponse from(Organization organization) {
    return new SupplierResponse(
        organization.getBusinessRegistrationNumber(),
        organization.getRepresentativeName(),
        organization.getBusinessAddress());
  }
}
