package com.meterengine.payment.service;

import com.meterengine.global.security.BusinessEnvironment;
import com.meterengine.global.security.BusinessEnvironmentContext;
import java.util.UUID;
import java.util.function.Supplier;

final class PaymentEnvironments {
  private PaymentEnvironments() {}

  static <T> T call(UUID organizationId, UUID environmentId, String mode, Supplier<T> action) {
    return BusinessEnvironmentContext.run(
        new BusinessEnvironment(organizationId, environmentId, environmentId == null ? null : mode),
        action);
  }
}
