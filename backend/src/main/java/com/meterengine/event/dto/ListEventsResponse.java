package com.meterengine.event.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRawValue;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ListEventsResponse(
    @Schema(description = "조회한 달(yyyy-MM, KST)입니다. from과 to로 기간을 조회했으면 null입니다.") String month,
    @Schema(description = "0부터 세는 페이지 번호") int page,
    @Schema(description = "한 페이지에 담는 개수") int size,
    @Schema(description = "조건에 맞는 이벤트 전체 건수") long total,
    @Schema(description = "이 페이지의 이벤트") List<EventResponse> events,
    @Schema(description = "조회한 기간의 시작 시각(포함, ISO 8601)입니다. month로 조회했으면 그 달 1일 00:00 KST입니다.")
        @JsonProperty("from")
        OffsetDateTime fromAt,
    @Schema(description = "조회한 기간의 종료 시각(미포함, ISO 8601)입니다. month로 조회했으면 다음 달 1일 00:00 KST입니다.")
        @JsonProperty("to")
        OffsetDateTime toAt) {

  public ListEventsResponse(
      String month, int page, int size, long total, List<EventResponse> events) {
    this(month, page, size, total, events, null, null);
  }

  public record EventResponse(
      @Schema(description = "보낸 transaction_id") @JsonProperty("transaction_id")
          String transactionId,
      @Schema(description = "고객 ID(UUID)") @JsonProperty("customer_id") UUID customerId,
      @Schema(description = "고객 이름") @JsonProperty("customer_name") String customerName,
      @Schema(description = "수집 요청의 type 값입니다.") @JsonProperty("type") String type,
      @Schema(
              description = "보낸 properties입니다. 키 순서와 공백은 보낸 것과 다를 수 있고, 숫자 자릿수는 그대로입니다.",
              implementation = Map.class)
          @JsonRawValue
          String properties,
      @Schema(description = "이벤트가 발생한 시각(ISO 8601)입니다. 수집 요청의 timestamp입니다.")
          @JsonProperty("occurred_at")
          OffsetDateTime occurredAt,
      @Schema(description = "수신 시각(ISO 8601)입니다. MeterEngine이 이벤트를 받은 시각입니다.")
          @JsonProperty("received_at")
          OffsetDateTime receivedAt,
      @JsonProperty("request_id")
          @JsonInclude(JsonInclude.Include.NON_NULL)
          @Schema(
              description =
                  "이벤트를 처음 저장한 요청의 요청 ID(응답 헤더 X-Request-ID)입니다. 이 정보를 남기기 전에 저장한 이벤트에는 없습니다.",
              requiredMode = Schema.RequiredMode.NOT_REQUIRED)
          UUID requestId,
      @JsonProperty("actor_type")
          @JsonInclude(JsonInclude.Include.NON_NULL)
          @Schema(
              description =
                  "이벤트를 처음 저장한 요청의 주체 종류입니다. api_key(API 키) 또는 user_account(사용자 계정)이며, request_id가 없는 이벤트에는 없습니다.",
              requiredMode = Schema.RequiredMode.NOT_REQUIRED)
          String actorType,
      @JsonProperty("actor_id")
          @JsonInclude(JsonInclude.Include.NON_NULL)
          @Schema(
              description = "이벤트를 처음 저장한 요청의 API 키 ID나 사용자 계정 ID입니다. request_id가 없는 이벤트에는 없습니다.",
              requiredMode = Schema.RequiredMode.NOT_REQUIRED)
          UUID actorId) {
    public EventResponse(
        String transactionId,
        UUID customerId,
        String customerName,
        String type,
        String properties,
        OffsetDateTime occurredAt,
        OffsetDateTime receivedAt) {
      this(
          transactionId,
          customerId,
          customerName,
          type,
          properties,
          occurredAt,
          receivedAt,
          null,
          null,
          null);
    }
  }
}
