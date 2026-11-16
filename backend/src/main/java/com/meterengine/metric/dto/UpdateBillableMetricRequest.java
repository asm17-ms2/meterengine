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
    @Schema(description = "콘솔과 청구서에 보이는 미터 이름") @NotBlank @StorableText String name,
    @Schema(description = "집계할 이벤트의 type 값입니다. 대소문자까지 같아야 집계합니다.")
        @NotBlank @StorableText
        @JsonProperty("event_type")
        String eventType,
    @Schema(
            description =
                "집계 함수입니다. sum, count, count_distinct, min, max, avg, first, last, median, percentile 가운데 하나이며 대소문자를 구분하지 않습니다.")
        @NotBlank @StorableText
        String aggregation,
    @Schema(description = "이벤트 properties에서 집계할 키입니다. count에는 넣지 않고, 다른 집계 함수에는 꼭 넣습니다.")
        @StorableText
        @JsonProperty("target_property")
        String targetProperty,
    @Schema(description = "단가를 가르는 이벤트 속성 키(구분 키)입니다. 생략하면 빈 배열이고, 같은 키를 두 번 넣거나 빈 키를 넣을 수 없습니다.")
        @JsonProperty("dimension_properties")
        List<@StorableText String> dimensionProperties,
    @Schema(description = "집계량을 나눌 양수 단위입니다. 기본값은 1입니다.")
        @Digits(
            integer = StorableJsonValidator.MAX_INTEGER_DIGITS,
            fraction = StorableJsonValidator.MAX_FRACTION_DIGITS)
        @JsonProperty("unit_size")
        BigDecimal unitSize,
    @Schema(
            description =
                "단위 변환 뒤 끝수 처리 방식입니다. none, ceil(올림), floor(내림), round(반올림, 사사오입) 가운데 하나이고 기본값은 none입니다.")
        @StorableText
        @JsonProperty("rounding_mode")
        String roundingMode,
    @Schema(description = "끝수 처리할 소수 자릿수(0~12)입니다. 기본값은 0이고, rounding_mode가 none이면 0으로 저장합니다.")
        @Min(0) @Max(12) @JsonProperty("rounding_precision")
        Integer roundingPrecision,
    @Schema(description = "percentile 집계에서만 넣는 백분위(0~100)")
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
