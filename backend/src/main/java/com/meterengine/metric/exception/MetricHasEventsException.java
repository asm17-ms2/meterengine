package com.meterengine.metric.exception;

public class MetricHasEventsException extends RuntimeException {

  public MetricHasEventsException(String metricCode) {
    super(
        "metric %s has usage events; deleting it would hide billable usage from the invoice"
            .formatted(metricCode));
  }
}
