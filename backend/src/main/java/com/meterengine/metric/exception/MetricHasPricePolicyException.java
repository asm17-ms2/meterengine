package com.meterengine.metric.exception;

public class MetricHasPricePolicyException extends RuntimeException {

  public MetricHasPricePolicyException(String metricCode) {
    super(
        "a price policy references metric %s; the metric cannot be deleted while referenced"
            .formatted(metricCode));
  }
}
