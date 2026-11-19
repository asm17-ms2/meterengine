package com.meterengine.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.UUID;

public record EventIssueResponse(
    @Schema(description = "수집 요청의 transaction_id입니다.") @JsonProperty("transaction_id")
        String transactionId,
    @Schema(description = "고객 ID(UUID)") @JsonProperty("customer_id") UUID customerId,
    @Schema(description = "고객 이름") @JsonProperty("customer_name") String customerName,
    @Schema(description = "수집 요청의 type 값입니다.") String type,
    @Schema(description = "이벤트가 발생한 시각(ISO 8601)") @JsonProperty("occurred_at")
        OffsetDateTime occurredAt,
    @Schema(description = "수신 시각(ISO 8601)입니다. MeterEngine이 이벤트를 받은 시각입니다.")
        @JsonProperty("received_at")
        OffsetDateTime receivedAt,
    @Schema(
            description =
                "문제 종류입니다. unknown_event_type(연결할 과금 미터 없음), invalid_target_property(수량을 읽을 수 없음), unpriced_usage(적용 단가 없음) 가운데 하나입니다.")
        String reason,
    @Schema(description = "문제가 된 과금 미터의 과금 미터 코드입니다. unknown_event_type이면 null입니다.")
        @JsonProperty("billable_metric_code")
        String billableMetricCode,
    @Schema(description = "그 과금 미터가 수량을 읽는 속성 이름입니다. unknown_event_type이거나 count 과금 미터면 null입니다.")
        @JsonProperty("target_property")
        String targetProperty,
    @Schema(
            description =
                "다음에 할 일을 알려 주는 안내 문구입니다. unpriced_usage이고 그 달 청구서를 이미 발행했으면, 단가를 등록해도 그 달 청구에 반영되지 않는다고 알립니다.")
        @JsonProperty("action_hint")
        String actionHint,
    @Schema(description = "unpriced_usage이고 이벤트의 수량 값이 음수면 true입니다. 그 밖에는 false입니다.")
        @JsonProperty("negative_quantity")
        boolean negativeQuantity) {}
