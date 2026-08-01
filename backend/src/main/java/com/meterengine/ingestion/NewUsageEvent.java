package com.meterengine.ingestion;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/** 수집 요청에서 추출한 저장 대상 이벤트. id, received_at, 해소 컬럼은 저장 과정에서 정해진다. */
public record NewUsageEvent(
    UUID organizationId,
    String transactionId,
    String externalCustomerId,
    String code,
    Map<String, Object> properties,
    OffsetDateTime occurredAt) {}
