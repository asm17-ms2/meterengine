package com.meterengine.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

public record IngestEventResponse(
    @JsonProperty("transaction_id") String transactionId,
    @Schema(description = "이미 저장된 transaction_id였는지. 202 응답에는 없다. 그때는 저장 전이라 아직 모른다")
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Boolean duplicate) {

  public static IngestEventResponse stored(String transactionId) {
    return new IngestEventResponse(transactionId, false);
  }

  public static IngestEventResponse alreadyStored(String transactionId) {
    return new IngestEventResponse(transactionId, true);
  }

  // 받아서 카프카에 발행했다. 저장과 중복 판정은 뒤에서 한다.
  public static IngestEventResponse accepted(String transactionId) {
    return new IngestEventResponse(transactionId, null);
  }

  @JsonIgnore
  public boolean storedInRequest() {
    return duplicate != null;
  }
}
