package com.meterengine.liveenvironment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ListLiveEnvironmentApplicationsResponse(
    @Schema(description = "신청 목록입니다. 최근 것부터입니다.") @JsonProperty("live_environment_applications")
        List<LiveEnvironmentApplicationResponse> liveEnvironmentApplications) {}
