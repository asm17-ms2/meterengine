package com.meterengine.payment.client;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class TossPaymentsClient {

  private static final String IDEMPOTENT_REQUEST_PROCESSING = "IDEMPOTENT_REQUEST_PROCESSING";
  private static final String EMPTY_SUCCESS_BODY = "2xx without body";

  private final RestClient restClient;

  public TossPaymentsClient(RestClient restClient) {
    this.restClient = restClient;
  }

  public TossPaymentsResult<TossPaymentsBilling> issueBillingKey(
      String authKey, String customerKey) {
    return send(
        restClient
            .post()
            .uri("/v1/billing/authorizations/issue")
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("authKey", authKey, "customerKey", customerKey)),
        TossPaymentsBilling.class);
  }

  public TossPaymentsResult<TossPaymentsPayment> approveBilling(
      String billingKey, TossPaymentsApproveBillingRequest approval, String idempotencyKey) {
    return send(
        restClient
            .post()
            .uri("/v1/billing/{billingKey}", billingKey)
            .header("Idempotency-Key", idempotencyKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(approval),
        TossPaymentsPayment.class);
  }

  public TossPaymentsResult<Void> deleteBillingKey(String billingKey) {
    return send(restClient.delete().uri("/v1/billing/{billingKey}", billingKey), Void.class);
  }

  public TossPaymentsResult<TossPaymentsPayment> findPaymentByOrderId(String orderId) {
    return send(
        restClient.get().uri("/v1/payments/orders/{orderId}", orderId), TossPaymentsPayment.class);
  }

  private <T> TossPaymentsResult<T> send(
      RestClient.RequestHeadersSpec<?> request, Class<T> bodyType) {
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

  private static boolean mayStillBeProcessing(HttpStatusCode status, TossPaymentsError error) {
    return IDEMPOTENT_REQUEST_PROCESSING.equals(error.code())
        || (status.value() == HttpStatus.CONFLICT.value() && error.code() == null);
  }

  private static <T> TossPaymentsResult<T> readSuccess(
      RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response, Class<T> bodyType) {
    if (bodyType == Void.class) {
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
