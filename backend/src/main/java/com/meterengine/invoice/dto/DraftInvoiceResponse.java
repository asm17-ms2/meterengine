package com.meterengine.invoice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 청구 예정액 조회 응답 본문 (MS2-124).
 *
 * <p>화면의 표와 같은 3계층이다: 전체 합계, 고객 소계, 미터 라인. 합계와 소계를 전부 서버가 계산해 싣는다. 화면이 금액을 직접 더하기 시작하면 라인별 절사 규칙이
 * 프론트에 복제되기 때문이다 (MS2-127 인수 조건 "프론트엔드에 별도의 연산 로직이 없다").
 *
 * <p>JSON 이름은 {@code @JsonProperty}로 하나씩 못박는다 (수집 API의 DTO와 같은 규칙).
 *
 * @param month 집계 기준 월. yyyy-MM, KST 기준이다
 * @param calculatedAt 이 응답을 계산한 서버 시각(KST). 집계가 조회 시점 계산이라 화면의 "계산 시각"은 이 값이 정본이다
 * @param totalAmount 도입사 전체 청구 예정액(원). 고객 amount의 합이다
 */
public record DraftInvoiceResponse(
    String month,
    @JsonProperty("calculated_at") OffsetDateTime calculatedAt,
    @JsonProperty("total_amount") long totalAmount,
    List<DraftInvoiceCustomer> customers) {

  /**
   * 고객 한 명의 청구 예정액.
   *
   * <p>이벤트가 없는 고객도 도입사의 모든 미터 라인을 수량 0으로 갖는다. 화면이 고객마다 같은 행 구조를 그리게 하기 위해서다.
   */
  public record DraftInvoiceCustomer(
      @JsonProperty("customer_id") UUID customerId,
      @JsonProperty("customer_name") String customerName,
      long amount,
      List<DraftInvoiceLine> lines) {}

  /**
   * 미터 하나가 만든 청구 라인. 고객 금액이 어느 미터에서 나왔는지 분해한 줄이고, 인보이스가 확정되는 후속 스토리에서 line item이 되는 단위다.
   *
   * @param quantity 집계 수량. properties의 숫자를 그대로 합산한 값이라 소수일 수 있다 (MS2-129와 같은 이유)
   * @param amount 이 라인의 금액(원). quantity x unitPrice에서 원 미만을 절사한 값이다
   */
  public record DraftInvoiceLine(
      @JsonProperty("billable_metric_code") String billableMetricCode,
      @JsonProperty("target_property") String targetProperty,
      BigDecimal quantity,
      @JsonProperty("unit_price") BigDecimal unitPrice,
      long amount) {}
}
