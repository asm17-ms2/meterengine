package com.meterengine.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record EventRejectionResponse(
    @Schema(description = "거절 기록 ID(UUID)") UUID id,
    @Schema(description = "요청에 담긴 transaction_id입니다. 없었으면 null입니다.") @JsonProperty("transaction_id")
        String transactionId,
    @Schema(description = "요청에 담긴 customer_id입니다. 없거나 UUID로 읽을 수 없었으면 null입니다.")
        @JsonProperty("customer_id")
        UUID customerId,
    @Schema(description = "요청에 담긴 type입니다. 없었으면 null입니다.") String type,
    @Schema(description = "요청에 담긴 timestamp(ISO 8601)입니다. 없거나 읽을 수 없었으면 null입니다.")
        @JsonProperty("occurred_at")
        OffsetDateTime occurredAt,
    @Schema(
            description =
                "거절 사유입니다. customer_not_found, customer_archived, event_too_old, validation_error 가운데 하나입니다.")
        @JsonProperty("error_code")
        String errorCode,
    @Schema(
            description =
                "거절된 필드 이름 목록입니다. transaction_id, customer_id, type, timestamp, properties, events 가운데 해당하는 것을 이름순으로 싣습니다.")
        @JsonProperty("error_fields")
        List<String> errorFields,
    @Schema(description = "거절한 요청의 요청 ID(응답 헤더 X-Request-ID)") @JsonProperty("request_id")
        UUID requestId,
    @Schema(
            description =
                "batch 요청이면 events 배열에서의 위치(0부터)입니다. 단건 요청이거나, 이벤트 수집이 202로 답한 뒤 저장할 때 거절했으면 null입니다.")
        @JsonProperty("batch_index")
        Integer batchIndex,
    @Schema(description = "거절을 기록한 시각(ISO 8601)입니다. 이벤트 수집이 202로 답한 뒤 저장할 때 거절했으면 그 이벤트의 수신 시각입니다.")
        @JsonProperty("recorded_at")
        OffsetDateTime recordedAt) {}
