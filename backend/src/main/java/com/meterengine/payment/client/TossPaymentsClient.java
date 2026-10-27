package com.meterengine.payment.client;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

public class TossPaymentsClient {

  private static final String IDEMPOTENT_REQUEST_PROCESSING = "IDEMPOTENT_REQUEST_PROCESSING";
  private static final String EMPTY_SUCCESS_BODY = "2xx without body";
  private static final String REQUEST_TIMER = "meterengine.toss.payments.request";
  private static final String ISSUE_BILLING_KEY = "issue_billing_key";
  private static final String APPROVE_BILLING = "approve_billing";
  private static final String DELETE_BILLING_KEY = "delete_billing_key";
  private static final String FIND_PAYMENT_BY_ORDER_ID = "find_payment_by_order_id";
  private static final String FIND_PAYMENT = "find_payment";
  private static final String CANCEL = "cancel";
  private static final String LIST_TRANSACTIONS = "list_transactions";
  private static final List<String> OPERATIONS =
      List.of(
          ISSUE_BILLING_KEY,
          APPROVE_BILLING,
          DELETE_BILLING_KEY,
          FIND_PAYMENT_BY_ORDER_ID,
          FIND_PAYMENT,
          CANCEL,
          LIST_TRANSACTIONS);
  private static final String SUCCESS = "success";
  private static final String REJECTED = "rejected";
  private static final String UNKNOWN = "unknown";
  private static final List<String> OUTCOMES = List.of(SUCCESS, REJECTED, UNKNOWN);
  private static final DateTimeFormatter QUERY_DATE_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
  private static final ParameterizedTypeReference<List<TossPaymentsTransaction>> TRANSACTIONS =
      new ParameterizedTypeReference<>() {};

  private final RestClient restClient;
  private final MeterRegistry meterRegistry;
  private final Map<String, Timer> timersByOperationAndOutcome = new HashMap<>();

  public TossPaymentsClient(RestClient restClient, MeterRegistry meterRegistry) {
    this.restClient = restClient;
    this.meterRegistry = meterRegistry;
    for (String operation : OPERATIONS) {
      for (String outcome : OUTCOMES) {
        timersByOperationAndOutcome.put(
            timerKey(operation, outcome),
            Timer.builder(REQUEST_TIMER)
                .tag("operation", operation)
                .tag("outcome", outcome)
                .register(meterRegistry));
      }
    }
  }

  public TossPaymentsResult<TossPaymentsBilling> issueBillingKey(
      String secretKey, String authKey, String customerKey) {
    return send(
        ISSUE_BILLING_KEY,
        secretKey,
        restClient
            .post()
            .uri("/v1/billing/authorizations/issue")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("authKey", authKey, "customerKey", customerKey)),
        TossPaymentsBilling.class);
  }

  public TossPaymentsResult<TossPaymentsPayment> approveBilling(
      String secretKey,
      String billingKey,
      TossPaymentsApproveBillingRequest approval,
      String idempotencyKey,
      String testCode) {
    return send(
        APPROVE_BILLING,
        secretKey,
        restClient
            .post()
            .uri("/v1/billing/{billingKey}", billingKey)
            .header("Idempotency-Key", idempotencyKey)
            .headers(
                headers -> {
                  if (testCode != null) {
                    headers.set("TossPayments-Test-Code", testCode);
                  }
                })
            .contentType(MediaType.APPLICATION_JSON)
            .body(approval),
        TossPaymentsPayment.class);
  }

  public TossPaymentsResult<Void> deleteBillingKey(String secretKey, String billingKey) {
    return send(
        DELETE_BILLING_KEY,
        secretKey,
        restClient.delete().uri("/v1/billing/{billingKey}", billingKey),
        Void.class);
  }

  public TossPaymentsResult<TossPaymentsPayment> findPaymentByOrderId(
      String secretKey, String orderId) {
    return send(
        FIND_PAYMENT_BY_ORDER_ID,
        secretKey,
        restClient.get().uri("/v1/payments/orders/{orderId}", orderId),
        TossPaymentsPayment.class);
  }

  public TossPaymentsResult<TossPaymentsPayment> findPayment(String secretKey, String paymentKey) {
    return send(
        FIND_PAYMENT,
        secretKey,
        restClient.get().uri("/v1/payments/{paymentKey}", paymentKey),
        TossPaymentsPayment.class);
  }

  public TossPaymentsResult<TossPaymentsPayment> cancel(
      String secretKey,
      String paymentKey,
      TossPaymentsCancelRequest cancellation,
      String idempotencyKey) {
    return send(
        CANCEL,
        secretKey,
        restClient
            .post()
            .uri("/v1/payments/{paymentKey}/cancel", paymentKey)
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(cancellation),
        TossPaymentsPayment.class);
  }

  public TossPaymentsResult<List<TossPaymentsTransaction>> listTransactions(
      String secretKey,
      LocalDateTime startDate,
      LocalDateTime endDate,
      String startingAfter,
      int limit) {
    return send(
        LIST_TRANSACTIONS,
        secretKey,
        restClient
            .get()
            .uri(
                uriBuilder ->
                    transactionsUri(uriBuilder, startDate, endDate, startingAfter, limit)),
        TRANSACTIONS);
  }

  private static URI transactionsUri(
      UriBuilder uriBuilder,
      LocalDateTime startDate,
      LocalDateTime endDate,
      String startingAfter,
      int limit) {
    uriBuilder
        .path("/v1/transactions")
        .queryParam("startDate", QUERY_DATE_TIME.format(startDate))
        .queryParam("endDate", QUERY_DATE_TIME.format(endDate));
    if (startingAfter == null) {
      return uriBuilder.queryParam("limit", limit).build();
    }
    return uriBuilder
        .queryParam("startingAfter", "{startingAfter}")
        .queryParam("limit", limit)
        .build(startingAfter);
  }

  private <T> TossPaymentsResult<T> send(
      String operation,
      String secretKey,
      RestClient.RequestHeadersSpec<?> request,
      Class<T> bodyType) {
    return send(operation, secretKey, request, ParameterizedTypeReference.forType(bodyType));
  }

  private <T> TossPaymentsResult<T> send(
      String operation,
      String secretKey,
      RestClient.RequestHeadersSpec<?> request,
      ParameterizedTypeReference<T> bodyType) {
    Timer.Sample sample = Timer.start(meterRegistry);
    TossPaymentsResult<T> result =
        exchange(request.headers(headers -> headers.setBasicAuth(secretKey, "")), bodyType);
    sample.stop(timersByOperationAndOutcome.get(timerKey(operation, outcome(result))));
    return result;
  }

  private static <T> TossPaymentsResult<T> exchange(
      RestClient.RequestHeadersSpec<?> request, ParameterizedTypeReference<T> bodyType) {
    try {
      return request.exchange(
          (clientRequest, response) -> {
            HttpStatusCode status = response.getStatusCode();
            if (status.is2xxSuccessful()) {
              return readSuccess(response, bodyType);
            }
            TossPaymentsError error = readError(response);
            if (status.is4xxClientError() && !mayStillBeProcessing(status, error)) {
              return new TossPaymentsResult.Rejected<>(
                  status.value(), error.code(), error.message());
            }
            return new TossPaymentsResult.Unknown<>(status.value() + " " + error.code());
          });
    } catch (ResourceAccessException exception) {
      return new TossPaymentsResult.Unknown<>(reasonWithoutUrl(exception));
    }
  }

  private static String timerKey(String operation, String outcome) {
    return operation + "/" + outcome;
  }

  private static String outcome(TossPaymentsResult<?> result) {
    return switch (result) {
      case TossPaymentsResult.Success<?> success -> SUCCESS;
      case TossPaymentsResult.Rejected<?> rejected -> REJECTED;
      case TossPaymentsResult.Unknown<?> unknown -> UNKNOWN;
    };
  }

  private static boolean mayStillBeProcessing(HttpStatusCode status, TossPaymentsError error) {
    return IDEMPOTENT_REQUEST_PROCESSING.equals(error.code())
        || (status.value() == HttpStatus.CONFLICT.value() && error.code() == null);
  }

  private static <T> TossPaymentsResult<T> readSuccess(
      RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response,
      ParameterizedTypeReference<T> bodyType) {
    if (bodyType.getType() == Void.class) {
      return new TossPaymentsResult.Success<>(null);
    }
    try {
      T body = response.bodyTo(bodyType);
      return body == null
          ? new TossPaymentsResult.Unknown<>(EMPTY_SUCCESS_BODY)
          : new TossPaymentsResult.Success<>(body);
    } catch (RestClientException exception) {
      return new TossPaymentsResult.Unknown<>(reasonWithoutUrl(exception));
    }
  }

  private static String reasonWithoutUrl(RestClientException exception) {
    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
    return cause.getClass().getSimpleName() + ": " + cause.getMessage();
  }

  private static TossPaymentsError readError(
      RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response) {
    try {
      TossPaymentsError error = response.bodyTo(TossPaymentsError.class);
      return error == null ? new TossPaymentsError(null, null) : error;
    } catch (RestClientException exception) {
      return new TossPaymentsError(null, null);
    }
  }
}
