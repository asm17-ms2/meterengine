package com.meterengine.event.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 조회된 이벤트 한 건 (MS2-131).
 *
 * @param customerName 이벤트 행에는 {@code customer_id}만 있어서 customer를 조인해 가져온다. 화면 표의 고객 컬럼이 UUID가 아니라
 *     이름을 보여준다.
 * @param propertiesJson DB의 jsonb를 문자열 그대로 담는다. 파싱하지 않는다 (이유는 {@link
 *     EventPageResponse.EventEntry#properties()} 참조).
 */
public record UsageEventRow(
    String transactionId,
    UUID customerId,
    String customerName,
    String eventType,
    String propertiesJson,
    OffsetDateTime occurredAt,
    OffsetDateTime receivedAt) {}
