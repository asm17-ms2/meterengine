package com.meterengine.webhook.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record WebhookEndpointResponse(
    @Schema(description = "웹훅 URL ID(UUID)") UUID id,
    @Schema(description = "웹훅을 받을 주소") String url,
    @Schema(description = "받을 웹훅 종류입니다. 등록하거나 수정할 때 고른 값입니다.") @JsonProperty("event_types")
        List<String> eventTypes,
    @Schema(description = "웹훅을 보내는 중이면 true입니다. 발송을 멈췄거나 보관했으면 false입니다.") boolean enabled,
    @Schema(description = "웹훅 URL 메모입니다. 없으면 null입니다.") String description,
    @Schema(description = "등록한 시각(ISO 8601)") @JsonProperty("created_at") OffsetDateTime createdAt,
    @Schema(description = "웹훅 시크릿(whsec_로 시작)입니다. 등록 응답에만 실리고 다른 응답에는 없습니다.")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String secret,
    @Schema(description = "보관한 시각(ISO 8601)입니다. 보관하지 않았으면 null입니다.") @JsonProperty("archived_at")
        OffsetDateTime archivedAt,
    @Schema(description = "가장 최근 발송 시도가 실패했으면 true입니다. 시도 기록이 없으면 false입니다.")
        @JsonProperty("latest_attempt_failed")
        boolean latestAttemptFailed) {
  public WebhookEndpointResponse(
      UUID id,
      String url,
      List<String> eventTypes,
      boolean enabled,
      String description,
      OffsetDateTime createdAt,
      String secret) {
    this(id, url, eventTypes, enabled, description, createdAt, secret, null, false);
  }
}
