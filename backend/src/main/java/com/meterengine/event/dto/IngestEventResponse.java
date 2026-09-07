package com.meterengine.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 이벤트 수집 응답 본문 (MS2-130).
 *
 * <p>신규 저장과 중복 재전송이 모두 200이다. 도입사 입장에서 재전송은 실패가 아니라 "이미 처리됨"이고, 재시도가 안전해야 수집 클라이언트가 단순해진다.
 *
 * <p>duplicate가 true면 이번 요청은 아무것도 저장하지 않았고 최초 저장본이 그대로 남아 있다 (first-write-wins). 같은 키로 내용이 다른 요청이
 * 와도 마찬가지다.
 */
public record IngestEventResponse(
    @JsonProperty("transaction_id") String transactionId, boolean duplicate) {

  public static IngestEventResponse stored(String transactionId) {
    return new IngestEventResponse(transactionId, false);
  }

  public static IngestEventResponse alreadyStored(String transactionId) {
    return new IngestEventResponse(transactionId, true);
  }
}
