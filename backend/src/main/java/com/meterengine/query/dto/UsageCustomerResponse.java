package com.meterengine.query.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.metric.dto.BillableMetricUsageResponse;
import java.util.List;
import java.util.UUID;

public record UsageCustomerResponse(
    @JsonProperty("customer_id") UUID customerId,
    @JsonProperty("customer_name") String customerName,
    List<BillableMetricUsageResponse> metrics) {}
