package com.meterengine.payment.service;

import java.util.UUID;

public record PaymentRetryEligibility(
    boolean allowed, String reason, UUID failedAttemptId, long attemptCount) {
  static PaymentRetryEligibility blocked(String reason) {
    return new PaymentRetryEligibility(false, reason, null, 0);
  }
}
