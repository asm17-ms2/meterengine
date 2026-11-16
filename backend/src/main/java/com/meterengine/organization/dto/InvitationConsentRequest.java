package com.meterengine.organization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.global.validation.StorableText;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record InvitationConsentRequest(
    @Schema(description = "동의한 문서입니다. terms_of_service(이용약관)나 privacy_policy(개인정보 처리방침)입니다.")
        @NotBlank @Pattern(regexp = "terms_of_service|privacy_policy") @StorableText
        String document,
    @Schema(description = "동의한 문서 버전입니다. 255자까지입니다.")
        @JsonProperty("document_version")
        @NotBlank @Size(max = 255) @StorableText
        String documentVersion) {}
