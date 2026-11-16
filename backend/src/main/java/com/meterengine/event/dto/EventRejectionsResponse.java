package com.meterengine.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

public record EventRejectionsResponse(
    @Schema(description = "조회한 기록 달(yyyy-MM, KST)") String month,
    @Schema(description = "수집 거절을 기록하기 시작한 시각(ISO 8601)입니다. 이보다 앞선 거절은 기록에 없습니다.")
        @JsonProperty("recording_started_at")
        OffsetDateTime recordingStartedAt,
    @Schema(description = "0부터 세는 페이지 번호") int page,
    @Schema(description = "한 페이지에 담는 개수") int size,
    @Schema(description = "조건에 맞는 거절 기록 전체 건수") long total,
    @Schema(description = "이 페이지의 거절 기록입니다. 최신 기록부터 싣습니다.")
        List<EventRejectionResponse> rejections) {}
