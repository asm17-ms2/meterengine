package com.meterengine.metric.dto;

import java.util.List;

public record BillableMetricListResponse(List<BillableMetricResponse> metrics) {}
