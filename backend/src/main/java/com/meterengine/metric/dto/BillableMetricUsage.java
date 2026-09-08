package com.meterengine.metric.dto;

import com.meterengine.metric.entity.BillableMetric;
import java.util.List;

public record BillableMetricUsage(BillableMetric billableMetric, List<CustomerUsage> customers) {}
