package com.meterengine.payment.service;

import com.meterengine.payment.client.TossPaymentsClient;
import com.meterengine.payment.client.TossPaymentsError;
import com.meterengine.payment.client.TossPaymentsPayment;
import com.meterengine.payment.client.TossPaymentsResult;
import com.meterengine.payment.config.PaymentProperties;
import com.meterengine.payment.dto.PaymentJobResult;
import com.meterengine.payment.entity.PaymentAttempt;
import com.meterengine.payment.entity.PaymentCancellation;
import com.meterengine.payment.entity.TossPaymentsCredential;
import com.meterengine.payment.repository.PaymentAttemptRepository;
import com.meterengine.payment.repository.PaymentCancellationRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentAttemptConfirmationService {

  private static final Logger logger =
      LoggerFactory.getLogger(PaymentAttemptConfirmationService.class);
  // 이보다 오래된 pending은 토스에 다시 묻지 않는다. stale 게이지는 계속 센다.
  static final Duration CONFIRMATION_WINDOW = Duration.ofDays(7);
  private static final String NOT_FOUND_PAYMENT_MESSAGE = "토스페이먼츠에 결제 기록이 없습니다";
  private static final String NOT_FOUND_CANCELLATION = "NOT_FOUND_CANCELLATION";
  private static final String NOT_FOUND_CANCELLATION_MESSAGE = "토스페이먼츠에 취소 기록이 없습니다";

  private final PaymentAttemptRepository paymentAttemptRepository;
  private final PaymentCancellationRepository paymentCancellationRepository;
  private final TossPaymentsCredentialService tossPaymentsCredentialService;
  private final PaymentAttemptService paymentAttemptService;
  private final PaymentCancellationService paymentCancellationService;
  private final TossPaymentsClient tossPaymentsClient;
  private final PaymentMetrics paymentMetrics;
  private final PaymentProperties paymentProperties;

  PaymentAttemptConfirmationService(
      PaymentAttemptRepository paymentAttemptRepository,
      PaymentCancellationRepository paymentCancellationRepository,
      TossPaymentsCredentialService tossPaymentsCredentialService,
      PaymentAttemptService paymentAttemptService,
      PaymentCancellationService paymentCancellationService,
      TossPaymentsClient tossPaymentsClient,
      PaymentMetrics paymentMetrics,
      PaymentProperties paymentProperties) {
    this.paymentAttemptRepository = paymentAttemptRepository;
    this.paymentCancellationRepository = paymentCancellationRepository;
    this.tossPaymentsCredentialService = tossPaymentsCredentialService;
    this.paymentAttemptService = paymentAttemptService;
    this.paymentCancellationService = paymentCancellationService;
    this.tossPaymentsClient = tossPaymentsClient;
    this.paymentMetrics = paymentMetrics;
    this.paymentProperties = paymentProperties;
  }

  public PaymentJobResult confirm() {
    OffsetDateTime now = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
    OffsetDateTime confirmableFrom = now.minus(CONFIRMATION_WINDOW);
    OffsetDateTime confirmableBefore = now.minus(paymentProperties.confirmationDelay());
    OffsetDateTime notFoundFailableBefore = now.minus(paymentProperties.notFoundFailureDelay());
    measureStalePending(confirmableBefore);
    PaymentJobTally tally = new PaymentJobTally();
    for (PaymentAttempt attempt :
        paymentAttemptRepository.findPendingRequestedBetween(confirmableFrom, confirmableBefore)) {
      try {
        tally.count(
            PaymentEnvironments.call(
                attempt.getOrganizationId(),
                attempt.getEnvironmentId(),
                attempt.getMode(),
                () -> confirmAttempt(attempt, notFoundFailableBefore)));
      } catch (RuntimeException exception) {
        logger.warn(
            "payment attempt confirmation failed: attempt_id={}, mode={}",
            attempt.getId(),
            attempt.getMode(),
            exception);
        tally.countFailed();
      }
    }
    for (PaymentCancellation cancellation :
        paymentCancellationRepository.findPendingRequestedBetween(
            confirmableFrom, confirmableBefore)) {
      try {
        tally.count(
            PaymentEnvironments.call(
                cancellation.getOrganizationId(),
                cancellation.getEnvironmentId(),
                cancellation.getMode(),
                () -> confirmCancellation(cancellation, notFoundFailableBefore)));
      } catch (RuntimeException exception) {
        logger.warn(
            "payment cancellation confirmation failed: cancellation_id={}, mode={}",
            cancellation.getId(),
            cancellation.getMode(),
            exception);
        tally.countFailed();
      }
    }
    measureStalePending(confirmableBefore);
    return tally.result();
  }

  private void measureStalePending(OffsetDateTime confirmableBefore) {
    for (String mode : PaymentMetrics.MODES) {
      paymentMetrics.setStalePending(mode, countStalePending(mode, confirmableBefore));
      findOldestStalePendingRequestedAt(mode, confirmableBefore)
          .ifPresentOrElse(
              requestedAt -> paymentMetrics.setStalePendingOldestRequestedAt(mode, requestedAt),
              () -> paymentMetrics.clearStalePendingOldestRequestedAt(mode));
    }
  }

  private long countStalePending(String mode, OffsetDateTime confirmableBefore) {
    return paymentAttemptRepository.countPendingRequestedBefore(mode, confirmableBefore)
        + paymentCancellationRepository.countPendingRequestedBefore(mode, confirmableBefore);
  }

  private Optional<OffsetDateTime> findOldestStalePendingRequestedAt(
      String mode, OffsetDateTime confirmableBefore) {
    return Stream.of(
            paymentAttemptRepository.findOldestPendingRequestedAt(mode, confirmableBefore),
            paymentCancellationRepository.findOldestPendingRequestedAt(mode, confirmableBefore))
        .flatMap(Optional::stream)
        .min(Comparator.naturalOrder());
  }

  private boolean confirmAttempt(PaymentAttempt attempt, OffsetDateTime notFoundFailableBefore) {
    UUID attemptId = attempt.getId();
    Optional<TossPaymentsCredential> credential =
        tossPaymentsCredentialService.historical(
            attempt.getOrganizationId(),
            attempt.getMode(),
            attempt.getCredentialUpdatedAt() == null
                ? attempt.getRequestedAt()
                : attempt.getCredentialUpdatedAt());
    if (credential.isEmpty()) {
      logger.warn(
          "payment attempt confirmation skipped without toss payments credential: attempt_id={},"
              + " mode={}",
          attemptId,
          attempt.getMode());
      return false;
    }
    TossPaymentsResult<TossPaymentsPayment> result =
        tossPaymentsClient.findPaymentByOrderId(
            credential.get().getSecretKey(), attemptId.toString());
    return switch (result) {
      case TossPaymentsResult.Success<TossPaymentsPayment> found
          when attemptId.toString().equals(found.value().orderId()) ->
          paymentAttemptService.settle(attempt, found.value());
      case TossPaymentsResult.Success<TossPaymentsPayment> otherOrder -> {
        logger.warn(
            "toss payments order lookup returned another order: attempt_id={}, mode={}",
            attemptId,
            attempt.getMode());
        yield false;
      }
      case TossPaymentsResult.Rejected<TossPaymentsPayment> notFound
          when TossPaymentsFailureCode.isNotFoundPayment(notFound)
              && attempt.getRequestedAt().isBefore(notFoundFailableBefore) ->
          failNotFound(attempt, credential.get());
      case TossPaymentsResult.Rejected<TossPaymentsPayment> rejected -> {
        logger.info(
            "toss payments order lookup rejected during confirmation: attempt_id={}, mode={},"
                + " status={}, code={}, message={}",
            attemptId,
            attempt.getMode(),
            rejected.status(),
            rejected.code(),
            new TossPaymentsError(rejected.code(), rejected.message()).singleLineMessage());
        yield false;
      }
      case TossPaymentsResult.Unknown<TossPaymentsPayment> unknown -> {
        logger.warn(
            "toss payments order lookup result unknown during confirmation: attempt_id={},"
                + " mode={}, reason={}",
            attemptId,
            attempt.getMode(),
            unknown.reason());
        yield false;
      }
    };
  }

  private boolean failNotFound(PaymentAttempt attempt, TossPaymentsCredential credential) {
    if (!credential.getUpdatedAt().isBefore(attempt.getRequestedAt())) {
      logger.warn(
          "toss payments credential changed after the attempt, leaving it pending:"
              + " attempt_id={}, mode={}",
          attempt.getId(),
          attempt.getMode());
      return false;
    }
    return paymentAttemptService.markFailed(
        attempt, TossPaymentsFailureCode.NOT_FOUND_PAYMENT, NOT_FOUND_PAYMENT_MESSAGE);
  }

  private boolean confirmCancellation(
      PaymentCancellation cancellation, OffsetDateTime notFoundFailableBefore) {
    UUID cancellationId = cancellation.getId();
    PaymentAttempt originalAttempt =
        paymentAttemptRepository.findById(cancellation.getPaymentAttemptId()).orElseThrow();
    Optional<TossPaymentsCredential> credential =
        tossPaymentsCredentialService.historical(
            cancellation.getOrganizationId(),
            cancellation.getMode(),
            originalAttempt.getCredentialUpdatedAt() == null
                ? originalAttempt.getRequestedAt()
                : originalAttempt.getCredentialUpdatedAt());
    if (credential.isEmpty()) {
      logger.warn(
          "payment cancellation confirmation skipped without toss payments credential:"
              + " cancellation_id={}, mode={}",
          cancellationId,
          cancellation.getMode());
      return false;
    }
    PaymentAttempt attempt =
        paymentAttemptRepository
            .findByOrganizationIdAndId(
                cancellation.getOrganizationId(), cancellation.getPaymentAttemptId())
            .orElseThrow();
    TossPaymentsResult<TossPaymentsPayment> result =
        tossPaymentsClient.findPayment(credential.get().getSecretKey(), attempt.getPaymentKey());
    return switch (result) {
      case TossPaymentsResult.Success<TossPaymentsPayment> found
          when attempt.getId().toString().equals(found.value().orderId()) -> {
        Optional<String> transactionKey =
            paymentCancellationService.matchTransactionKey(cancellation, found.value());
        if (transactionKey.isPresent()) {
          yield paymentCancellationService.markDone(cancellation, transactionKey.get());
        }
        if (cancellation.getRequestedAt().isBefore(notFoundFailableBefore)) {
          yield failNotFound(cancellation, found.value());
        }
        yield false;
      }
      case TossPaymentsResult.Success<TossPaymentsPayment> otherOrder -> {
        logger.warn(
            "toss payments payment lookup returned another order during cancellation"
                + " confirmation: cancellation_id={}, mode={}",
            cancellationId,
            cancellation.getMode());
        yield false;
      }
      case TossPaymentsResult.Rejected<TossPaymentsPayment> rejected -> {
        logger.info(
            "toss payments payment lookup rejected during cancellation confirmation:"
                + " cancellation_id={}, mode={}, status={}, code={}, message={}",
            cancellationId,
            cancellation.getMode(),
            rejected.status(),
            rejected.code(),
            new TossPaymentsError(rejected.code(), rejected.message()).singleLineMessage());
        yield false;
      }
      case TossPaymentsResult.Unknown<TossPaymentsPayment> unknown -> {
        logger.warn(
            "toss payments payment lookup result unknown during cancellation confirmation:"
                + " cancellation_id={}, mode={}, reason={}",
            cancellationId,
            cancellation.getMode(),
            unknown.reason());
        yield false;
      }
    };
  }

  private boolean failNotFound(PaymentCancellation cancellation, TossPaymentsPayment payment) {
    long tossCanceledAmount = payment.totalAmount() - payment.balanceAmount();
    long doneCanceledAmount =
        paymentCancellationRepository.sumDoneAmount(cancellation.getPaymentAttemptId());
    if (tossCanceledAmount > doneCanceledAmount) {
      logger.warn(
          "toss payments canceled more than the done cancellations, leaving the cancellation"
              + " pending: cancellation_id={}, mode={}, toss_canceled_amount={},"
              + " done_canceled_amount={}",
          cancellation.getId(),
          cancellation.getMode(),
          tossCanceledAmount,
          doneCanceledAmount);
      return false;
    }
    return paymentCancellationService.markFailed(
        cancellation, NOT_FOUND_CANCELLATION, NOT_FOUND_CANCELLATION_MESSAGE);
  }
}
