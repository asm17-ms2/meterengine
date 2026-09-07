package com.meterengine.pricing.exception;

public class PricePolicyAlreadyExistsException extends RuntimeException {

  public PricePolicyAlreadyExistsException(String billableMetricCode) {
    super(
        "metric %s already has a price policy; it cannot be registered twice"
            .formatted(billableMetricCode));
  }
}
