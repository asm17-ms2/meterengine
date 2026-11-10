package com.meterengine.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.global.error.ErrorResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record IngestEventBatchResponse(List<EventResult> results) {

  // 발행만 하고 저장은 뒤에서 하는 원소가 하나라도 있는지.
  @JsonIgnore
  public boolean hasAcceptedEvent() {
    return results.stream().anyMatch(result -> EventResult.ACCEPTED.equals(result.result()));
  }

  public record EventResult(
      int index,
      @JsonProperty("transaction_id") String transactionId,
      @Schema(allowableValues = {"stored", "duplicate", "rejected", "accepted", "failed"})
          String result,
      @JsonInclude(JsonInclude.Include.NON_NULL) ErrorResponse error) {

    // 받았고 저장은 뒤에서 한다.
    public static final String ACCEPTED = "accepted";
    // 받지 못했다. 같은 transaction_id로 다시 보내면 된다.
    public static final String FAILED = "failed";
  }
}
