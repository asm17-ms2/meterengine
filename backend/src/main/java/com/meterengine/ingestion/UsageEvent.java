package com.meterengine.ingestion;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 저장된 사용량 이벤트 한 건. raw 청구 근거이며 불변이다 (MS2-26 결정 3).
 *
 * <p>id와 received_at은 DB가 생성한다 (gen_random_uuid, clock_timestamp). customer_id와 meter_id는 수신 시 해소
 * 결과이며 미해소면 null이다 (결정 1-B).
 */
public record UsageEvent(
    UUID id,
    UUID organizationId,
    String transactionId,
    String externalCustomerId,
    UUID customerId,
    String code,
    UUID meterId,
    Map<String, Object> properties,
    OffsetDateTime occurredAt,
    OffsetDateTime receivedAt) {}
