package com.meterengine.metric.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * 사용량 조회 응답 본문 (MS2-138).
 *
 * <p>JSON 이름은 {@code @JsonProperty}로 하나씩 못박는다. 전역 SNAKE_CASE 설정은 springdoc 생성물처럼 우리가 만들지 않은 응답까지
 * 바꾼다 (수집 API의 DTO와 같은 규칙).
 *
 * @param month 집계 기준 월. yyyy-MM, KST 기준이다. 요청이 month를 생략했을 때 어느 달로 계산했는지 응답만 보고 알 수 있어야 한다
 */
public record MetricUsageResponse(String month, List<MetricEntry> metrics) {

  public static MetricUsageResponse from(YearMonth month, List<MetricUsage> metricUsages) {
    return new MetricUsageResponse(
        month.toString(), metricUsages.stream().map(MetricEntry::from).toList());
  }

  /** 미터 하나와 그 미터로 잰 고객별 사용량. */
  public record MetricEntry(
      String code,
      String name,
      @JsonProperty("event_type") String eventType,
      String aggregation,
      @JsonProperty("target_property") String targetProperty,
      List<CustomerEntry> customers) {

    static MetricEntry from(MetricUsage usage) {
      return new MetricEntry(
          usage.metric().getCode(),
          usage.metric().getName(),
          usage.metric().getEventType(),
          usage.metric().getAggregation(),
          usage.metric().getTargetProperty(),
          usage.customers().stream().map(CustomerEntry::from).toList());
    }
  }

  /** 고객 한 명의 사용량. 이벤트가 없는 고객도 quantity 0으로 들어 있다. */
  public record CustomerEntry(
      @JsonProperty("customer_id") UUID customerId,
      @JsonProperty("customer_name") String customerName,
      BigDecimal quantity) {

    static CustomerEntry from(CustomerUsage usage) {
      return new CustomerEntry(usage.customerId(), usage.customerName(), usage.quantity());
    }
  }
}
