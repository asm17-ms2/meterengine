package com.meterengine.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

// 카프카 토픽 event.ingest에 싣는 메시지다. 컨슈머에는 요청과 인증 정보가 없어 event 한 행을 만드는 값을 전부 싣는다.
// 필드는 더하기만 하고 이름과 형은 바꾸지 않는다. 깨지는 변경일 때만 schema_version을 올린다.
public record EventIngestMessage(
    @JsonProperty("schema_version") int schemaVersion,
    @JsonProperty("organization_id") UUID organizationId,
    @JsonProperty("environment_id") UUID environmentId,
    @JsonProperty("transaction_id") String transactionId,
    @JsonProperty("customer_id") UUID customerId,
    String type,
    Map<String, Object> properties,
    @JsonProperty("occurred_at") OffsetDateTime occurredAt,
    @JsonProperty("accepted_at") Instant acceptedAt,
    @JsonProperty("request_id") UUID requestId,
    @JsonProperty("actor_type") String actorType,
    @JsonProperty("actor_id") UUID actorId) {

  public static final int CURRENT_SCHEMA_VERSION = 1;

  // 같은 transaction_id가 같은 파티션에 들어가야 먼저 받은 것이 먼저 저장된다.
  @JsonIgnore
  public String key() {
    return organizationId + ":" + transactionId;
  }
}
