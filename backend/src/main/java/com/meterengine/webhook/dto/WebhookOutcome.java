package com.meterengine.webhook.dto;

public record WebhookOutcome(Integer responseStatus, String failureReason, long durationMs) {
  public boolean isSuccessful() {
    return responseStatus != null && responseStatus >= 200 && responseStatus < 300;
  }
}
