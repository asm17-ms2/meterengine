package com.meterengine.pricing.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 가격 정책 등록의 요청 본문 (MS2-157).
 *
 * <p>정책(축 선언)과 단가를 한 본문으로 받는다. price_rate의 FK가 price_policy를 가리켜 "축 선언 없는 단가"를 DB가 막듯이, 여기서는 반대로
 * "단가 없는 정책"이 API로 생기지 않게 한다. 정책만 있고 단가가 없으면 청구 예정액 계산이 그 미터에서 실패한다.
 *
 * <p>여기 애노테이션은 필드 하나씩의 형식만 본다(400 validation_error). 필드 사이의 관계 -- 조합의 키 집합이 선언과 일치하는지, 기본 단가 행이 있는지
 * -- 는 서비스가 검증한다(400 invalid_price_policy). JSONB 키 오타는 DB가 못 잡으므로 그 검증이 방어선이다 (V2 마이그레이션 주석).
 *
 * @param dimensionProperties 이 미터의 가격을 가르는 이벤트 속성 키의 집합. 무차원이면 빈 배열이다
 * @param rates 조합별 단가. dimension_values가 빈 객체({})인 기본 단가 행이 반드시 하나 있어야 한다
 */
public record SavePricePolicyRequest(
    @NotNull @JsonProperty("dimension_properties") List<String> dimensionProperties,
    // 원소 단위 @NotNull이 없으면 [null]이 @Valid 캐스케이드를 조용히 건너뛰어 서비스에서 NPE(500)가 된다.
    @NotEmpty @JsonProperty("rates") List<@NotNull @Valid RateEntry> rates) {

  /**
   * 단가 한 행.
   *
   * @param dimensionValues 이 단가가 적용되는 조합. 기본 단가는 빈 객체다
   * @param unitPrice 단가. 소수를 허용하고 음수는 안 된다 (price_rate의 CHECK와 같은 규칙)
   */
  public record RateEntry(
      @NotNull @JsonProperty("dimension_values") Map<String, String> dimensionValues,
      @NotNull @DecimalMin("0") @JsonProperty("unit_price") BigDecimal unitPrice) {}
}
