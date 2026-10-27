package com.meterengine.payment.service;

import com.meterengine.audit.service.AuditLogService;
import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.security.BusinessEnvironmentContext;
import com.meterengine.payment.dto.PaymentSettingResponse;
import com.meterengine.payment.entity.PaymentSetting;
import com.meterengine.payment.entity.TossPaymentsCredentialId;
import com.meterengine.payment.repository.PaymentSettingRepository;
import com.meterengine.payment.repository.TossPaymentsCredentialRepository;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentSettingService {

  private final PaymentSettingRepository paymentSettingRepository;
  private final AuditLogService auditLogService;
  private final TossPaymentsCredentialRepository tossPaymentsCredentialRepository;

  PaymentSettingService(
      PaymentSettingRepository paymentSettingRepository,
      TossPaymentsCredentialRepository tossPaymentsCredentialRepository,
      AuditLogService auditLogService) {
    this.paymentSettingRepository = paymentSettingRepository;
    this.auditLogService = auditLogService;
    this.tossPaymentsCredentialRepository = tossPaymentsCredentialRepository;
  }

  @Transactional(readOnly = true)
  public PaymentSettingResponse get(UUID organizationId) {
    if (BusinessEnvironmentContext.mode() != null) {
      return new PaymentSettingResponse(BusinessEnvironmentContext.mode(), null);
    }
    return paymentSettingRepository
        .findById(organizationId)
        .map(PaymentSettingResponse::from)
        .orElseGet(() -> new PaymentSettingResponse(PaymentSetting.TEST, null));
  }

  @Transactional
  public PaymentSettingResponse update(UUID organizationId, String mode) {
    if (BusinessEnvironmentContext.mode() != null) {
      if (!BusinessEnvironmentContext.mode().equals(mode)) {
        throw new ConflictException(ErrorCode.PAYMENT_MODE_MISMATCH);
      }
      return get(organizationId);
    }
    if (PaymentSetting.LIVE.equals(mode)
        && tossPaymentsCredentialRepository
            .findById(new TossPaymentsCredentialId(organizationId, PaymentSetting.LIVE))
            .filter(credential -> credential.getArchivedAt() == null)
            .isEmpty()) {
      throw new ConflictException(ErrorCode.TOSS_PAYMENTS_CREDENTIAL_MISSING);
    }
    OffsetDateTime updatedAt = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
    PaymentSetting setting =
        paymentSettingRepository
            .findById(organizationId)
            .orElseGet(() -> new PaymentSetting(organizationId, mode, updatedAt));
    setting.update(mode, updatedAt);
    paymentSettingRepository.saveAndFlush(setting);
    auditLogService.record(
        organizationId,
        "payment_setting_updated",
        "payment_setting",
        organizationId.toString(),
        Map.of("mode", mode));
    return PaymentSettingResponse.from(setting);
  }

  @Transactional(readOnly = true)
  String findMode(UUID organizationId) {
    if (BusinessEnvironmentContext.mode() != null) return BusinessEnvironmentContext.mode();
    return paymentSettingRepository
        .findById(organizationId)
        .map(PaymentSetting::getMode)
        .orElse(PaymentSetting.TEST);
  }
}
