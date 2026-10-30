package com.meterengine.organization.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.global.validation.StorableText;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record InvitationConsentRequest(
    @NotBlank @Pattern(regexp = "terms_of_service|privacy_policy") @StorableText String document,
    @JsonProperty("document_version") @NotBlank @Size(max = 255) @StorableText
        String documentVersion) {}
