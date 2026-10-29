package com.meterengine.event.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.global.error.ErrorResponse;
import java.util.List;

public record IngestEventBatchResponse(List<EventResult> results) {
  public record EventResult(
      int index,
      @JsonProperty("transaction_id") String transactionId,
      String result,
      @JsonInclude(JsonInclude.Include.NON_NULL) ErrorResponse error) {}
}
