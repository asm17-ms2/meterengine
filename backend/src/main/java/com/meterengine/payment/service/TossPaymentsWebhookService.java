package com.meterengine.payment.service;

import com.meterengine.payment.client.TossPaymentsClient;
import com.meterengine.payment.client.TossPaymentsPayment;
import com.meterengine.payment.client.TossPaymentsResult;
import com.meterengine.payment.entity.PaymentAttempt;
import com.meterengine.payment.entity.PaymentCancellation;
import com.meterengine.payment.entity.TossPaymentsCredential;
import com.meterengine.payment.repository.PaymentAttemptRepository;
import com.meterengine.payment.repository.PaymentCancellationRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class TossPaymentsWebhookService {

  private static final Logger logger = LoggerFactory.getLogger(TossPaymentsWebhookService.class);

  private final PaymentAttemptRepository paymentAttemptRepository;
  private final PaymentCancellationRepository paymentCancellationRepository;
  private final TossPaymentsCredentialService tossPaymentsCredentialService;
  private final PaymentAttemptService paymentAttemptService;
  private final PaymentCancellationService paymentCancellationService;
  private final TossPaymentsClient tossPaymentsClient;
  private final PaymentMetrics paymentMetrics;
  private final JsonMapper jsonMapper;

  TossPaymentsWebhookService(
      PaymentAttemptRepository paymentAttemptRepository,
      PaymentCancellationRepository paymentCancellationRepository,
      TossPaymentsCredentialService tossPaymentsCredentialService,
      PaymentAttemptService paymentAttemptService,
      PaymentCancellationService paymentCancellationService,
      TossPaymentsClient tossPaymentsClient,
      PaymentMetrics paymentMetrics,
      JsonMapper jsonMapper) {
    this.paymentAttemptRepository = paymentAttemptRepository;
    this.paymentCancellationRepository = paymentCancellationRepository;
    this.tossPaymentsCredentialService = tossPaymentsCredentialService;
    this.paymentAttemptService = paymentAttemptService;
    this.paymentCancellationService = paymentCancellationService;
    this.tossPaymentsClient = tossPaymentsClient;
    this.paymentMetrics = paymentMetrics;
    this.jsonMapper = jsonMapper;
  }

  public void receive(UUID organizationId, String body) {
    JsonNode event = parse(body);
    String eventType = stringField(event.path("eventType"));
    if (!PaymentMetrics.PAYMENT_STATUS_CHANGED.equals(eventType)) {
      paymentMetrics.countWebhook(eventType, PaymentMetrics.IGNORED);
      return;
    }
    JsonNode data = event.path("data");
    Optional<PaymentAttempt> attempt =
        parseUuid(stringField(data.path("orderId")))
            .flatMap(
                orderId ->
                    paymentAttemptRepository.findByOrganizationIdAndId(organizationId, orderId));
    if (attempt.isEmpty() || !awaitsSettlement(attempt.get())) {
      paymentMetrics.countWebhook(eventType, PaymentMetrics.IGNORED);
      return;
    }
    Optional<TossPaymentsCredential> credential =
        tossPaymentsCredentialService.historical(
            organizationId, attempt.get().getMode(), attempt.get().getRequestedAt());
    if (credential.isEmpty()) {
      logOutcome(organizationId, attempt.get().getId(), PaymentMetrics.IGNORED, null);
      paymentMetrics.countWebhook(eventType, PaymentMetrics.IGNORED);
      return;
    }
    String outcome =
        reflect(
            attempt.get(),
            credential.get().getSecretKey(),
            lookupPaymentKey(attempt.get(), stringField(data.path("paymentKey"))));
    paymentMetrics.countWebhook(eventType, outcome);
  }

  private boolean awaitsSettlement(PaymentAttempt attempt) {
    return PaymentAttempt.PENDING.equals(attempt.getStatus())
        || paymentCancellationRepository.existsByPaymentAttemptIdAndStatus(
            attempt.getId(), PaymentCancellation.PENDING);
  }

  private static String lookupPaymentKey(PaymentAttempt attempt, String bodyPaymentKey) {
    if (attempt.getPaymentKey() != null) {
      return attempt.getPaymentKey();
    }
    return PaymentAttempt.PENDING.equals(attempt.getStatus()) ? bodyPaymentKey : null;
  }

  private String reflect(PaymentAttempt attempt, String secretKey, String paymentKey) {
    UUID organizationId = attempt.getOrganizationId();
    UUID attemptId = attempt.getId();
    TossPaymentsResult<TossPaymentsPayment> result =
        paymentKey == null
            ? tossPaymentsClient.findPaymentByOrderId(secretKey, attemptId.toString())
            : tossPaymentsClient.findPayment(secretKey, paymentKey);
    switch (result) {
      case TossPaymentsResult.Success<TossPaymentsPayment> found
          when attemptId.toString().equals(found.value().orderId()) -> {
        String outcome = apply(attempt, found.value());
        logOutcome(organizationId, attemptId, outcome, found.value().status());
        return outcome;
      }
      case TossPaymentsResult.Success<TossPaymentsPayment> otherOrder -> {
        logOutcome(organizationId, attemptId, PaymentMetrics.IGNORED, otherOrder.value().status());
        return PaymentMetrics.IGNORED;
      }
      case TossPaymentsResult.Rejected<TossPaymentsPayment> rejected -> {
        logOutcome(
            organizationId,
            attemptId,
            PaymentMetrics.IGNORED,
            TossPaymentsFailureCode.httpStatus(rejected.status()));
        return PaymentMetrics.IGNORED;
      }
      case TossPaymentsResult.Unknown<TossPaymentsPayment> unknown -> {
        paymentMetrics.countWebhook(PaymentMetrics.PAYMENT_STATUS_CHANGED, PaymentMetrics.UNKNOWN);
        throw new IllegalStateException(
            "toss payments payment lookup for webhook result unknown: organization_id="
                + organizationId
                + ", attempt_id="
                + attemptId
                + ", reason="
                + unknown.reason());
      }
    }
  }

  private String apply(PaymentAttempt attempt, TossPaymentsPayment payment) {
    List<PaymentCancellation> pendingCancellations =
        paymentCancellationRepository
            .findByPaymentAttemptIdOrderByRequestedAtDesc(attempt.getId())
            .stream()
            .filter(cancellation -> PaymentCancellation.PENDING.equals(cancellation.getStatus()))
            .toList();
    boolean applied = false;
    if (PaymentAttempt.PENDING.equals(attempt.getStatus())) {
      applied = paymentAttemptService.settle(attempt, payment);
    }
    for (PaymentCancellation pending : pendingCancellations) {
      Optional<String> transactionKey =
          paymentCancellationService.matchTransactionKey(pending, payment);
      if (transactionKey.isPresent()
          && paymentCancellationService.markDone(pending, transactionKey.get())) {
        applied = true;
      }
    }
    return applied ? PaymentMetrics.APPLIED : PaymentMetrics.IGNORED;
  }

  private JsonNode parse(String body) {
    if (body == null) {
      return jsonMapper.missingNode();
    }
    try {
      return jsonMapper.readTree(body);
    } catch (JacksonException exception) {
      return jsonMapper.missingNode();
    }
  }

  private static String stringField(JsonNode node) {
    return node.isString() ? node.stringValue() : null;
  }

  private static Optional<UUID> parseUuid(String value) {
    if (value == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(UUID.fromString(value));
    } catch (IllegalArgumentException exception) {
      return Optional.empty();
    }
  }

  private static void logOutcome(
      UUID organizationId, UUID attemptId, String outcome, String tossStatus) {
    logger.info(
        "toss payments webhook handled: organization_id={}, attempt_id={}, outcome={},"
            + " toss_status={}",
        organizationId,
        attemptId,
        outcome,
        tossStatus);
  }
}
