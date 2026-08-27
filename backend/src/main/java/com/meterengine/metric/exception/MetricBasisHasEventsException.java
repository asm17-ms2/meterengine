package com.meterengine.metric.exception;

public class MetricBasisHasEventsException extends RuntimeException {

  public MetricBasisHasEventsException(String metricCode, String eventType, String targetProperty) {
    super(
        "usage events exist for event_type %s and target_property %s; the aggregation basis of metric %s cannot change"
            .formatted(eventType, targetProperty, metricCode));
  }
}
