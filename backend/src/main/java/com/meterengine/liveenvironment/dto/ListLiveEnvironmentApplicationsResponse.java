package com.meterengine.liveenvironment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ListLiveEnvironmentApplicationsResponse(
    @JsonProperty("live_environment_applications")
        List<LiveEnvironmentApplicationResponse> liveEnvironmentApplications) {}
