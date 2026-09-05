package com.meterengine.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * 이벤트 수집 요청 본문 (MS2-130).
 *
 * <p>다섯 필드가 전부 필수이고, V1 마이그레이션의 usage_event NOT NULL 컬럼 목록과 일치한다. 하나라도 없으면 400이고 저장은 0건이다 (스토리
 * MS2-121 팀 정책).
 *
 * <p>properties의 내용은 검증하지 않는다. 어느 키가 사용량 값인지는 billable_metric.target_property가 정하는데 이번 슬라이스는 미터를
 * 조회하지 않아 판정할 근거가 없다. model도 token도 필수가 아니다. 값의 유효성은 집계 시점(MS2-129)의 문제다.
 *
 * <p><b>"원문 그대로"가 아니라 "값 그대로"다.</b> 저장 타입이 jsonb라 키 순서와 공백은 보존되지 않고 중복 키는 하나만 남는다. 대신 값은 자릿수까지 그대로
 * 남긴다 (application.properties의 use-big-decimal-for-floats 참조).
 *
 * <p>received_at은 필드를 두지 않는다. 클라이언트가 무엇을 보내도 매핑되지 않으며, DB 트리거가 서버 시각으로 덮어쓴다 (V1에서 강제).
 *
 * <p>JSON 이름은 {@code @JsonProperty}로 하나씩 못박는다. 전역 SNAKE_CASE 설정을 켜면 springdoc 생성물처럼 우리가 만들지 않은 응답까지
 * 영향을 받는다.
 */
public record IngestEventRequest(
    /**
     * 멱등키. 상한이 필요한 이유는 (organization_id, transaction_id)가 PK, 즉 btree 인덱스이기 때문이다. btree는 인덱스 행이
     * 2704바이트를 넘을 수 없어 비압축 2704자에서 SQLSTATE 54000으로 실패한다(실측). 압축이 잘 되는 값은 3000자도 통과하므로, 상한이 없으면 길이가
     * 아니라 내용에 따라 갈리는 간헐적 실패가 되고 54000은 500으로 샌다. 255는 그 안쪽의 관례값이다(롤백된 ADR-0006과 같은 값).
     */
    @JsonProperty("transaction_id") @NotBlank @Size(max = 255) String transactionId,
    @JsonProperty("customer_id") @NotNull UUID customerId,
    @JsonProperty("type") @NotBlank String type,
    @NotNull Map<String, Object> properties,

    /** 이벤트 발생 시각. usage_event.occurred_at에 저장된다. */
    @JsonProperty("occurred_at") @NotNull OffsetDateTime occurredAt) {}
