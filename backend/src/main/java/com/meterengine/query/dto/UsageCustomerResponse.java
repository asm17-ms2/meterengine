package com.meterengine.query.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.metric.dto.BillableMetricUsageResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

public record UsageCustomerResponse(
    @Schema(description = "고객 ID(UUID)") @JsonProperty("customer_id") UUID customerId,
    @Schema(description = "고객 이름") @JsonProperty("customer_name") String customerName,
    @Schema(
            description =
                "이 고객의 과금 미터별 사용량입니다. 항목은 GET /v1/usage의 billable_metric_usages 항목과 같고, customers에는"
                    + " 이 고객 하나만 담습니다.")
        List<BillableMetricUsageResponse> metrics) {}
