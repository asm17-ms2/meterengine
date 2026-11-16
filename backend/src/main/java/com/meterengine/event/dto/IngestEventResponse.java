package com.meterengine.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

public record IngestEventResponse(
    @Schema(description = "요청에 보낸 transaction_id") @JsonProperty("transaction_id")
        String transactionId,
    @Schema(
            description =
                "200 응답에만 실립니다. false면 이번 요청으로 저장했고, true면 같은 transaction_id의 이벤트가 이미 있어 저장하지 않고 처음 것을 유지했습니다. 202 응답에는 아직 저장 전이라 없습니다.")
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
