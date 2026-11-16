package com.meterengine.event.dto;

import com.meterengine.global.security.BusinessEnvironmentContext;
import java.util.UUID;

// event에서 한 행을 가리키는 값. transaction_id는 도입사의 환경 안에서만 겹치지 않는다.
public record EventTransactionKey(
    UUID organizationId, UUID environmentScopeId, String transactionId) {

  public static EventTransactionKey of(EventIngestMessage message) {
    return new EventTransactionKey(
        message.organizationId(),
        BusinessEnvironmentContext.scopeId(message.environmentId()),
        message.transactionId());
  }
}
