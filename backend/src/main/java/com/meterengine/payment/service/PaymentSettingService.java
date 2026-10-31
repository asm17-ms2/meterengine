package com.meterengine.payment.service;

import com.meterengine.global.database.DatabaseScopes;
import com.meterengine.payment.dto.PaymentSettingResponse;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentSettingService {
  @Transactional(readOnly = true)
  public PaymentSettingResponse get(UUID organizationId) {
    return new PaymentSettingResponse(selectedMode(), null);
  }

  @Transactional(readOnly = true)
  String findMode(UUID organizationId) {
    return selectedMode();
  }

  static String selectedMode() {
    return switch (DatabaseScopes.current()) {
      case TEST -> "test";
      case LIVE -> "live";
      case SHARED -> throw new IllegalStateException("payment requires a business database scope");
    };
  }
}
