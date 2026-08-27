package com.meterengine.metric.exception;

import java.util.UUID;

public class MetricNotFoundException extends RuntimeException {

  public MetricNotFoundException(UUID organizationId, String metricCode) {
    super(
        "no metric %s in organization %s; it may not exist or belong to another organization"
            .formatted(metricCode, organizationId));
  }
}
