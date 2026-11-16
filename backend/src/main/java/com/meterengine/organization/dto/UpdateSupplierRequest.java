package com.meterengine.organization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.global.validation.StorableText;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateSupplierRequest(
    @Schema(description = "사업자등록번호입니다. 하이픈 없이 숫자 10자리로 보냅니다.")
        @NotBlank @StorableText
        @Pattern(regexp = "[0-9]{10}") @JsonProperty("business_registration_number")
        String businessRegistrationNumber,
    @Schema(description = "대표자명입니다. 255자까지입니다.")
        @NotBlank @Size(max = 255) @StorableText
        @JsonProperty("representative_name")
        String representativeName,
    @Schema(description = "사업장 주소입니다. 1000자까지입니다.")
        @NotBlank @Size(max = 1000) @StorableText
        @JsonProperty("business_address")
        String businessAddress) {}
