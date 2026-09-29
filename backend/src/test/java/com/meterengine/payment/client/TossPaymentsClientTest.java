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
import java.util.Base64;
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
  void 시크릿_키_뒤에_콜론을_붙여_Basic_인증한다() {
    String expected = "Basic " + Base64.getEncoder().encodeToString((SECRET_KEY + ":").getBytes());
    server
        .expect(requestTo(BASE + "/v1/billing/authorizations/issue"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", expected))
        .andExpect(content().json("{\"authKey\":\"auth-1\",\"customerKey\":\"customer-1\"}"))
        .andRespond(
            withSuccess(
                """
                {"billingKey":"bk-1","authenticatedAt":"2026-09-28T10:00:00+09:00",
                 "card":{"issuerCode":"4V","number":"43301234****123*"},"mId":"tvivarepublica"}
                """,
                MediaType.APPLICATION_JSON));

    TossPaymentsResult<TossPaymentsBilling> result = client.issueBillingKey("auth-1", "customer-1");

    assertThat(result).isInstanceOf(TossPaymentsResult.Success.class);
    TossPaymentsBilling billing =
        ((TossPaymentsResult.Success<TossPaymentsBilling>) result).value();
    assertThat(billing.billingKey()).isEqualTo("bk-1");
    assertThat(billing.card().issuerCode()).isEqualTo("4V");
    assertThat(billing.card().number()).isEqualTo("43301234****123*");
    server.verify();
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
  void 처리중_409는_결과_모름이다() {
    server
        .expect(requestTo(BASE + "/v1/billing/bk-1"))
        .andRespond(
            withStatus(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":\"IDEMPOTENT_REQUEST_PROCESSING\",\"message\":\"처리 중\"}"));

    TossPaymentsResult<TossPaymentsPayment> result =
        client.approveBilling(
            "bk-1",
            new TossPaymentsApproveBillingRequest("c", 1, "order-000001", "n"),
            "order-000001");

    assertThat(result).isInstanceOf(TossPaymentsResult.Unknown.class);
  }

  @Test
  void 본문_없는_409는_결과_모름이다() {
    server.expect(requestTo(BASE + "/v1/billing/bk-1")).andRespond(withStatus(HttpStatus.CONFLICT));

    TossPaymentsResult<TossPaymentsPayment> result =
        client.approveBilling(
            "bk-1",
            new TossPaymentsApproveBillingRequest("c", 1, "order-000001", "n"),
            "order-000001");

    assertThat(result).isInstanceOf(TossPaymentsResult.Unknown.class);
  }

  @Test
  void 처리중이_아닌_코드의_409는_거절이다() {
    server
        .expect(requestTo(BASE + "/v1/billing/bk-1"))
        .andRespond(
            withStatus(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":\"DUPLICATED_ORDER_ID\",\"message\":\"이미 쓴 주문번호\"}"));

    TossPaymentsResult<TossPaymentsPayment> result =
        client.approveBilling(
            "bk-1",
            new TossPaymentsApproveBillingRequest("c", 1, "order-000001", "n"),
            "order-000001");

    assertThat(result)
        .isEqualTo(new TossPaymentsResult.Rejected<>(409, "DUPLICATED_ORDER_ID", "이미 쓴 주문번호"));
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
  void 승인_2xx_본문을_읽지_못하면_결과_모름이다() {
    server
        .expect(requestTo(BASE + "/v1/billing/bk-1"))
        .andRespond(withSuccess("{", MediaType.APPLICATION_JSON));

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
  void 승인_2xx_본문이_비면_결과_모름이다() {
    server.expect(requestTo(BASE + "/v1/billing/bk-1")).andRespond(withSuccess());

    TossPaymentsResult<TossPaymentsPayment> result =
        client.approveBilling(
            "bk-1",
            new TossPaymentsApproveBillingRequest("c", 1, "order-000001", "n"),
            "order-000001");

    assertThat(result).isInstanceOf(TossPaymentsResult.Unknown.class);
  }

  @Test
  void 조회에_있으면_성공이다() {
    server
        .expect(requestTo(BASE + "/v1/payments/orders/order-000001"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withSuccess(
                "{\"paymentKey\":\"pk-1\",\"orderId\":\"order-000001\",\"status\":\"DONE\"}",
                MediaType.APPLICATION_JSON));

    TossPaymentsResult<TossPaymentsPayment> result = client.findPaymentByOrderId("order-000001");

    assertThat(result)
        .isEqualTo(
            new TossPaymentsResult.Success<>(
                new TossPaymentsPayment("pk-1", "order-000001", "DONE")));
  }

  @Test
  void 조회에_없으면_404_거절이다() {
    server
        .expect(requestTo(BASE + "/v1/payments/orders/order-000001"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":\"NOT_FOUND_PAYMENT\",\"message\":\"없음\"}"));

    TossPaymentsResult<TossPaymentsPayment> result = client.findPaymentByOrderId("order-000001");

    assertThat(result).isEqualTo(new TossPaymentsResult.Rejected<>(404, "NOT_FOUND_PAYMENT", "없음"));
  }

  @Test
  void 빌링키_삭제는_빈_본문_200이면_성공이다() {
    server
        .expect(requestTo(BASE + "/v1/billing/bk-1"))
        .andExpect(method(HttpMethod.DELETE))
        .andRespond(withSuccess());

    assertThat(client.deleteBillingKey("bk-1"))
        .isEqualTo(new TossPaymentsResult.Success<Void>(null));
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
