package com.meterengine.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.global.error.ErrorResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record IngestEventBatchResponse(
    @Schema(description = "보낸 순서대로 이벤트마다 결과 하나") List<EventResult> results) {

  // 발행만 하고 저장은 뒤에서 하는 원소가 하나라도 있는지.
  @JsonIgnore
  public boolean hasAcceptedEvent() {
    return results.stream().anyMatch(result -> EventResult.ACCEPTED.equals(result.result()));
  }

  public record EventResult(
      @Schema(description = "요청 events 배열에서의 위치(0부터)") int index,
      @Schema(
              description = "그 이벤트의 transaction_id입니다. 문자열 transaction_id가 없는 이벤트면 null입니다.",
              example = "tx-20261105-0001")
          @JsonProperty("transaction_id")
          String transactionId,
      @Schema(
              description =
                  "stored(저장함), duplicate(이미 저장돼 있어 저장하지 않음), accepted(받기만 함, 저장은 응답 뒤에 함), rejected(거절함), failed(받지 못함, 같은 transaction_id로 다시 보내야 함) 가운데 하나입니다.",
              allowableValues = {"stored", "duplicate", "rejected", "accepted", "failed"},
              example = "stored")
          String result,
      @Schema(
              description =
                  """
                  rejected와 failed일 때만 실립니다. 단건 오류 응답과 같은 형태입니다. 분기는 code로 합니다. message는 사람이 읽는 한국어 문구라 로그에 남기는 데만 쓰고, 바뀔 수 있으니 분기에 쓰지 않습니다.

                  rejected의 code는 다음 가운데 하나입니다.

                  - validation_error: 이벤트의 필드 값이 형식 검사를 통과하지 못했습니다. 필드가 빠졌거나, 빈 문자열이거나, customer_id가 UUID가 아니거나, timestamp에 오프셋이 없는 경우입니다. errors에 틀린 필드와 이유가 실리고, field는 events[2].timestamp처럼 이벤트 위치를 붙여 씁니다. 값의 타입이 맞지 않아 이벤트를 읽지 못했거나 원소가 JSON 객체가 아니면 field가 events[2]처럼 이벤트 위치만 가리킵니다.
                  - event_too_old: timestamp가 받은 시각보다 35일 넘게 지났습니다.
                  - customer_not_found: customer_id의 고객이 이 업무 환경에 없습니다. 200 응답에서만 나옵니다.
                  - customer_archived: 보관한 고객입니다. 200 응답에서만 나옵니다.

                  malformed_request_body는 이벤트 결과에 나오지 않습니다. 본문 전체를 JSON으로 읽을 수 없을 때 요청 전체를 400으로 거절하면서 씁니다.

                  failed의 code는 event_ingest_unavailable입니다.
                  """)
          @JsonInclude(JsonInclude.Include.NON_NULL)
          ErrorResponse error) {

    // 받았고 저장은 뒤에서 한다.
    public static final String ACCEPTED = "accepted";
    // 받지 못했다. 같은 transaction_id로 다시 보내면 된다.
    public static final String FAILED = "failed";
  }
}
