package com.meterengine.payment.service;

import com.meterengine.global.database.DatabaseScope;
import com.meterengine.global.database.DatabaseScopes;
import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.payment.dto.PaymentJobResult;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

final class PaymentEnvironments {
  private PaymentEnvironments() {}

  static <T> T call(UUID organizationId, String mode, Supplier<T> action) {
    if (!PaymentSettingService.selectedMode().equals(mode)) {
      throw new ConflictException(ErrorCode.PAYMENT_MODE_MISMATCH);
    }
    return action.get();
  }

  public static PaymentJobResult all(Supplier<PaymentJobResult> action) {
    int processed = 0, skipped = 0, failed = 0;
    RuntimeException failure = null;
    for (var scope : List.of(DatabaseScope.TEST, DatabaseScope.LIVE)) {
      try {
        var result = DatabaseScopes.call(scope, action);
        processed += result.processedCount();
        skipped += result.skippedCount();
        failed += result.failedCount();
      } catch (RuntimeException exception) {
        if (failure == null) failure = exception;
        else failure.addSuppressed(exception);
      }
    }
    if (failure != null) throw failure;
    return new PaymentJobResult(processed, skipped, failed);
  }
}
