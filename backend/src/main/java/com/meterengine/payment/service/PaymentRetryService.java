package com.meterengine.payment.service;

import com.meterengine.global.security.BusinessEnvironmentContext;
import com.meterengine.invoice.repository.InvoiceRepository;
import com.meterengine.payment.config.PaymentProperties;
import com.meterengine.payment.dto.PaymentJobResult;
import com.meterengine.payment.entity.BillingKeyId;
import com.meterengine.payment.entity.PaymentAttempt;
import com.meterengine.payment.entity.TossPaymentsCredential;
import com.meterengine.payment.repository.BillingKeyRepository;
import com.meterengine.payment.repository.PaymentAttemptRepository;
import com.meterengine.payment.repository.PaymentRetryCandidateProjection;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaymentRetryService {

  private static final Logger logger = LoggerFactory.getLogger(PaymentRetryService.class);

  private final PaymentAttemptRepository paymentAttemptRepository;
  private final TossPaymentsCredentialService tossPaymentsCredentialService;
  private final BillingKeyRepository billingKeyRepository;
  private final PaymentSettingService paymentSettingService;
  private final PaymentAttemptService paymentAttemptService;
  private final PaymentRetryPolicy paymentRetryPolicy;
  private final PaymentProperties paymentProperties;
  private final TransactionTemplate transactionTemplate;
  private final InvoiceRepository invoiceRepository;

  PaymentRetryService(
      PaymentAttemptRepository paymentAttemptRepository,
      TossPaymentsCredentialService tossPaymentsCredentialService,
      BillingKeyRepository billingKeyRepository,
      PaymentSettingService paymentSettingService,
      PaymentAttemptService paymentAttemptService,
      PaymentRetryPolicy paymentRetryPolicy,
      PaymentProperties paymentProperties,
      PlatformTransactionManager platformTransactionManager,
      InvoiceRepository invoiceRepository) {
    this.paymentAttemptRepository = paymentAttemptRepository;
    this.tossPaymentsCredentialService = tossPaymentsCredentialService;
    this.billingKeyRepository = billingKeyRepository;
    this.paymentSettingService = paymentSettingService;
    this.paymentAttemptService = paymentAttemptService;
    this.paymentRetryPolicy = paymentRetryPolicy;
    this.paymentProperties = paymentProperties;
    this.transactionTemplate = new TransactionTemplate(platformTransactionManager);
    this.invoiceRepository = invoiceRepository;
  }

  public PaymentJobResult retry() {
    OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
    PaymentJobTally tally = new PaymentJobTally();
    for (PaymentRetryCandidateProjection candidate :
        paymentAttemptRepository.findRetryCandidates(
            now.minus(paymentProperties.retryInterval()),
            paymentProperties.retryMaxAttempts(),
            paymentRetryPolicy.nonRetryableFailureCodes(),
            PaymentRetryPolicy.ALREADY_REMOVED_BILLING_KEY,
            TossPaymentsFailureCode.NOT_FOUND_PAYMENT,
            paymentRetryPolicy.notFoundRetryableFrom(now))) {
      if (!isDue(candidate, now)) {
        continue;
      }
      try {
        tally.count(
            PaymentEnvironments.call(
                candidate.getOrganizationId(),
                candidate.getEnvironmentId(),
                candidate.getMode(),
                () -> retryCandidate(candidate)));
      } catch (RuntimeException exception) {
        logger.warn(
            "payment retry failed: failed_attempt_id={}, mode={}",
            candidate.getId(),
            candidate.getMode(),
            exception);
        tally.countFailed();
      }
    }
    return tally.result();
  }

  private boolean isDue(PaymentRetryCandidateProjection candidate, OffsetDateTime now) {
    OffsetDateTime retryAt =
        paymentRetryPolicy.nextRetryAt(
            candidate.getFailureCode(),
            candidate.getAttemptCount(),
            inUtc(candidate.getCompletedAt()),
            inUtc(candidate.getBillingKeyAuthenticatedAt()));
    return retryAt != null && !retryAt.isAfter(now);
  }

  private boolean retryCandidate(PaymentRetryCandidateProjection candidate) {
    // 키를 보관하면 새 결제를 멈춘다. 실패한 시도가 고정한 옛 키로도 다시 승인하지 않는다.
    if (tossPaymentsCredentialService
        .active(candidate.getOrganizationId(), candidate.getMode())
        .isEmpty()) {
      logSkipped(candidate, "toss_payments_credential_missing");
      return false;
    }
    PaymentAttempt original = paymentAttemptRepository.findById(candidate.getId()).orElseThrow();
    Optional<TossPaymentsCredential> credential =
        original.getCredentialUpdatedAt() != null
            ? tossPaymentsCredentialService.historical(
                candidate.getOrganizationId(),
                candidate.getMode(),
                original.getCredentialUpdatedAt())
            : candidate.getEnvironmentId() == null
                ? tossPaymentsCredentialService.active(
                    candidate.getOrganizationId(), candidate.getMode())
                : tossPaymentsCredentialService.historical(
                    candidate.getOrganizationId(), candidate.getMode(), original.getRequestedAt());
    if (credential.isEmpty()) {
      logSkipped(candidate, "toss_payments_credential_missing");
      return false;
    }
    if (TossPaymentsFailureCode.NOT_FOUND_PAYMENT.equals(candidate.getFailureCode())
        && !paymentAttemptService.isUncharged(
            candidate.getId(), candidate.getRequestedAt(), credential.get())) {
      return false;
    }
    Optional<PaymentAttempt> pendingAttempt =
        transactionTemplate.execute(status -> insertRetryAttempt(candidate));
    if (pendingAttempt == null || pendingAttempt.isEmpty()) {
      return false;
    }
    paymentAttemptService.approve(pendingAttempt.get(), null);
    return true;
  }

  private Optional<PaymentAttempt> insertRetryAttempt(PaymentRetryCandidateProjection candidate) {
    UUID organizationId = candidate.getOrganizationId();
    UUID invoiceId = candidate.getInvoiceId();
    String mode = candidate.getMode();
    (candidate.getEnvironmentId() == null
            ? invoiceRepository.findForUpdate(organizationId, invoiceId)
            : invoiceRepository.lockScoped(organizationId, candidate.getEnvironmentId(), invoiceId))
        .orElseThrow();
    PaymentAttempt failedAttempt =
        paymentAttemptRepository.findForUpdate(organizationId, candidate.getId()).orElseThrow();
    PaymentAttempt latestAttempt =
        paymentAttemptRepository.findLatest(organizationId, invoiceId, mode).orElseThrow();
    if (!latestAttempt.getId().equals(failedAttempt.getId())
        || paymentAttemptRepository.countByOrganizationIdAndInvoiceIdAndMode(
                organizationId, invoiceId, mode)
            != candidate.getAttemptCount()
        || !mode.equals(paymentSettingService.findMode(organizationId))
        || paymentAttemptRepository.findActive(organizationId, invoiceId, mode).isPresent()) {
      logSkipped(candidate, "candidate_changed");
      return Optional.empty();
    }
    if (billingKeyRepository
        .findById(new BillingKeyId(organizationId, failedAttempt.getCustomerId(), mode))
        .filter(key -> BusinessEnvironmentContext.matches(key.getEnvironmentId()))
        .filter(key -> key.getArchivedAt() == null)
        .isEmpty()) {
      logSkipped(candidate, "payment_method_missing");
      return Optional.empty();
    }
    return Optional.of(
        paymentAttemptService.savePending(
            organizationId,
            failedAttempt.getCustomerId(),
            invoiceId,
            mode,
            failedAttempt.getAmount(),
            failedAttempt.getOrderName()));
  }

  private static OffsetDateTime inUtc(Instant time) {
    return time == null ? null : time.atOffset(ZoneOffset.UTC);
  }

  private static void logSkipped(PaymentRetryCandidateProjection candidate, String reason) {
    logger.info(
        "payment retry skipped: failed_attempt_id={}, mode={}, reason={}",
        candidate.getId(),
        candidate.getMode(),
        reason);
  }
}
