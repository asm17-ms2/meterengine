package com.meterengine.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;

public record EventRejectionsResponse(
    String month,
    @JsonProperty("recording_started_at") OffsetDateTime recordingStartedAt,
    int page,
    int size,
    long total,
    List<EventRejectionResponse> rejections) {}
