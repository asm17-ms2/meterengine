package com.meterengine.metric.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.global.validation.StorableJsonValidator;
import com.meterengine.global.validation.StorableText;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.List;

public record UpdateBillableMetricRequest(
    @NotBlank @StorableText String name,
    @NotBlank @StorableText @JsonProperty("event_type") String eventType,
    @NotBlank @StorableText String aggregation,
    @StorableText @JsonProperty("target_property") String targetProperty,
    @Schema(description = "단가를 가르는 이벤트 속성 키. 속성에 따라 단가가 갈리지 않으면 빈 배열")
        @JsonProperty("dimension_properties")
        List<@StorableText String> dimensionProperties,
    @Schema(description = "집계량을 나눌 양수 단위. 기본값은 1")
        @Digits(
            integer = StorableJsonValidator.MAX_INTEGER_DIGITS,
            fraction = StorableJsonValidator.MAX_FRACTION_DIGITS)
        @JsonProperty("unit_size")
        BigDecimal unitSize,
    @Schema(description = "단위 변환 후 반올림: none, ceil, floor, round. 기본값은 none")
        @StorableText
        @JsonProperty("rounding_mode")
        String roundingMode,
    @Schema(description = "반올림할 소수 자릿수 0~12. 기본값은 0")
        @Min(0) @Max(12) @JsonProperty("rounding_precision")
        Integer roundingPrecision,
    @Schema(description = "percentile 집계에서만 지정하는 백분위 0~100")
        @Digits(
            integer = StorableJsonValidator.MAX_INTEGER_DIGITS,
            fraction = StorableJsonValidator.MAX_FRACTION_DIGITS)
        BigDecimal percentile) {
  public UpdateBillableMetricRequest(
      String name,
      String eventType,
      String aggregation,
      String targetProperty,
      List<String> dimensionProperties) {
    this(name, eventType, aggregation, targetProperty, dimensionProperties, null, null, null, null);
  }
}
