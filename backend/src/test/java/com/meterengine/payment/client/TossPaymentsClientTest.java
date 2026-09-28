package com.meterengine.payment.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.meterengine.payment.config.TossPaymentsConfig;
import com.meterengine.payment.config.TossPaymentsProperties;
import java.net.SocketTimeoutException;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TossPaymentsClientTest {

  private static final String BASE = "https://api.tosspayments.com";
  private static final String SECRET_KEY = "test_sk_client";

  private MockRestServiceServer server;
  private TossPaymentsClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    TossPaymentsConfig.withTossDefaults(
        builder, new TossPaymentsProperties(SECRET_KEY, URI.create(BASE)));
    client = new TossPaymentsClient(builder.build());
  }

  @Test
  void 승인은_멱등키_헤더와_네_값을_보낸다() {
    server
        .expect(requestTo(BASE + "/v1/billing/bk-1"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Idempotency-Key", "order-000001"))
        .andExpect(
            content()
                .json(
                    """
                    {"customerKey":"customer-1","amount":10000,
                     "orderId":"order-000001","orderName":"2026-09 사용료"}
                    """))
        .andRespond(
            withSuccess(
                "{\"paymentKey\":\"pk-1\",\"orderId\":\"order-000001\",\"status\":\"DONE\"}",
                MediaType.APPLICATION_JSON));

    TossPaymentsResult<TossPaymentsPayment> result =
        client.approveBilling(
            "bk-1",
            new TossPaymentsApproveBillingRequest(
                "customer-1", 10000, "order-000001", "2026-09 사용료"),
            "order-000001");

    assertThat(result)
        .isEqualTo(
            new TossPaymentsResult.Success<>(
                new TossPaymentsPayment("pk-1", "order-000001", "DONE")));
  }

  @Test
  void 빌링키의_특수문자는_경로에서_인코딩된다() {
    server
        .expect(requestTo(BASE + "/v1/billing/a%2Bb%2Fc%3D"))
        .andRespond(
            withSuccess(
                "{\"paymentKey\":\"pk\",\"orderId\":\"o\",\"status\":\"DONE\"}",
                MediaType.APPLICATION_JSON));

    client.approveBilling(
        "a+b/c=",
        new TossPaymentsApproveBillingRequest("c", 1, "order-000001", "n"),
        "order-000001");

    server.verify();
  }

  @Test
  void 오류_코드가_있는_4xx는_거절이다() {
    server
        .expect(requestTo(BASE + "/v1/billing/bk-1"))
        .andRespond(
            withStatus(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":\"REJECT_CARD_COMPANY\",\"message\":\"한도 초과\"}"));

    TossPaymentsResult<TossPaymentsPayment> result =
        client.approveBilling(
            "bk-1",
            new TossPaymentsApproveBillingRequest("c", 1, "order-000001", "n"),
            "order-000001");

    assertThat(result)
        .isEqualTo(new TossPaymentsResult.Rejected<>(400, "REJECT_CARD_COMPANY", "한도 초과"));
  }

  @Test
  void 응답_5xx는_결과_모름이다() {
    server
        .expect(requestTo(BASE + "/v1/billing/bk-1"))
        .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

    TossPaymentsResult<TossPaymentsPayment> result =
        client.approveBilling(
            "bk-1",
            new TossPaymentsApproveBillingRequest("c", 1, "order-000001", "n"),
            "order-000001");

    assertThat(result).isInstanceOf(TossPaymentsResult.Unknown.class);
  }

  @Test
  void 타임아웃은_결과_모름이다() {
    server
        .expect(requestTo(BASE + "/v1/billing/bk-1"))
        .andRespond(withException(new SocketTimeoutException("read timed out")));

    TossPaymentsResult<TossPaymentsPayment> result =
        client.approveBilling(
            "bk-1",
            new TossPaymentsApproveBillingRequest("c", 1, "order-000001", "n"),
            "order-000001");

    assertThat(result).isInstanceOf(TossPaymentsResult.Unknown.class);
    assertThat(((TossPaymentsResult.Unknown<TossPaymentsPayment>) result).reason())
        .doesNotContain("bk-1");
  }

  @Test
  void 오류_본문이_JSON이_아니어도_상태로_판정한다() {
    server
        .expect(requestTo(BASE + "/v1/billing/bk-1"))
        .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("<html>bad</html>"));

    TossPaymentsResult<TossPaymentsPayment> result =
        client.approveBilling(
            "bk-1",
            new TossPaymentsApproveBillingRequest("c", 1, "order-000001", "n"),
            "order-000001");

    assertThat(result).isEqualTo(new TossPaymentsResult.Rejected<>(400, null, null));
  }
}
