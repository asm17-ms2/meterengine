package com.meterengine.payment.service;

import com.meterengine.global.security.BusinessEnvironmentContext;
import com.meterengine.invoice.repository.InvoiceRepository;
import com.meterengine.payment.config.PaymentProperties;
import com.meterengine.payment.entity.BillingKey;
import com.meterengine.payment.entity.BillingKeyId;
import com.meterengine.payment.entity.PaymentAttempt;
import com.meterengine.payment.repository.BillingKeyRepository;
import com.meterengine.payment.repository.PaymentAttemptRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentRetryEligibilityService {
  private final PaymentAttemptRepository paymentAttemptRepository;
  private final BillingKeyRepository billingKeyRepository;
  private final TossPaymentsCredentialService tossPaymentsCredentialService;
  private final PaymentSettingService paymentSettingService;
  private final PaymentRetryPolicy paymentRetryPolicy;
  private final PaymentProperties paymentProperties;
  private final InvoiceRepository invoiceRepository;

  PaymentRetryEligibilityService(
      PaymentAttemptRepository paymentAttemptRepository,
      BillingKeyRepository billingKeyRepository,
      TossPaymentsCredentialService tossPaymentsCredentialService,
      PaymentSettingService paymentSettingService,
      PaymentRetryPolicy paymentRetryPolicy,
      PaymentProperties paymentProperties,
      InvoiceRepository invoiceRepository) {
    this.paymentAttemptRepository = paymentAttemptRepository;
    this.billingKeyRepository = billingKeyRepository;
    this.tossPaymentsCredentialService = tossPaymentsCredentialService;
    this.paymentSettingService = paymentSettingService;
    this.paymentRetryPolicy = paymentRetryPolicy;
    this.paymentProperties = paymentProperties;
    this.invoiceRepository = invoiceRepository;
  }

  @Transactional(readOnly = true)
  public PaymentRetryEligibility get(UUID organizationId, UUID invoiceId, String mode) {
    if (!mode.equals(paymentSettingService.findMode(organizationId)))
      return PaymentRetryEligibility.blocked("payment_mode_changed");
    if (paymentAttemptRepository.findActive(organizationId, invoiceId, mode).isPresent())
      return PaymentRetryEligibility.blocked("payment_not_failed");
    if ((BusinessEnvironmentContext.environmentId() == null
            ? invoiceRepository.findByOrganizationIdAndId(organizationId, invoiceId)
            : invoiceRepository.findScoped(
                organizationId, BusinessEnvironmentContext.environmentId(), invoiceId))
        .filter(invoice -> invoice.getTotalAmount() > 0)
        .isEmpty()) return PaymentRetryEligibility.blocked("payment_not_failed");
    Optional<PaymentAttempt> latest =
        paymentAttemptRepository.findLatest(organizationId, invoiceId, mode);
    if (latest.isEmpty() || !PaymentAttempt.FAILED.equals(latest.get().getStatus()))
      return PaymentRetryEligibility.blocked("payment_not_failed");
    PaymentAttempt failed = latest.get();
    if (failed.getCompletedAt() == null)
      return PaymentRetryEligibility.blocked("payment_result_unconfirmed");
    long count =
        paymentAttemptRepository.countByOrganizationIdAndInvoiceIdAndMode(
            organizationId, invoiceId, mode);
    if (count >= paymentProperties.retryMaxAttempts())
      return PaymentRetryEligibility.blocked("retry_limit_reached");
    Optional<BillingKey> billingKey =
        billingKeyRepository
            .findById(new BillingKeyId(organizationId, failed.getCustomerId(), mode))
            .filter(key -> BusinessEnvironmentContext.matches(key.getEnvironmentId()))
            .filter(key -> key.getArchivedAt() == null);
    if (billingKey.isEmpty()) return PaymentRetryEligibility.blocked("payment_method_missing");
    var credential =
        BusinessEnvironmentContext.environmentId() == null
                && failed.getCredentialUpdatedAt() == null
            ? tossPaymentsCredentialService.active(organizationId, mode)
            : tossPaymentsCredentialService.chargeable(
                organizationId,
                mode,
                failed.getCredentialUpdatedAt() == null
                    ? failed.getRequestedAt()
                    : failed.getCredentialUpdatedAt());
    if (credential.isEmpty()) return PaymentRetryEligibility.blocked("credentials_missing");
    // 더 새 시도가 있는 실패는 그 시도를 만들 때 미청구로 확인했으므로 관리자 수동 결제처럼 7일 창은 최신 실패에만 건다.
    if (TossPaymentsFailureCode.NOT_FOUND_PAYMENT.equals(failed.getFailureCode())
        && (BusinessEnvironmentContext.environmentId() == null
                    && failed.getCredentialUpdatedAt() == null
                ? java.util.Optional.of(credential.get())
                : tossPaymentsCredentialService.historical(
                    organizationId,
                    mode,
                    failed.getCredentialUpdatedAt() == null
                        ? failed.getRequestedAt()
                        : failed.getCredentialUpdatedAt()))
            .map(
                original ->
                    PaymentRetryPolicy.isUnconfirmable(
                        failed, original.getUpdatedAt(), OffsetDateTime.now()))
            .orElse(true)) return PaymentRetryEligibility.blocked("payment_result_unconfirmed");
    if (paymentRetryPolicy.nextRetryAt(
            failed.getFailureCode(),
            count,
            failed.getCompletedAt(),
            billingKey.get().getAuthenticatedAt())
        == null)
      return PaymentRetryEligibility.blocked(
          failed.getFailureCode() != null
                  && paymentRetryPolicy.nonRetryableFailureCodes().contains(failed.getFailureCode())
              ? "card_update_required"
              : "retry_not_allowed");
    return new PaymentRetryEligibility(true, "available", failed.getId(), count);
  }
}
