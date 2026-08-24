package com.meterengine.metric.exception;

public class MetricAlreadyExistsException extends RuntimeException {

  public MetricAlreadyExistsException(String code) {
    super("metric %s already exists; use a different code".formatted(code));
  }
}
