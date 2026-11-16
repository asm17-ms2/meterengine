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
      @Schema(description = "그 이벤트의 transaction_id입니다. 문자열 transaction_id가 없는 이벤트면 null입니다.")
          @JsonProperty("transaction_id")
          String transactionId,
      @Schema(
              description =
                  "accepted(받음, 저장은 뒤에서 함), failed(받지 못함, 같은 transaction_id로 다시 보내야 함), stored(저장함), duplicate(이미 저장돼 있음), rejected(거절함) 가운데 하나입니다.",
              allowableValues = {"stored", "duplicate", "rejected", "accepted", "failed"})
          String result,
      @Schema(
              description =
                  "rejected와 failed일 때만 실립니다. 단건 오류 응답과 같은 형태이고, errors의 field는 events[2].timestamp처럼 이벤트 위치를 붙여 씁니다. failed의 code는 event_ingest_unavailable입니다.")
          @JsonInclude(JsonInclude.Include.NON_NULL)
          ErrorResponse error) {

    // 받았고 저장은 뒤에서 한다.
    public static final String ACCEPTED = "accepted";
    // 받지 못했다. 같은 transaction_id로 다시 보내면 된다.
    public static final String FAILED = "failed";
  }
}
