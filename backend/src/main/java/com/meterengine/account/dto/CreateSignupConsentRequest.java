package com.meterengine.account.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.account.entity.Consent;
import com.meterengine.global.validation.StorableText;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateSignupConsentRequest(
    @Schema(
            description = "동의한 문서입니다. terms_of_service(이용약관)나 privacy_policy(개인정보 처리방침)입니다.",
            allowableValues = {Consent.TERMS_OF_SERVICE, Consent.PRIVACY_POLICY})
        @NotBlank @Pattern(regexp = Consent.DOCUMENT_PATTERN) @StorableText
        String document,
    @Schema(description = "동의한 문서 버전입니다. 화면이 보여 준 버전을 그대로 싣습니다.", example = "2026-10-01")
        @JsonProperty("document_version")
        @NotBlank @Size(max = Consent.DOCUMENT_VERSION_MAX_LENGTH) @StorableText
        String documentVersion) {}
