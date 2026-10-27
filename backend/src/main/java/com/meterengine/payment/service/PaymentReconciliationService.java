package com.meterengine.payment.service;

import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ErrorResponse.FieldError;
import com.meterengine.global.error.InvalidRequestException;
import com.meterengine.global.security.BusinessEnvironmentContext;
import com.meterengine.metric.service.BillableMetricUsageService;
import com.meterengine.payment.client.TossPaymentsClient;
import com.meterengine.payment.client.TossPaymentsError;
import com.meterengine.payment.client.TossPaymentsPayment;
import com.meterengine.payment.client.TossPaymentsResult;
import com.meterengine.payment.client.TossPaymentsTransaction;
import com.meterengine.payment.config.PaymentProperties;
import com.meterengine.payment.dto.ListPaymentReconciliationMismatchesResponse;
import com.meterengine.payment.dto.PaymentJobResult;
import com.meterengine.payment.entity.PaymentAttempt;
import com.meterengine.payment.entity.PaymentCancellation;
import com.meterengine.payment.entity.PaymentReconciliationMismatch;
import com.meterengine.payment.entity.TossPaymentsCredential;
import com.meterengine.payment.entity.TossPaymentsCredentialId;
import com.meterengine.payment.repository.PaymentArchiveRepository;
import com.meterengine.payment.repository.PaymentAttemptRepository;
import com.meterengine.payment.repository.PaymentCancellationRepository;
import com.meterengine.payment.repository.PaymentReconciliationMismatchRepository;
import com.meterengine.payment.repository.TossPaymentsCredentialRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentReconciliationService {

  private static final int PAGE_SIZE = 1000;
  private static final int ATTEMPT_LOOKUP_CHUNK = 1000;

  private static final Logger logger = LoggerFactory.getLogger(PaymentReconciliationService.class);
  private static final Duration WINDOW = Duration.ofDays(1);
  private static final Duration LOOKBACK = Duration.ofHours(1);
  private static final String TOSS_DONE = "DONE";
  private static final Set<String> CANCEL_STATUSES = Set.of("PARTIAL_CANCELED", "CANCELED");
  private static final String TO_BEFORE_FROM_MESSAGE = "from보다 앞설 수 없습니다";

  private final TossPaymentsCredentialRepository tossPaymentsCredentialRepository;
  private final PaymentAttemptRepository paymentAttemptRepository;
  private final PaymentCancellationRepository paymentCancellationRepository;
  private final PaymentReconciliationMismatchRepository paymentReconciliationMismatchRepository;
  private final TossPaymentsClient tossPaymentsClient;
  private final PaymentArchiveRepository paymentArchiveRepository;
  private final PaymentMetrics paymentMetrics;
  private final PaymentProperties paymentProperties;

  PaymentReconciliationService(
      TossPaymentsCredentialRepository tossPaymentsCredentialRepository,
      PaymentAttemptRepository paymentAttemptRepository,
      PaymentCancellationRepository paymentCancellationRepository,
      PaymentReconciliationMismatchRepository paymentReconciliationMismatchRepository,
      TossPaymentsClient tossPaymentsClient,
      PaymentMetrics paymentMetrics,
      PaymentProperties paymentProperties,
      PaymentArchiveRepository paymentArchiveRepository) {
    this.tossPaymentsCredentialRepository = tossPaymentsCredentialRepository;
    this.paymentAttemptRepository = paymentAttemptRepository;
    this.paymentCancellationRepository = paymentCancellationRepository;
    this.paymentReconciliationMismatchRepository = paymentReconciliationMismatchRepository;
    this.tossPaymentsClient = tossPaymentsClient;
    this.paymentMetrics = paymentMetrics;
    this.paymentProperties = paymentProperties;
    this.paymentArchiveRepository = paymentArchiveRepository;
  }

  public PaymentJobResult reconcile(OffsetDateTime slot) {
    OffsetDateTime windowEnd = slot.minus(paymentProperties.reconciliationLag());
    Window window = new Window(windowEnd.minus(WINDOW), windowEnd, slot);
    PaymentJobTally tally = new PaymentJobTally();
    for (TossPaymentsCredentialId credentialId : tossPaymentsCredentialRepository.findAllIds()) {
      try {
        switch (PaymentEnvironments.call(
            credentialId.getOrganizationId(),
            credentialId.getEnvironmentId(),
            credentialId.getMode(),
            () -> reconcileCredential(credentialId, window))) {
          case COMPARED -> tally.count(true);
          case NOTHING_TO_COMPARE -> tally.count(false);
          case FAILED -> tally.countFailed();
        }
      } catch (RuntimeException exception) {
        logger.warn(
            "payment reconciliation failed: organization_id={}, mode={}",
            credentialId.getOrganizationId(),
            credentialId.getMode(),
            exception);
        tally.countFailed();
      }
    }
    return tally.result();
  }

  @Transactional(readOnly = true)
  public ListPaymentReconciliationMismatchesResponse list(
      UUID organizationId, LocalDate from, LocalDate to) {
    if (from.isAfter(to)) {
      throw new InvalidRequestException(
          ErrorCode.VALIDATION_ERROR, List.of(new FieldError("to", TO_BEFORE_FROM_MESSAGE)));
    }
    return ListPaymentReconciliationMismatchesResponse.from(
        paymentReconciliationMismatchRepository.findDetectedBetween(
            organizationId, startOfBillingDay(from), startOfBillingDay(to.plusDays(1))));
  }

  private Outcome reconcileCredential(TossPaymentsCredentialId credentialId, Window window) {
    Optional<TossPaymentsCredential> credential =
        tossPaymentsCredentialRepository.findById(credentialId);
    if (credential.isEmpty()) {
      return Outcome.NOTHING_TO_COMPARE;
    }
    Map<OffsetDateTime, TossPaymentsCredential> versions = new LinkedHashMap<>();
    versions.put(credential.get().getUpdatedAt(), credential.get());
    for (TossPaymentsCredential historical :
        paymentArchiveRepository.credentialVersions(
            credentialId.getOrganizationId(), credentialId.getMode())) {
      versions.putIfAbsent(historical.getUpdatedAt(), historical);
    }
    Outcome outcome = Outcome.NOTHING_TO_COMPARE;
    for (TossPaymentsCredential version : versions.values()) {
      Outcome result =
          reconcileVersion(
              version, window, version.getUpdatedAt().isEqual(credential.get().getUpdatedAt()));
      if (result == Outcome.FAILED) return result;
      if (result == Outcome.COMPARED) outcome = result;
    }
    return outcome;
  }

  private Outcome reconcileVersion(
      TossPaymentsCredential credential, Window window, boolean current) {
    Optional<List<TossPaymentsTransaction>> transactions = listTransactions(credential, window);
    if (transactions.isEmpty()) {
      return Outcome.FAILED;
    }
    UUID organizationId = credential.getOrganizationId();
    String mode = credential.getMode();
    Reconciliation reconciliation =
        new Reconciliation(
            organizationId,
            mode,
            window,
            paymentAttemptRepository
                .findDoneCompletedBetween(
                    organizationId, mode, window.lookbackStartAt(), window.slotAt())
                .stream()
                .filter(attempt -> usesCredential(attempt, credential, current))
                .toList(),
            paymentCancellationRepository
                .findDoneCompletedBetween(
                    organizationId, mode, window.lookbackStartAt(), window.slotAt())
                .stream()
                .filter(
                    cancellation ->
                        paymentAttemptRepository
                            .findUnscoped(organizationId, cancellation.getPaymentAttemptId())
                            .filter(attempt -> usesCredential(attempt, credential, current))
                            .isPresent())
                .toList());
    reconciliation.useFallbackAttempts(
        findAttempts(organizationId, mode, reconciliation.fallbackAttemptIds(transactions.get())));
    for (TossPaymentsTransaction transaction : transactions.get()) {
      if (belongsToCredential(transaction, credential, current)) reconciliation.match(transaction);
    }
    boolean complete = reconciliation.collectMissingInTossPayments(credential);
    OffsetDateTime detectedAt = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
    for (Mismatch mismatch : reconciliation.mismatches) {
      record(mismatch.toEntity(organizationId, mode, detectedAt));
    }
    if (!complete) {
      return Outcome.FAILED;
    }
    boolean nothingToCompare =
        transactions.get().isEmpty()
            && reconciliation.attempts.isEmpty()
            && reconciliation.cancellations.isEmpty();
    return nothingToCompare ? Outcome.NOTHING_TO_COMPARE : Outcome.COMPARED;
  }

  private boolean usesCredential(
      PaymentAttempt attempt, TossPaymentsCredential credential, boolean current) {
    return attempt.getCredentialUpdatedAt() == null
        ? current
        : attempt.getCredentialUpdatedAt().isEqual(credential.getUpdatedAt());
  }

  private boolean belongsToCredential(
      TossPaymentsTransaction transaction, TossPaymentsCredential credential, boolean current) {
    Optional<PaymentAttempt> original =
        parseAttemptId(transaction.orderId()).flatMap(paymentAttemptRepository::findById);
    if (original.isEmpty()) return current;
    return credential.getOrganizationId().equals(original.get().getOrganizationId())
        && BusinessEnvironmentContext.matches(original.get().getEnvironmentId())
        && usesCredential(original.get(), credential, current);
  }

  private static Optional<UUID> parseAttemptId(String orderId) {
    try {
      return Optional.of(UUID.fromString(orderId));
    } catch (IllegalArgumentException | NullPointerException ignored) {
      return Optional.empty();
    }
  }

  private Optional<List<TossPaymentsTransaction>> listTransactions(
      TossPaymentsCredential credential, Window window) {
    LocalDateTime startDate = inBillingZone(window.lookbackStartAt());
    LocalDateTime endDate = inBillingZone(window.endAt());
    List<TossPaymentsTransaction> transactions = new ArrayList<>();
    String startingAfter = null;
    while (true) {
      TossPaymentsResult<List<TossPaymentsTransaction>> page =
          tossPaymentsClient.listTransactions(
              credential.getSecretKey(), startDate, endDate, startingAfter, PAGE_SIZE);
      switch (page) {
        case TossPaymentsResult.Success<List<TossPaymentsTransaction>> success -> {
          transactions.addAll(success.value());
          if (success.value().size() < PAGE_SIZE) {
            return Optional.of(transactions);
          }
          String lastTransactionKey = success.value().getLast().transactionKey();
          if (lastTransactionKey == null || lastTransactionKey.equals(startingAfter)) {
            logger.warn(
                "toss payments transaction list did not advance during reconciliation:"
                    + " organization_id={}, mode={}, starting_after={}",
                credential.getOrganizationId(),
                credential.getMode(),
                startingAfter);
            return Optional.empty();
          }
          startingAfter = lastTransactionKey;
        }
        case TossPaymentsResult.Rejected<List<TossPaymentsTransaction>> rejected -> {
          logger.warn(
              "toss payments transaction list rejected during reconciliation:"
                  + " organization_id={}, mode={}, status={}, code={}, message={}",
              credential.getOrganizationId(),
              credential.getMode(),
              rejected.status(),
              rejected.code(),
              new TossPaymentsError(rejected.code(), rejected.message()).singleLineMessage());
          return Optional.empty();
        }
        case TossPaymentsResult.Unknown<List<TossPaymentsTransaction>> unknown -> {
          logger.warn(
              "toss payments transaction list result unknown during reconciliation:"
                  + " organization_id={}, mode={}, reason={}",
              credential.getOrganizationId(),
              credential.getMode(),
              unknown.reason());
          return Optional.empty();
        }
      }
    }
  }

  private List<PaymentAttempt> findAttempts(UUID organizationId, String mode, List<UUID> ids) {
    List<PaymentAttempt> attempts = new ArrayList<>();
    for (int from = 0; from < ids.size(); from += ATTEMPT_LOOKUP_CHUNK) {
      attempts.addAll(
          paymentAttemptRepository.findByOrganizationIdAndModeAndIdIn(
              organizationId,
              mode,
              ids.subList(from, Math.min(from + ATTEMPT_LOOKUP_CHUNK, ids.size()))));
    }
    return attempts;
  }

  private void record(PaymentReconciliationMismatch mismatch) {
    if (paymentReconciliationMismatchRepository.insertIfAbsent(mismatch) == 1) {
      paymentMetrics.countReconciliationMismatch(mismatch.getMode(), mismatch.getKind());
    }
  }

  private static LocalDateTime inBillingZone(OffsetDateTime time) {
    return time.atZoneSameInstant(BillableMetricUsageService.BILLING_ZONE).toLocalDateTime();
  }

  private static OffsetDateTime startOfBillingDay(LocalDate date) {
    return date.atStartOfDay(BillableMetricUsageService.BILLING_ZONE).toOffsetDateTime();
  }

  private enum Outcome {
    COMPARED,
    NOTHING_TO_COMPARE,
    FAILED
  }

  private record Window(OffsetDateTime startAt, OffsetDateTime endAt, OffsetDateTime slotAt) {

    OffsetDateTime lookbackStartAt() {
      return startAt.minus(LOOKBACK);
    }

    boolean contains(OffsetDateTime time) {
      return !time.isBefore(startAt) && time.isBefore(endAt);
    }
  }

  private record Mismatch(
      String kind,
      String orderId,
      String paymentKey,
      String transactionKey,
      Long meterengineAmount,
      Long tossPaymentsAmount,
      OffsetDateTime transactedAt) {

    PaymentReconciliationMismatch toEntity(
        UUID organizationId, String mode, OffsetDateTime detectedAt) {
      return new PaymentReconciliationMismatch(
          UUID.randomUUID(),
          organizationId,
          mode,
          kind,
          orderId,
          paymentKey,
          transactionKey,
          meterengineAmount,
          tossPaymentsAmount,
          transactedAt,
          detectedAt);
    }
  }

  private final class Reconciliation {

    private final UUID organizationId;
    private final String mode;
    private final Window window;
    private final List<PaymentAttempt> attempts;
    private final List<PaymentCancellation> cancellations;
    private final Map<String, PaymentAttempt> attemptsByPaymentKey;
    private final Map<String, PaymentCancellation> cancellationsByTransactionKey;
    private final Map<UUID, PaymentAttempt> fallbackAttemptsById = new HashMap<>();
    private final Set<UUID> matchedAttemptIds = new HashSet<>();
    private final Set<UUID> matchedCancellationIds = new HashSet<>();
    private final List<Mismatch> mismatches = new ArrayList<>();

    private Reconciliation(
        UUID organizationId,
        String mode,
        Window window,
        List<PaymentAttempt> attempts,
        List<PaymentCancellation> cancellations) {
      this.organizationId = organizationId;
      this.mode = mode;
      this.window = window;
      this.attempts = attempts;
      this.cancellations = cancellations;
      this.attemptsByPaymentKey =
          indexFirst(attempts, PaymentAttempt::getPaymentKey, "payment_key");
      this.cancellationsByTransactionKey =
          indexFirst(cancellations, PaymentCancellation::getTransactionKey, "transaction_key");
    }

    private <T> Map<String, T> indexFirst(List<T> rows, Function<T, String> key, String keyName) {
      Map<String, T> rowsByKey = new LinkedHashMap<>();
      for (T row : rows) {
        if (rowsByKey.putIfAbsent(key.apply(row), row) != null) {
          logger.warn(
              "duplicate {} among done rows during reconciliation: organization_id={}, mode={}",
              keyName,
              organizationId,
              mode);
        }
      }
      return rowsByKey;
    }

    private List<UUID> fallbackAttemptIds(List<TossPaymentsTransaction> transactions) {
      Set<UUID> ids = new LinkedHashSet<>();
      for (TossPaymentsTransaction transaction : transactions) {
        if (needsFallback(transaction)) {
          attemptId(transaction.orderId()).ifPresent(ids::add);
        }
      }
      return List.copyOf(ids);
    }

    private void useFallbackAttempts(List<PaymentAttempt> attempts) {
      for (PaymentAttempt attempt : attempts) {
        fallbackAttemptsById.put(attempt.getId(), attempt);
      }
    }

    private boolean needsFallback(TossPaymentsTransaction transaction) {
      return TOSS_DONE.equals(transaction.status())
          && window.contains(transaction.transactionAt())
          && !cancellationsByTransactionKey.containsKey(transaction.transactionKey())
          && !attemptsByPaymentKey.containsKey(transaction.paymentKey());
    }

    private void match(TossPaymentsTransaction transaction) {
      PaymentCancellation cancellation =
          cancellationsByTransactionKey.get(transaction.transactionKey());
      if (cancellation != null) {
        matchedCancellationIds.add(cancellation.getId());
        compareAmount(transaction, cancellation.getAmount());
        return;
      }
      if (CANCEL_STATUSES.contains(transaction.status())) {
        addFromTossPayments(
            transaction, PaymentReconciliationMismatch.MISSING_IN_METERENGINE, null);
        return;
      }
      PaymentAttempt attempt = attemptsByPaymentKey.get(transaction.paymentKey());
      if (attempt != null) {
        if (TOSS_DONE.equals(transaction.status())) {
          matchedAttemptIds.add(attempt.getId());
          compareAmount(transaction, attempt.getAmount());
        } else {
          addFromTossPayments(
              transaction, PaymentReconciliationMismatch.STATUS_MISMATCH, attempt.getAmount());
        }
        return;
      }
      if (needsFallback(transaction)) {
        matchFallback(transaction);
      }
    }

    private void matchFallback(TossPaymentsTransaction transaction) {
      PaymentAttempt attempt =
          attemptId(transaction.orderId()).map(fallbackAttemptsById::get).orElse(null);
      if (attempt == null) {
        addFromTossPayments(
            transaction, PaymentReconciliationMismatch.MISSING_IN_METERENGINE, null);
      } else if (!PaymentAttempt.DONE.equals(attempt.getStatus())) {
        addFromTossPayments(
            transaction, PaymentReconciliationMismatch.STATUS_MISMATCH, attempt.getAmount());
      } else if (attempt.getPaymentKey().equals(transaction.paymentKey())) {
        compareAmount(transaction, attempt.getAmount());
      } else {
        addFromTossPayments(
            transaction, PaymentReconciliationMismatch.MISSING_IN_METERENGINE, null);
      }
    }

    private void compareAmount(TossPaymentsTransaction transaction, long meterengineAmount) {
      if (Math.abs(transaction.amount()) != meterengineAmount) {
        addFromTossPayments(
            transaction, PaymentReconciliationMismatch.AMOUNT_MISMATCH, meterengineAmount);
      }
    }

    private void addFromTossPayments(
        TossPaymentsTransaction transaction, String kind, Long meterengineAmount) {
      if (!window.contains(transaction.transactionAt())) {
        return;
      }
      mismatches.add(
          new Mismatch(
              kind,
              transaction.orderId(),
              transaction.paymentKey(),
              transaction.transactionKey(),
              meterengineAmount,
              transaction.amount(),
              transaction.transactionAt()));
    }

    private boolean collectMissingInTossPayments(TossPaymentsCredential credential) {
      boolean complete = true;
      for (PaymentAttempt attempt : attempts) {
        if (!matchedAttemptIds.contains(attempt.getId())
            && window.contains(attempt.getCompletedAt())) {
          complete &= confirmMissingAttempt(attempt, credential);
        }
      }
      for (PaymentCancellation cancellation : cancellations) {
        if (!matchedCancellationIds.contains(cancellation.getId())
            && window.contains(cancellation.getCompletedAt())) {
          mismatches.add(
              new Mismatch(
                  PaymentReconciliationMismatch.MISSING_IN_TOSS_PAYMENTS,
                  cancellation.getPaymentAttemptId().toString(),
                  paymentKeyOf(cancellation),
                  cancellation.getTransactionKey(),
                  cancellation.getAmount(),
                  null,
                  cancellation.getCompletedAt()));
        }
      }
      return complete;
    }

    private boolean confirmMissingAttempt(
        PaymentAttempt attempt, TossPaymentsCredential credential) {
      return switch (tossPaymentsClient.findPayment(
          credential.getSecretKey(), attempt.getPaymentKey())) {
        case TossPaymentsResult.Success<TossPaymentsPayment> found -> {
          TossPaymentsPayment payment = found.value();
          if (!attempt.getPaymentKey().equals(payment.paymentKey())
              || attemptId(payment.orderId()).filter(attempt.getId()::equals).isEmpty()
              || !(TOSS_DONE.equals(payment.status())
                  || CANCEL_STATUSES.contains(payment.status()))) {
            addFromAttempt(
                attempt, PaymentReconciliationMismatch.STATUS_MISMATCH, payment.totalAmount());
          } else if (attempt.getAmount() != payment.totalAmount()) {
            addFromAttempt(
                attempt, PaymentReconciliationMismatch.AMOUNT_MISMATCH, payment.totalAmount());
          }
          yield true;
        }
        case TossPaymentsResult.Rejected<TossPaymentsPayment> notFound
            when TossPaymentsFailureCode.isNotFoundPayment(notFound)
                && credential.getUpdatedAt().isBefore(attempt.getRequestedAt()) -> {
          addFromAttempt(attempt, PaymentReconciliationMismatch.MISSING_IN_TOSS_PAYMENTS, null);
          yield true;
        }
        case TossPaymentsResult.Rejected<TossPaymentsPayment> rejected -> {
          logger.warn(
              "toss payments payment lookup inconclusive during reconciliation:"
                  + " attempt_id={}, mode={}, status={}, code={}",
              attempt.getId(),
              mode,
              rejected.status(),
              rejected.code());
          yield false;
        }
        case TossPaymentsResult.Unknown<TossPaymentsPayment> unknown -> {
          logger.warn(
              "toss payments payment lookup result unknown during reconciliation:"
                  + " attempt_id={}, mode={}, reason={}",
              attempt.getId(),
              mode,
              unknown.reason());
          yield false;
        }
      };
    }

    private void addFromAttempt(PaymentAttempt attempt, String kind, Long tossPaymentsAmount) {
      mismatches.add(
          new Mismatch(
              kind,
              attempt.getId().toString(),
              attempt.getPaymentKey(),
              null,
              attempt.getAmount(),
              tossPaymentsAmount,
              attempt.getCompletedAt()));
    }

    private String paymentKeyOf(PaymentCancellation cancellation) {
      return paymentAttemptRepository
          .findByOrganizationIdAndId(organizationId, cancellation.getPaymentAttemptId())
          .map(PaymentAttempt::getPaymentKey)
          .orElse(null);
    }
  }

  private static Optional<UUID> attemptId(String orderId) {
    if (orderId == null) {
      return Optional.empty();
    }
    String suffix = orderId.substring(orderId.lastIndexOf('_') + 1);
    try {
      UUID id = UUID.fromString(suffix);
      return id.toString().equals(suffix) ? Optional.of(id) : Optional.empty();
    } catch (IllegalArgumentException exception) {
      return Optional.empty();
    }
  }
}
