package com.meterengine.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.global.validation.StorableJson;
import com.meterengine.global.validation.StorableText;
import com.meterengine.global.validation.StorableTimestamp;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record IngestEventRequest(
    @Schema(
            description = "이벤트마다 고유한 값으로 1자에서 255자입니다. 같은 값으로 다시 보내면 저장하지 않고 처음 저장한 이벤트를 유지합니다.",
            example = "tx-20261105-0001")
        @JsonProperty("transaction_id")
        @NotBlank @Size(max = 255) @StorableText
        String transactionId,
    @Schema(
            description = "고객 등록 응답의 id(UUID)입니다. 같은 업무 환경에 등록한 고객이어야 합니다.",
            example = "3f2b8c1e-7a4d-4e6b-9c0a-5d1f2e3a4b5c")
        @JsonProperty("customer_id")
        @NotNull UUID customerId,
    @Schema(description = "이벤트 유형입니다. 미터의 event_type과 같은 값이어야 그 미터에 집계됩니다.", example = "api_call")
        @JsonProperty("type")
        @NotBlank @StorableText
        String type,
    @Schema(
            description = "JSON 객체입니다. 비어 있어도({}) 됩니다. 미터의 집계 속성과 구분 키를 여기에 담습니다.",
            example = "{\"units\": 12}")
        @NotNull @StorableJson
        Map<String, Object> properties,
    @Schema(
            description =
                "이벤트가 발생한 시각입니다. 오프셋을 붙인 ISO 8601로 보냅니다(예: 2026-11-05T10:00:00+09:00). 조회 응답에서는 occurred_at으로 나옵니다.",
            example = "2026-11-05T10:00:00+09:00")
        @JsonProperty("timestamp")
        @NotNull @StorableTimestamp
        OffsetDateTime occurredAt) {}
