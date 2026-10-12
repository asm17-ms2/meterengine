package com.meterengine.invoice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DraftInvoiceResponse(
    String month,
    @JsonProperty("calculated_at") OffsetDateTime calculatedAt,
    @JsonProperty("total_amount") long totalAmount,
    List<DraftInvoiceCustomer> customers) {

  public record DraftInvoiceCustomer(
      @JsonProperty("customer_id") UUID customerId,
      @JsonProperty("customer_name") String customerName,
      long amount,
      List<DraftInvoiceLine> lines) {}

  public record DraftInvoiceLine(
      @JsonProperty("billable_metric_code") String billableMetricCode,
      @JsonProperty("target_property") String targetProperty,
      @Schema(
              description =
                  "미터의 dimension_properties마다 이벤트 properties에서 뽑은 값. 같은 조합의 단가를 곱한다. 미터에 dimension_properties가 없으면 빈 객체다")
          @JsonProperty("dimension_values")
          Map<String, Object> dimensionValues,
      BigDecimal quantity,
      @JsonProperty("unit_price") BigDecimal unitPrice,
      long amount) {}
}
