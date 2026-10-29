package com.meterengine.webhook.entity;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum WebhookEventType {
  WEBHOOK_TEST,
  UNBILLED_USAGE_DETECTED,
  PAYMENT_SUCCEEDED,
  PAYMENT_FAILED,
  PAYMENT_CANCELED;

  @JsonValue
  public String getValue() {
    return name().toLowerCase(Locale.ROOT);
  }
}
