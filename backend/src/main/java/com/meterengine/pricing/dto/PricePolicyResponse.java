package com.meterengine.pricing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 가격 정책 하나의 응답 표현 (MS2-157).
 *
 * <p>등록 응답이 저장된 정책 전체를 담아, 단건 조회가 생기기 전까지 이 응답이 반영 결과를 확인하는 창구다. 조회 API(MS2-176)가 생기면 같은 모양을 쓴다.
 *
 * <p>필드 이름 {@code metric_code}는 사용량과 청구 예정액 응답이 이미 쓰는 이름이다.
 */
public record PricePolicyResponse(
    @JsonProperty("metric_code") String metricCode,
    @JsonProperty("dimension_properties") List<String> dimensionProperties,
    @JsonProperty("rates") List<RateResponse> rates) {

  /** 단가 한 행의 응답 표현. */
  public record RateResponse(
      @JsonProperty("dimension_values") Map<String, String> dimensionValues,
      @JsonProperty("unit_price") BigDecimal unitPrice) {}

  public static PricePolicyResponse from(String metricCode, SavePricePolicyRequest request) {
    return new PricePolicyResponse(
        metricCode,
        List.copyOf(request.dimensionProperties()),
        request.rates().stream()
            .map(rate -> new RateResponse(Map.copyOf(rate.dimensionValues()), rate.unitPrice()))
            .toList());
  }
}
