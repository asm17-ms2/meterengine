package com.meterengine.payment.service;

import com.meterengine.audit.service.AuditLogService;
import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.payment.client.TossPaymentsCancelRequest;
import com.meterengine.payment.client.TossPaymentsClient;
import com.meterengine.payment.client.TossPaymentsError;
import com.meterengine.payment.client.TossPaymentsPayment;
import com.meterengine.payment.client.TossPaymentsResult;
import com.meterengine.payment.dto.CreatePaymentCancellationRequest;
import com.meterengine.payment.dto.ListPaymentCancellationsResponse;
import com.meterengine.payment.dto.PaymentCancellationResponse;
import com.meterengine.payment.entity.PaymentAttempt;
import com.meterengine.payment.entity.PaymentCancellation;
import com.meterengine.payment.entity.TossPaymentsCredential;
import com.meterengine.payment.repository.PaymentAttemptRepository;
import com.meterengine.payment.repository.PaymentCancellationRepository;
import com.meterengine.webhook.entity.WebhookEventType;
import com.meterengine.webhook.service.WebhookEventService;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class PaymentCancellationService {

  private static final Logger logger = LoggerFactory.getLogger(PaymentCancellationService.class);
  private static final String TOSS_CANCEL_DONE = "DONE";
  private static final Duration CANCELED_AT_TOLERANCE = Duration.ofMinutes(1);

  private final PaymentAttemptRepository paymentAttemptRepository;
  private final PaymentCancellationRepository paymentCancellationRepository;
  private final TossPaymentsCredentialService tossPaymentsCredentialService;
  private final TossPaymentsClient tossPaymentsClient;
  private final PaymentMetrics paymentMetrics;
  private final TransactionTemplate transactionTemplate;
  private final AuditLogService auditLogService;
  private final WebhookEventService webhookEventService;
  private final JdbcTemplate jdbcTemplate;

  PaymentCancellationService(
      PaymentAttemptRepository paymentAttemptRepository,
      PaymentCancellationRepository paymentCancellationRepository,
      TossPaymentsCredentialService tossPaymentsCredentialService,
      TossPaymentsClient tossPaymentsClient,
      PaymentMetrics paymentMetrics,
      PlatformTransactionManager platformTransactionManager,
      AuditLogService auditLogService,
      WebhookEventService webhookEventService,
      JdbcTemplate jdbcTemplate) {
    this.paymentAttemptRepository = paymentAttemptRepository;
    this.paymentCancellationRepository = paymentCancellationRepository;
    this.tossPaymentsCredentialService = tossPaymentsCredentialService;
    this.tossPaymentsClient = tossPaymentsClient;
    this.paymentMetrics = paymentMetrics;
    this.transactionTemplate = new TransactionTemplate(platformTransactionManager);
    this.auditLogService = auditLogService;
    this.webhookEventService = webhookEventService;
    this.jdbcTemplate = jdbcTemplate;
  }

  public PaymentCancellationResponse create(
      UUID organizationId, UUID paymentAttemptId, CreatePaymentCancellationRequest request) {
    return create(organizationId, paymentAttemptId, request, null);
  }

  public PaymentCancellationResponse create(
      UUID organizationId,
      UUID paymentAttemptId,
      CreatePaymentCancellationRequest request,
      UUID idempotencyKey) {
    return create(organizationId, paymentAttemptId, request, idempotencyKey, null);
  }

  public PaymentCancellationResponse create(
      UUID organizationId,
      UUID paymentAttemptId,
      CreatePaymentCancellationRequest request,
      UUID idempotencyKey,
      String principalMode) {
    PendingCancellation pending =
        transactionTemplate.execute(
            status ->
                insertPending(
                    organizationId, paymentAttemptId, request, idempotencyKey, principalMode));
    return pending.created()
        ? cancel(pending)
        : PaymentCancellationResponse.from(pending.cancellation());
  }

  @Transactional(readOnly = true)
  public ListPaymentCancellationsResponse list(UUID organizationId, UUID paymentAttemptId) {
    paymentAttemptRepository
        .findByOrganizationIdAndId(organizationId, paymentAttemptId)
        .orElseThrow(() -> new NotFoundException(ErrorCode.PAYMENT_ATTEMPT_NOT_FOUND));
    return ListPaymentCancellationsResponse.from(
        paymentCancellationRepository.findByPaymentAttemptIdOrderByRequestedAtDesc(
            paymentAttemptId));
  }

  boolean markDone(PaymentCancellation cancellation, String transactionKey) {
    UUID cancellationId = cancellation.getId();
    boolean applied =
        Boolean.TRUE.equals(
            transactionTemplate.execute(
                status -> {
                  if (paymentCancellationRepository.markDone(cancellationId, transactionKey, now())
                      != 1) {
                    return false;
                  }
                  PaymentAttempt attempt =
                      paymentAttemptRepository
                          .findById(cancellation.getPaymentAttemptId())
                          .orElseThrow();
                  webhookEventService.publish(
                      cancellation.getOrganizationId(),
                      WebhookEventType.PAYMENT_CANCELED,
                      Map.of(
                          "cancellation_id",
                          cancellationId,
                          "payment_attempt_id",
                          attempt.getId(),
                          "invoice_id",
                          attempt.getInvoiceId(),
                          "customer_id",
                          attempt.getCustomerId(),
                          "amount",
                          cancellation.getAmount(),
                          "mode",
                          cancellation.getMode()));
                  return true;
                }));
    if (applied) {
      paymentMetrics.countCancellation(cancellation.getMode(), PaymentCancellation.DONE);
    }
    return applied;
  }

  boolean markFailed(PaymentCancellation cancellation, String failureCode, String failureMessage) {
    UUID cancellationId = cancellation.getId();
    boolean applied =
        Boolean.TRUE.equals(
            transactionTemplate.execute(
                status ->
                    paymentCancellationRepository.markFailed(
                            cancellationId, failureCode, failureMessage, now())
                        == 1));
    if (applied) {
      paymentMetrics.countCancellation(cancellation.getMode(), PaymentCancellation.FAILED);
    }
    return applied;
  }

  Optional<String> matchTransactionKey(PaymentCancellation pending, TossPaymentsPayment payment) {
    Set<String> doneTransactionKeys =
        paymentCancellationRepository
            .findByPaymentAttemptIdOrderByRequestedAtDesc(pending.getPaymentAttemptId())
            .stream()
            .filter(cancellation -> PaymentCancellation.DONE.equals(cancellation.getStatus()))
            .map(PaymentCancellation::getTransactionKey)
            .collect(Collectors.toSet());
    OffsetDateTime earliestCanceledAt = pending.getRequestedAt().minus(CANCELED_AT_TOLERANCE);
    return payment.cancels().reversed().stream()
        .filter(cancel -> cancel.transactionKey() != null)
        .filter(cancel -> !doneTransactionKeys.contains(cancel.transactionKey()))
        .filter(cancel -> TOSS_CANCEL_DONE.equals(cancel.cancelStatus()))
        .filter(cancel -> cancel.cancelAmount() == pending.getAmount())
        .filter(
            cancel ->
                cancel.canceledAt() != null && !cancel.canceledAt().isBefore(earliestCanceledAt))
        .map(TossPaymentsPayment.Cancel::transactionKey)
        .findFirst();
  }

  private PendingCancellation insertPending(
      UUID organizationId,
      UUID paymentAttemptId,
      CreatePaymentCancellationRequest request,
      UUID idempotencyKey,
      String principalMode) {
    UUID cancellationId =
        idempotencyKey == null ? UUID.randomUUID() : cancellationId(organizationId, idempotencyKey);
    String fingerprint = idempotencyKey == null ? null : fingerprint(paymentAttemptId, request);
    if (idempotencyKey != null) {
      jdbcTemplate.queryForList(
          "SELECT pg_advisory_xact_lock(?)", cancellationId.getMostSignificantBits());
      Optional<PaymentCancellation> existing =
          paymentCancellationRepository.findById(cancellationId);
      if (existing.isPresent()) {
        PaymentCancellation cancellation = existing.get();
        if (!organizationId.equals(cancellation.getOrganizationId())
            || !fingerprint.equals(cancellation.getIdempotencyFingerprint())) {
          throw new ConflictException(ErrorCode.PAYMENT_CANCELLATION_IDEMPOTENCY_CONFLICT);
        }
        validateMode(principalMode, cancellation.getMode());
        return new PendingCancellation(cancellation, null, null, false);
      }
    }
    PaymentAttempt attempt =
        paymentAttemptRepository
            .findForUpdate(organizationId, paymentAttemptId)
            .orElseThrow(() -> new NotFoundException(ErrorCode.PAYMENT_ATTEMPT_NOT_FOUND));
    validateMode(principalMode, attempt.getMode());
    if (!PaymentAttempt.DONE.equals(attempt.getStatus())) {
      throw new ConflictException(ErrorCode.PAYMENT_ATTEMPT_NOT_CANCELABLE);
    }
    if (paymentCancellationRepository.existsByPaymentAttemptIdAndStatus(
        paymentAttemptId, PaymentCancellation.PENDING)) {
      throw new ConflictException(ErrorCode.PAYMENT_CANCELLATION_IN_PROGRESS);
    }
    long remainingAmount =
        attempt.getAmount() - paymentCancellationRepository.sumDoneAmount(paymentAttemptId);
    if (remainingAmount <= 0) {
      throw new ConflictException(ErrorCode.PAYMENT_ATTEMPT_FULLY_CANCELED);
    }
    long cancelAmount = request.amount() == null ? remainingAmount : request.amount();
    if (cancelAmount > remainingAmount) {
      throw new ConflictException(ErrorCode.PAYMENT_CANCELLATION_AMOUNT_EXCEEDED);
    }
    PaymentCancellation cancellation =
        paymentCancellationRepository.saveAndFlush(
            PaymentCancellation.pending(
                cancellationId,
                organizationId,
                paymentAttemptId,
                attempt.getMode(),
                cancelAmount,
                request.reason(),
                fingerprint));
    auditLogService.record(
        organizationId,
        "payment_cancellation_created",
        "payment_cancellation",
        cancellation.getId().toString(),
        Map.of(
            "payment_attempt_id",
            paymentAttemptId,
            "amount",
            cancelAmount,
            "mode",
            attempt.getMode()));
    return new PendingCancellation(
        cancellation,
        attempt.getPaymentKey(),
        new TossPaymentsCancelRequest(request.reason(), cancelAmount),
        true);
  }

  private static void validateMode(String principalMode, String paymentMode) {
    if (principalMode != null && !principalMode.equals(paymentMode)) {
      throw new ConflictException(ErrorCode.PAYMENT_MODE_MISMATCH);
    }
  }

  private PaymentCancellationResponse cancel(PendingCancellation pending) {
    PaymentCancellation cancellation = pending.cancellation();
    UUID cancellationId = cancellation.getId();
    String mode = cancellation.getMode();
    Optional<TossPaymentsCredential> credential =
        tossPaymentsCredentialService.historical(
            cancellation.getOrganizationId(),
            mode,
            paymentAttemptRepository
                .findById(cancellation.getPaymentAttemptId())
                .orElseThrow()
                .getRequestedAt());
    if (credential.isEmpty()) {
      ErrorCode reason = ErrorCode.TOSS_PAYMENTS_CREDENTIAL_MISSING;
      logger.warn(
          "payment cancellation failed before calling toss payments: cancellation_id={}, mode={},"
              + " reason={}",
          cancellationId,
          mode,
          reason.getCode());
      markFailed(cancellation, reason.getCode(), reason.getMessage());
      return find(cancellationId);
    }
    TossPaymentsResult<TossPaymentsPayment> result =
        tossPaymentsClient.cancel(
            credential.get().getSecretKey(),
            pending.paymentKey(),
            pending.request(),
            cancellationId.toString());
    switch (result) {
      case TossPaymentsResult.Success<TossPaymentsPayment> canceled -> {
        Optional<String> transactionKey = matchTransactionKey(cancellation, canceled.value());
        if (transactionKey.isPresent()) {
          markDone(cancellation, transactionKey.get());
        } else if (paymentCancellationRepository.existsByPaymentAttemptIdAndStatus(
            cancellation.getPaymentAttemptId(), PaymentCancellation.PENDING)) {
          logger.warn(
              "toss payments cancel returned no matching cancel transaction: cancellation_id={},"
                  + " mode={}, status={}",
              cancellationId,
              mode,
              new TossPaymentsError(null, canceled.value().status()).singleLineMessage());
          paymentMetrics.countCancellation(mode, PaymentMetrics.UNKNOWN);
        }
      }
      case TossPaymentsResult.Rejected<TossPaymentsPayment> rejected -> {
        logger.warn(
            "toss payments rejected cancel: cancellation_id={}, mode={}, status={}, code={},"
                + " message={}",
            cancellationId,
            mode,
            rejected.status(),
            rejected.code(),
            new TossPaymentsError(rejected.code(), rejected.message()).singleLineMessage());
        markFailed(cancellation, TossPaymentsFailureCode.of(rejected), rejected.message());
      }
      case TossPaymentsResult.Unknown<TossPaymentsPayment> unknown -> {
        logger.warn(
            "toss payments cancel result unknown: cancellation_id={}, mode={}, reason={}",
            cancellationId,
            mode,
            unknown.reason());
        paymentMetrics.countCancellation(mode, PaymentMetrics.UNKNOWN);
      }
    }
    return find(cancellationId);
  }

  private PaymentCancellationResponse find(UUID cancellationId) {
    return PaymentCancellationResponse.from(
        paymentCancellationRepository.findById(cancellationId).orElseThrow());
  }

  private static OffsetDateTime now() {
    return OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
  }

  private static UUID cancellationId(UUID organizationId, UUID idempotencyKey) {
    byte[] value = sha256((organizationId + ":" + idempotencyKey).getBytes(StandardCharsets.UTF_8));
    ByteBuffer bytes = ByteBuffer.wrap(value);
    return new UUID(bytes.getLong(), bytes.getLong());
  }

  private static String fingerprint(
      UUID paymentAttemptId, CreatePaymentCancellationRequest request) {
    String value = paymentAttemptId + "\n" + request.amount() + "\n" + request.reason();
    return HexFormat.of().formatHex(sha256(value.getBytes(StandardCharsets.UTF_8)));
  }

  private static byte[] sha256(byte[] value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private record PendingCancellation(
      PaymentCancellation cancellation,
      String paymentKey,
      TossPaymentsCancelRequest request,
      boolean created) {}
}
