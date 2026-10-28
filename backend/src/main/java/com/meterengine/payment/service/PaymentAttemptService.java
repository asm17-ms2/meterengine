package com.meterengine.payment.service;

import com.meterengine.audit.service.AuditActor;
import com.meterengine.audit.service.AuditLogService;
import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.InvalidRequestException;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.global.security.BusinessEnvironmentContext;
import com.meterengine.invoice.entity.Invoice;
import com.meterengine.invoice.repository.InvoiceRepository;
import com.meterengine.payment.client.TossPaymentsApproveBillingRequest;
import com.meterengine.payment.client.TossPaymentsClient;
import com.meterengine.payment.client.TossPaymentsError;
import com.meterengine.payment.client.TossPaymentsPayment;
import com.meterengine.payment.client.TossPaymentsResult;
import com.meterengine.payment.config.PaymentProperties;
import com.meterengine.payment.dto.ListPaymentAttemptsResponse;
import com.meterengine.payment.dto.PaymentAttemptResponse;
import com.meterengine.payment.entity.BillingKey;
import com.meterengine.payment.entity.BillingKeyId;
import com.meterengine.payment.entity.PaymentAttempt;
import com.meterengine.payment.entity.PaymentSetting;
import com.meterengine.payment.entity.TossPaymentsCredential;
import com.meterengine.payment.repository.BillingKeyRepository;
import com.meterengine.payment.repository.PaymentAttemptRepository;
import com.meterengine.webhook.entity.WebhookEventType;
import com.meterengine.webhook.service.WebhookEventService;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaymentAttemptService {

  private static final Logger logger = LoggerFactory.getLogger(PaymentAttemptService.class);
  private static final String ORDER_NAME_SUFFIX = " 사용료";
  private static final String TOSS_PAYMENT_DONE = "DONE";
  private static final Set<String> TOSS_PAYMENT_ENDED_WITHOUT_APPROVAL =
      Set.of("ABORTED", "EXPIRED");
  private static final String ENDED_WITHOUT_APPROVAL_MESSAGE = "토스페이먼츠 결제가 승인되지 않고 끝났습니다";

  private final PaymentAttemptRepository paymentAttemptRepository;
  private final InvoiceRepository invoiceRepository;
  private final PaymentSettingService paymentSettingService;
  private final TossPaymentsCredentialService tossPaymentsCredentialService;
  private final BillingKeyRepository billingKeyRepository;
  private final TossPaymentsClient tossPaymentsClient;
  private final PaymentMetrics paymentMetrics;
  private final PaymentProperties paymentProperties;
  private final TransactionTemplate transactionTemplate;
  private final AuditLogService auditLogService;
  private final WebhookEventService webhookEventService;
  private final PaymentRetryEligibilityService paymentRetryEligibilityService;

  PaymentAttemptService(
      PaymentAttemptRepository paymentAttemptRepository,
      InvoiceRepository invoiceRepository,
      PaymentSettingService paymentSettingService,
      TossPaymentsCredentialService tossPaymentsCredentialService,
      BillingKeyRepository billingKeyRepository,
      TossPaymentsClient tossPaymentsClient,
      PaymentMetrics paymentMetrics,
      PaymentProperties paymentProperties,
      PlatformTransactionManager platformTransactionManager,
      AuditLogService auditLogService,
      WebhookEventService webhookEventService,
      PaymentRetryEligibilityService paymentRetryEligibilityService) {
    this.paymentAttemptRepository = paymentAttemptRepository;
    this.invoiceRepository = invoiceRepository;
    this.paymentSettingService = paymentSettingService;
    this.tossPaymentsCredentialService = tossPaymentsCredentialService;
    this.billingKeyRepository = billingKeyRepository;
    this.tossPaymentsClient = tossPaymentsClient;
    this.paymentMetrics = paymentMetrics;
    this.paymentProperties = paymentProperties;
    this.transactionTemplate = new TransactionTemplate(platformTransactionManager);
    this.auditLogService = auditLogService;
    this.webhookEventService = webhookEventService;
    this.paymentRetryEligibilityService = paymentRetryEligibilityService;
  }

  public PaymentAttemptResponse create(
      UUID organizationId, UUID invoiceId, String principalMode, String testCode) {
    return create(organizationId, invoiceId, principalMode, testCode, null, 0, null, () -> {});
  }

  public PaymentAttemptResponse retry(
      UUID organizationId,
      UUID invoiceId,
      String mode,
      UUID failedAttemptId,
      long attemptCount,
      AuditActor actor,
      Runnable authorizationCheck) {
    return create(
        organizationId,
        invoiceId,
        mode,
        null,
        failedAttemptId,
        attemptCount,
        actor,
        authorizationCheck);
  }

  private PaymentAttemptResponse create(
      UUID organizationId,
      UUID invoiceId,
      String principalMode,
      String testCode,
      UUID failedAttemptId,
      long attemptCount,
      AuditActor actor,
      Runnable authorizationCheck) {
    PaymentCreation creation =
        transactionTemplate.execute(
            status -> {
              lockInvoice(organizationId, invoiceId)
                  .orElseThrow(() -> new NotFoundException(ErrorCode.INVOICE_NOT_FOUND));
              authorizationCheck.run();
              PaymentCreation checked = prepare(organizationId, invoiceId, principalMode, testCode);
              if (failedAttemptId != null)
                requireRetry(
                    organizationId, invoiceId, checked.mode(), failedAttemptId, attemptCount);
              return checked;
            });
    for (PaymentAttempt attempt : creation.uncertainAttempts()) {
      if (!isConfirmedUncharged(creation, attempt)) {
        throw new ConflictException(ErrorCode.PAYMENT_RESULT_UNCONFIRMED);
      }
    }
    PaymentAttempt pendingAttempt =
        transactionTemplate.execute(
            status -> {
              return insertPending(
                  creation,
                  principalMode,
                  testCode,
                  failedAttemptId,
                  attemptCount,
                  actor,
                  authorizationCheck);
            });
    return approve(pendingAttempt, testCode);
  }

  @Transactional(readOnly = true)
  public ListPaymentAttemptsResponse list(UUID organizationId, UUID invoiceId) {
    findInvoice(organizationId, invoiceId);
    return ListPaymentAttemptsResponse.from(
        paymentAttemptRepository.findByOrganizationIdAndInvoiceIdOrderByRequestedAtDesc(
            organizationId, invoiceId));
  }

  PaymentAttemptResponse approve(PaymentAttempt pendingAttempt, String testCode) {
    return PaymentEnvironments.call(
        pendingAttempt.getOrganizationId(),
        pendingAttempt.getEnvironmentId(),
        pendingAttempt.getMode(),
        () -> approveScoped(pendingAttempt, testCode));
  }

  private PaymentAttemptResponse approveScoped(PaymentAttempt pendingAttempt, String testCode) {
    UUID organizationId = pendingAttempt.getOrganizationId();
    UUID attemptId = pendingAttempt.getId();
    String mode = pendingAttempt.getMode();
    Optional<TossPaymentsCredential> credential =
        pendingAttempt.getCredentialUpdatedAt() != null
            ? tossPaymentsCredentialService.historical(
                organizationId, mode, pendingAttempt.getCredentialUpdatedAt())
            : pendingAttempt.getEnvironmentId() == null
                ? tossPaymentsCredentialService.active(organizationId, mode)
                : tossPaymentsCredentialService.historical(
                    organizationId, mode, pendingAttempt.getRequestedAt());
    if (credential.isEmpty()) {
      return failWithoutToss(pendingAttempt, ErrorCode.TOSS_PAYMENTS_CREDENTIAL_MISSING);
    }
    Optional<BillingKey> billingKey =
        findBillingKey(organizationId, pendingAttempt.getCustomerId(), mode);
    if (billingKey.isEmpty() && pendingAttempt.getBillingKeySnapshot() == null) {
      return failWithoutToss(pendingAttempt, ErrorCode.PAYMENT_METHOD_MISSING);
    }
    TossPaymentsResult<TossPaymentsPayment> result =
        tossPaymentsClient.approveBilling(
            credential.get().getSecretKey(),
            pendingAttempt.getBillingKeySnapshot() == null
                ? billingKey.orElseThrow().getBillingKey()
                : pendingAttempt.getBillingKeySnapshot(),
            new TossPaymentsApproveBillingRequest(
                pendingAttempt.getCustomerId().toString(),
                pendingAttempt.getAmount(),
                attemptId.toString(),
                pendingAttempt.getOrderName()),
            attemptId.toString(),
            testCode);
    switch (result) {
      case TossPaymentsResult.Success<TossPaymentsPayment> approved when isDone(approved.value()) ->
          markApproved(pendingAttempt, approved.value().paymentKey());
      case TossPaymentsResult.Success<TossPaymentsPayment> notDone -> {
        logger.warn(
            "toss payments billing approval returned a payment that is not done: attempt_id={},"
                + " mode={}, status={}, payment_key_present={}",
            attemptId,
            mode,
            new TossPaymentsError(null, notDone.value().status()).singleLineMessage(),
            notDone.value().paymentKey() != null);
        paymentMetrics.countAttempt(mode, PaymentMetrics.UNKNOWN);
      }
      case TossPaymentsResult.Rejected<TossPaymentsPayment> rejected -> {
        logger.warn(
            "toss payments rejected billing approval: attempt_id={}, mode={}, status={}, code={},"
                + " message={}",
            attemptId,
            mode,
            rejected.status(),
            rejected.code(),
            new TossPaymentsError(rejected.code(), rejected.message()).singleLineMessage());
        markFailed(pendingAttempt, TossPaymentsFailureCode.of(rejected), rejected.message());
      }
      case TossPaymentsResult.Unknown<TossPaymentsPayment> unknown -> {
        logger.warn(
            "toss payments billing approval result unknown: attempt_id={}, mode={}, reason={}",
            attemptId,
            mode,
            unknown.reason());
        paymentMetrics.countAttempt(mode, PaymentMetrics.UNKNOWN);
      }
    }
    return find(organizationId, attemptId);
  }

  // 토스가 조회해 준 결제로 pending 시도를 끝내고, 이번에 바꿨으면 true다.
  boolean settle(PaymentAttempt attempt, TossPaymentsPayment payment) {
    if (isDone(payment)) {
      return markApproved(attempt, payment.paymentKey());
    }
    if (isEndedWithoutApproval(payment)) {
      return markFailed(attempt, payment.status(), ENDED_WITHOUT_APPROVAL_MESSAGE);
    }
    return false;
  }

  boolean markDone(PaymentAttempt attempt, String paymentKey) {
    UUID attemptId = attempt.getId();
    String mode = attempt.getMode();
    boolean applied =
        Boolean.TRUE.equals(
            transactionTemplate.execute(
                status -> {
                  if (paymentAttemptRepository.markDone(attemptId, paymentKey, now()) != 1) {
                    return false;
                  }
                  publish(attempt, WebhookEventType.PAYMENT_SUCCEEDED);
                  return true;
                }));
    if (applied) {
      paymentMetrics.countAttempt(mode, PaymentAttempt.DONE);
    }
    return applied;
  }

  boolean markFailed(PaymentAttempt attempt, String failureCode, String failureMessage) {
    UUID attemptId = attempt.getId();
    UUID organizationId = attempt.getOrganizationId();
    UUID invoiceId = attempt.getInvoiceId();
    String mode = attempt.getMode();
    Optional<Long> attemptCount =
        transactionTemplate.execute(
            status -> {
              if (paymentAttemptRepository.markFailed(attemptId, failureCode, failureMessage, now())
                  != 1) {
                return Optional.empty();
              }
              publish(attempt, WebhookEventType.PAYMENT_FAILED);
              return Optional.of(
                  paymentAttemptRepository.countByOrganizationIdAndInvoiceIdAndMode(
                      organizationId, invoiceId, mode));
            });
    if (attemptCount.isEmpty()) {
      return false;
    }
    paymentMetrics.countAttempt(mode, PaymentAttempt.FAILED);
    if (attemptCount.get() == paymentProperties.retryMaxAttempts()) {
      paymentMetrics.countRetryExhausted(mode);
    }
    return true;
  }

  PaymentAttempt savePending(
      UUID organizationId,
      UUID customerId,
      UUID invoiceId,
      String mode,
      long amount,
      String orderName) {
    return savePending(organizationId, customerId, invoiceId, mode, amount, orderName, null);
  }

  private PaymentAttempt savePending(
      UUID organizationId,
      UUID customerId,
      UUID invoiceId,
      String mode,
      long amount,
      String orderName,
      AuditActor actor) {
    PaymentAttempt attempt =
        PaymentAttempt.pending(
            UUID.randomUUID(), organizationId, customerId, invoiceId, mode, amount, orderName);
    findBillingKey(organizationId, customerId, mode)
        .ifPresent(
            key -> {
              attempt.pinBillingKey(key.getBillingKey());
              if (key.getCredentialUpdatedAt() != null)
                attempt.pinCredential(key.getCredentialUpdatedAt());
            });
    if (attempt.getCredentialUpdatedAt() == null) {
      tossPaymentsCredentialService
          .active(organizationId, mode)
          .ifPresent(credential -> attempt.pinCredential(credential.getUpdatedAt()));
    }
    paymentAttemptRepository.saveAndFlush(attempt);
    Map<String, Object> detail =
        Map.of("invoice_id", invoiceId, "customer_id", customerId, "mode", mode, "amount", amount);
    if (actor == null) {
      auditLogService.record(
          organizationId,
          "payment_attempt_created",
          "payment_attempt",
          attempt.getId().toString(),
          detail);
    } else {
      auditLogService.record(
          actor, "payment_attempt_created", "payment_attempt", attempt.getId().toString(), detail);
      auditLogService.record(
          actor,
          "payment_retry_requested",
          "invoice",
          invoiceId.toString(),
          Map.of("mode", mode, "amount", amount));
    }
    return attempt;
  }

  boolean isUncharged(UUID attemptId, Instant requestedAt, TossPaymentsCredential credential) {
    String orderId = attemptId.toString();
    return switch (tossPaymentsClient.findPaymentByOrderId(credential.getSecretKey(), orderId)) {
      case TossPaymentsResult.Success<TossPaymentsPayment> found
          when orderId.equals(found.value().orderId()) && isEndedWithoutApproval(found.value()) ->
          true;
      case TossPaymentsResult.Success<TossPaymentsPayment> found -> {
        logger.warn(
            "toss payments order lookup found a payment for a failed attempt, skipping retry:"
                + " failed_attempt_id={}, organization_id={}, mode={}, status={},"
                + " order_matches={}",
            attemptId,
            credential.getOrganizationId(),
            credential.getMode(),
            new TossPaymentsError(null, found.value().status()).singleLineMessage(),
            orderId.equals(found.value().orderId()));
        yield false;
      }
      case TossPaymentsResult.Rejected<TossPaymentsPayment> notFound
          when TossPaymentsFailureCode.isNotFoundPayment(notFound)
              && credential.getUpdatedAt().toInstant().isBefore(requestedAt) ->
          true;
      case TossPaymentsResult.Rejected<TossPaymentsPayment> notFound
          when TossPaymentsFailureCode.isNotFoundPayment(notFound) -> {
        logger.warn(
            "toss payments credential changed after the failed attempt, skipping retry:"
                + " failed_attempt_id={}, mode={}",
            attemptId,
            credential.getMode());
        yield false;
      }
      case TossPaymentsResult.Rejected<TossPaymentsPayment> rejected -> {
        logger.warn(
            "toss payments order lookup rejected before retry, skipping retry:"
                + " failed_attempt_id={}, mode={}, status={}, code={}",
            attemptId,
            credential.getMode(),
            rejected.status(),
            rejected.code());
        yield false;
      }
      case TossPaymentsResult.Unknown<TossPaymentsPayment> unknown -> {
        logger.warn(
            "toss payments order lookup result unknown before retry, skipping retry:"
                + " failed_attempt_id={}, mode={}, reason={}",
            attemptId,
            credential.getMode(),
            unknown.reason());
        yield false;
      }
    };
  }

  private void publish(PaymentAttempt attempt, WebhookEventType type) {
    webhookEventService.publish(
        attempt.getOrganizationId(),
        type,
        Map.of(
            "payment_attempt_id",
            attempt.getId(),
            "invoice_id",
            attempt.getInvoiceId(),
            "customer_id",
            attempt.getCustomerId(),
            "amount",
            attempt.getAmount(),
            "mode",
            attempt.getMode()));
  }

  private boolean markApproved(PaymentAttempt attempt, String paymentKey) {
    if (markDone(attempt, paymentKey)) {
      return true;
    }
    paymentAttemptRepository
        .findById(attempt.getId())
        .filter(current -> PaymentAttempt.FAILED.equals(current.getStatus()))
        .ifPresent(
            failed ->
                logger.error(
                    "toss payments approved a payment attempt already marked failed:"
                        + " attempt_id={}, organization_id={}, mode={}, failure_code={}",
                    failed.getId(),
                    failed.getOrganizationId(),
                    failed.getMode(),
                    failed.getFailureCode()));
    return false;
  }

  // 더 새 시도가 있는 실패는 그 시도를 만들 때 미청구로 확인했으므로 7일 창을 걸지 않고, 그 시도의 키로 물을 수 없으면 다시 묻지 않는다.
  private boolean isConfirmedUncharged(PaymentCreation creation, PaymentAttempt attempt) {
    Optional<TossPaymentsCredential> original =
        attempt.getEnvironmentId() == null && attempt.getCredentialUpdatedAt() == null
            ? Optional.of(creation.credential())
            : tossPaymentsCredentialService.historical(
                attempt.getOrganizationId(),
                attempt.getMode(),
                attempt.getCredentialUpdatedAt() == null
                    ? attempt.getRequestedAt()
                    : attempt.getCredentialUpdatedAt());
    if (!attempt.getId().equals(creation.latestAttemptId())) {
      return original.isEmpty()
          || !original.get().getUpdatedAt().isBefore(attempt.getRequestedAt())
          || isUncharged(attempt.getId(), attempt.getRequestedAt().toInstant(), original.get());
    }
    return original.isPresent()
        && !PaymentRetryPolicy.isUnconfirmable(attempt, original.get().getUpdatedAt(), now())
        && isUncharged(attempt.getId(), attempt.getRequestedAt().toInstant(), original.get());
  }

  private PaymentCreation prepare(
      UUID organizationId, UUID invoiceId, String principalMode, String testCode) {
    Invoice invoice =
        lockInvoice(organizationId, invoiceId)
            .orElseThrow(() -> new NotFoundException(ErrorCode.INVOICE_NOT_FOUND));
    if (invoice.getTotalAmount() <= 0) {
      throw new ConflictException(ErrorCode.INVOICE_AMOUNT_ZERO);
    }
    String mode = paymentSettingService.findMode(organizationId);
    if (principalMode != null && !principalMode.equals(mode)) {
      throw new ConflictException(ErrorCode.PAYMENT_MODE_MISMATCH);
    }
    if (testCode != null && PaymentSetting.LIVE.equals(mode)) {
      throw new InvalidRequestException(ErrorCode.TOSS_PAYMENTS_TEST_CODE_IN_LIVE_MODE);
    }
    TossPaymentsCredential credential =
        tossPaymentsCredentialService
            .active(organizationId, mode)
            .orElseThrow(() -> new ConflictException(ErrorCode.TOSS_PAYMENTS_CREDENTIAL_MISSING));
    if (findBillingKey(organizationId, invoice.getCustomerId(), mode).isEmpty()) {
      throw new ConflictException(ErrorCode.PAYMENT_METHOD_MISSING);
    }
    if (paymentAttemptRepository.findActive(organizationId, invoiceId, mode).isPresent()) {
      throw new ConflictException(ErrorCode.INVOICE_ALREADY_PAID);
    }
    List<PaymentAttempt> attempts =
        paymentAttemptRepository
            .findByOrganizationIdAndInvoiceIdOrderByRequestedAtDesc(organizationId, invoiceId)
            .stream()
            .filter(attempt -> mode.equals(attempt.getMode()))
            .toList();
    List<PaymentAttempt> uncertainAttempts =
        attempts.stream()
            .filter(attempt -> PaymentAttempt.FAILED.equals(attempt.getStatus()))
            .filter(
                attempt ->
                    TossPaymentsFailureCode.NOT_FOUND_PAYMENT.equals(attempt.getFailureCode()))
            .toList();
    UUID latestAttemptId = attempts.isEmpty() ? null : attempts.getFirst().getId();
    return new PaymentCreation(
        invoice,
        mode,
        credential,
        uncertainAttempts,
        paymentAttemptRepository.countByOrganizationIdAndInvoiceIdAndMode(
            organizationId, invoiceId, mode),
        latestAttemptId);
  }

  private PaymentAttempt insertPending(
      PaymentCreation checked,
      String principalMode,
      String testCode,
      UUID failedAttemptId,
      long attemptCount,
      AuditActor actor,
      Runnable authorizationCheck) {
    UUID organizationId = checked.invoice().getOrganizationId();
    UUID invoiceId = checked.invoice().getId();
    lockInvoice(organizationId, invoiceId).orElseThrow();
    for (UUID attemptId : checked.uncertainAttemptIds().stream().sorted().toList()) {
      paymentAttemptRepository.findForUpdate(organizationId, attemptId).orElseThrow();
    }
    authorizationCheck.run();
    PaymentCreation current = prepare(organizationId, invoiceId, principalMode, testCode);
    if (!current.mode().equals(checked.mode())) {
      throw new ConflictException(ErrorCode.PAYMENT_MODE_MISMATCH);
    }
    if (!current.uncertainAttemptIds().equals(checked.uncertainAttemptIds())
        || (!checked.uncertainAttempts().isEmpty()
            && !current
                .credential()
                .getUpdatedAt()
                .toInstant()
                .equals(checked.credential().getUpdatedAt().toInstant()))) {
      throw new ConflictException(ErrorCode.PAYMENT_RESULT_UNCONFIRMED);
    }
    if (current.attemptCount() != checked.attemptCount()) {
      throw new ConflictException(ErrorCode.PAYMENT_RETRY_STALE);
    }
    if (failedAttemptId != null)
      requireRetry(organizationId, invoiceId, current.mode(), failedAttemptId, attemptCount);
    authorizationCheck.run();
    Invoice invoice = current.invoice();
    return savePending(
        organizationId,
        invoice.getCustomerId(),
        invoiceId,
        current.mode(),
        invoice.getTotalAmount(),
        invoice.getPeriod() + ORDER_NAME_SUFFIX,
        actor);
  }

  private record PaymentCreation(
      Invoice invoice,
      String mode,
      TossPaymentsCredential credential,
      List<PaymentAttempt> uncertainAttempts,
      long attemptCount,
      UUID latestAttemptId) {

    private Set<UUID> uncertainAttemptIds() {
      return Set.copyOf(uncertainAttempts.stream().map(PaymentAttempt::getId).toList());
    }
  }

  private void requireRetry(
      UUID organizationId, UUID invoiceId, String mode, UUID failedAttemptId, long attemptCount) {
    PaymentRetryEligibility eligibility =
        paymentRetryEligibilityService.get(organizationId, invoiceId, mode);
    if (!eligibility.allowed()) throw new ConflictException(ErrorCode.PAYMENT_RETRY_UNAVAILABLE);
    if (!failedAttemptId.equals(eligibility.failedAttemptId())
        || attemptCount != eligibility.attemptCount())
      throw new ConflictException(ErrorCode.PAYMENT_RETRY_STALE);
  }

  private Invoice findInvoice(UUID organizationId, UUID invoiceId) {
    return (BusinessEnvironmentContext.environmentId() == null
            ? invoiceRepository.findByOrganizationIdAndId(organizationId, invoiceId)
            : invoiceRepository.findScoped(
                organizationId, BusinessEnvironmentContext.environmentId(), invoiceId))
        .orElseThrow(() -> new NotFoundException(ErrorCode.INVOICE_NOT_FOUND));
  }

  private Optional<Invoice> lockInvoice(UUID organizationId, UUID invoiceId) {
    return BusinessEnvironmentContext.environmentId() == null
        ? invoiceRepository.findForUpdate(organizationId, invoiceId)
        : invoiceRepository.lockScoped(
            organizationId, BusinessEnvironmentContext.environmentId(), invoiceId);
  }

  private Optional<BillingKey> findBillingKey(UUID organizationId, UUID customerId, String mode) {
    return billingKeyRepository
        .findById(new BillingKeyId(organizationId, customerId, mode))
        .filter(key -> BusinessEnvironmentContext.matches(key.getEnvironmentId()))
        .filter(key -> key.getArchivedAt() == null);
  }

  private PaymentAttemptResponse failWithoutToss(PaymentAttempt pendingAttempt, ErrorCode reason) {
    UUID organizationId = pendingAttempt.getOrganizationId();
    UUID attemptId = pendingAttempt.getId();
    logger.warn(
        "billing approval failed before calling toss payments: attempt_id={}, mode={}, reason={}",
        attemptId,
        pendingAttempt.getMode(),
        reason.getCode());
    markFailed(pendingAttempt, reason.getCode(), reason.getMessage());
    return find(organizationId, attemptId);
  }

  private PaymentAttemptResponse find(UUID organizationId, UUID attemptId) {
    return PaymentAttemptResponse.from(
        paymentAttemptRepository
            .findByOrganizationIdAndId(organizationId, attemptId)
            .orElseThrow());
  }

  static boolean isEndedWithoutApproval(TossPaymentsPayment payment) {
    return TOSS_PAYMENT_ENDED_WITHOUT_APPROVAL.contains(payment.status());
  }

  private static boolean isDone(TossPaymentsPayment payment) {
    return TOSS_PAYMENT_DONE.equals(payment.status()) && payment.paymentKey() != null;
  }

  private static OffsetDateTime now() {
    return OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
  }
}
