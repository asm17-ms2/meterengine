package com.meterengine.liveenvironment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.global.validation.StorableText;
import com.meterengine.liveenvironment.entity.LiveEnvironmentApplication;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateLiveEnvironmentApplicationStatusRequest(
    @Schema(
            description = "검토 결과",
            allowableValues = {
              LiveEnvironmentApplication.APPROVED,
              LiveEnvironmentApplication.REJECTED
            })
        @NotBlank @Pattern(regexp = LiveEnvironmentApplication.REVIEW_STATUS_PATTERN) @StorableText
        String status,
    @Schema(description = "거절 사유. rejected일 때만 보내고 신청한 조직이 그대로 읽는다", nullable = true)
        @Size(max = LiveEnvironmentApplication.REJECTION_REASON_MAX_LENGTH) @StorableText
        @JsonProperty("rejection_reason")
        String rejectionReason) {}
