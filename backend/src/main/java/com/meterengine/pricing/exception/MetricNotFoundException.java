package com.meterengine.pricing.exception;

import java.util.UUID;

public class MetricNotFoundException extends RuntimeException {

  public MetricNotFoundException(UUID organizationId, String billableMetricCode) {
    super(
        "no metric %s in organization %s; it may not exist or belong to another organization"
            .formatted(billableMetricCode, organizationId));
  }
}
