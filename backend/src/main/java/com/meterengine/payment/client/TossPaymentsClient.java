package com.meterengine.payment.client;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class TossPaymentsClient {

  private final RestClient restClient;

  public TossPaymentsClient(RestClient restClient) {
    this.restClient = restClient;
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

  private <T> TossPaymentsResult<T> send(
      RestClient.RequestHeadersSpec<?> request, Class<T> bodyType) {
    try {
      return request.exchange(
          (clientRequest, response) -> {
            HttpStatusCode status = response.getStatusCode();
            if (status.is2xxSuccessful()) {
              return new TossPaymentsResult.Success<>(response.bodyTo(bodyType));
            }
            TossPaymentsError error = readError(response);
            if (status.is4xxClientError()) {
              return new TossPaymentsResult.Rejected<>(
                  status.value(), error.code(), error.message());
            }
            return new TossPaymentsResult.Unknown<>(status.value() + " " + error.code());
          });
    } catch (ResourceAccessException exception) {
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
