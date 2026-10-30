package com.meterengine.payment.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.meterengine.payment.config.TossPaymentsConfig;
import com.meterengine.payment.config.TossPaymentsProperties;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.observation.DefaultMeterObservationHandler;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TossPaymentsClientTest {

  private static final String BASE = "https://api.tosspayments.com";
  private static final String SECRET_KEY = "test_sk_client";
  private static final String TIMER = "meterengine.toss.payments.request";
  private static final Comparator<OffsetDateTime> SAME_INSTANT =
      Comparator.comparing(OffsetDateTime::toInstant);
  private static final String PAYMENT_BODY =
      """
      {"paymentKey":"pk-1","orderId":"order-000001","status":"DONE",
       "totalAmount":10000,"balanceAmount":10000,
       "approvedAt":"2026-10-25T10:00:00+09:00","cancels":null,"mId":"tvivarepublica"}
      """;
  private static final TossPaymentsPayment PAYMENT =
      new TossPaymentsPayment("pk-1", "order-000001", "DONE", 10000, 10000, List.of());

  private MockRestServiceServer server;
  private SimpleMeterRegistry meterRegistry;
  private TossPaymentsClient client;

  @BeforeEach
  void setUp() {
    RestClient.Builder builder = RestClient.builder();
    server = MockRestServiceServer.bindTo(builder).build();
    TossPaymentsConfig.withTossDefaults(builder, new TossPaymentsProperties(URI.create(BASE)));
    meterRegistry = new SimpleMeterRegistry();
    client = new TossPaymentsClient(builder.build(), meterRegistry);
  }

  @Test
  void 시크릿_키_뒤에_콜론을_붙여_Basic_인증한다() {
    server
        .expect(requestTo(BASE + "/v1/billing/authorizations/issue"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", basic(SECRET_KEY)))
        .andExpect(content().json("{\"authKey\":\"auth-1\",\"customerKey\":\"customer-1\"}"))
        .andRespond(
            withSuccess(
                """
                {"billingKey":"bk-1","authenticatedAt":"2026-09-28T10:00:00+09:00",
                 "card":{"issuerCode":"4V","number":"43301234****123*"},"mId":"tvivarepublica"}
                """,
                MediaType.APPLICATION_JSON));

    TossPaymentsBilling billing =
        success(client.issueBillingKey(SECRET_KEY, "auth-1", "customer-1"));

    assertThat(billing.billingKey()).isEqualTo("bk-1");
    assertThat(billing.card().issuerCode()).isEqualTo("4V");
    assertThat(billing.card().number()).isEqualTo("43301234****123*");
    assertThat(recordedCount("issue_billing_key", "success")).isEqualTo(1);
    server.verify();
  }

  @Test
  void 같은_클라이언트도_요청마다_받은_시크릿_키로_인증한다() {
    server
        .expect(requestTo(BASE + "/v1/payments/pk-1"))
        .andExpect(header("Authorization", basic("test_sk_first")))
        .andRespond(withSuccess(PAYMENT_BODY, MediaType.APPLICATION_JSON));
    server
        .expect(requestTo(BASE + "/v1/payments/pk-1"))
        .andExpect(header("Authorization", basic("live_sk_second")))
        .andRespond(withSuccess(PAYMENT_BODY, MediaType.APPLICATION_JSON));

    client.findPayment("test_sk_first", "pk-1");
    client.findPayment("live_sk_second", "pk-1");

    server.verify();
  }

  @Test
  void 승인은_멱등키_헤더와_네_값을_보낸다() {
    server
        .expect(requestTo(BASE + "/v1/billing/bk-1"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Idempotency-Key", "order-000001"))
        .andExpect(headerDoesNotExist("TossPayments-Test-Code"))
        .andExpect(
            content()
                .json(
                    """
                    {"customerKey":"customer-1","amount":10000,
                     "orderId":"order-000001","orderName":"2026-09 사용료"}
                    """))
        .andRespond(withSuccess(PAYMENT_BODY, MediaType.APPLICATION_JSON));

    TossPaymentsResult<TossPaymentsPayment> result =
        client.approveBilling(
            SECRET_KEY,
            "bk-1",
            new TossPaymentsApproveBillingRequest(
                "customer-1", 10000, "order-000001", "2026-09 사용료"),
            "order-000001",
            null);

    assertThat(success(result))
        .usingRecursiveComparison()
        .withComparatorForType(SAME_INSTANT, OffsetDateTime.class)
        .isEqualTo(PAYMENT);
    assertThat(recordedCount("approve_billing", "success")).isEqualTo(1);
    server.verify();
  }

  @Test
  void 승인에_테스트_코드를_주면_테스트_코드_헤더를_보낸다() {
    server
        .expect(requestTo(BASE + "/v1/billing/bk-1"))
        .andExpect(header("TossPayments-Test-Code", "REJECT_CARD_PAYMENT"))
        .andRespond(
            withStatus(HttpStatus.FORBIDDEN)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":\"REJECT_CARD_PAYMENT\",\"message\":\"거절\"}"));

    TossPaymentsResult<TossPaymentsPayment> result = approve("bk-1", "REJECT_CARD_PAYMENT");

    assertThat(result)
        .isEqualTo(new TossPaymentsResult.Rejected<>(403, "REJECT_CARD_PAYMENT", "거절"));
    server.verify();
  }

  @Test
  void 빌링키의_특수문자는_경로에서_인코딩된다() {
    server
        .expect(requestTo(BASE + "/v1/billing/a%2Bb%2Fc%3D"))
        .andRespond(withSuccess(PAYMENT_BODY, MediaType.APPLICATION_JSON));

    approve("a+b/c=", null);

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

    TossPaymentsResult<TossPaymentsPayment> result = approve("bk-1", null);

    assertThat(result)
        .isEqualTo(new TossPaymentsResult.Rejected<>(400, "REJECT_CARD_COMPANY", "한도 초과"));
    assertThat(recordedCount("approve_billing", "rejected")).isEqualTo(1);
    assertThat(recordedCount("approve_billing", "success")).isZero();
  }

  @Test
  void 처리중_409는_결과_모름이다() {
    server
        .expect(requestTo(BASE + "/v1/billing/bk-1"))
        .andRespond(
            withStatus(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":\"IDEMPOTENT_REQUEST_PROCESSING\",\"message\":\"처리 중\"}"));

    TossPaymentsResult<TossPaymentsPayment> result = approve("bk-1", null);

    assertThat(result).isInstanceOf(TossPaymentsResult.Unknown.class);
    assertThat(recordedCount("approve_billing", "unknown")).isEqualTo(1);
  }

  @Test
  void 본문_없는_409는_결과_모름이다() {
    server.expect(requestTo(BASE + "/v1/billing/bk-1")).andRespond(withStatus(HttpStatus.CONFLICT));

    TossPaymentsResult<TossPaymentsPayment> result = approve("bk-1", null);

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

    TossPaymentsResult<TossPaymentsPayment> result = approve("bk-1", null);

    assertThat(result)
        .isEqualTo(new TossPaymentsResult.Rejected<>(409, "DUPLICATED_ORDER_ID", "이미 쓴 주문번호"));
  }

  @Test
  void 응답_5xx는_결과_모름이다() {
    server
        .expect(requestTo(BASE + "/v1/billing/bk-1"))
        .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

    TossPaymentsResult<TossPaymentsPayment> result = approve("bk-1", null);

    assertThat(result).isInstanceOf(TossPaymentsResult.Unknown.class);
  }

  @Test
  void 타임아웃은_결과_모름이다() {
    server
        .expect(requestTo(BASE + "/v1/billing/bk-1"))
        .andRespond(withException(new SocketTimeoutException("read timed out")));

    TossPaymentsResult<TossPaymentsPayment> result = approve("bk-1", null);

    assertThat(unknownReason(result)).doesNotContain("bk-1");
    assertThat(recordedCount("approve_billing", "unknown")).isEqualTo(1);
  }

  @Test
  void 승인_2xx_본문을_읽지_못하면_결과_모름이다() {
    server
        .expect(requestTo(BASE + "/v1/billing/bk-1"))
        .andRespond(withSuccess("{", MediaType.APPLICATION_JSON));

    TossPaymentsResult<TossPaymentsPayment> result = approve("bk-1", null);

    assertThat(unknownReason(result)).doesNotContain("bk-1");
  }

  @Test
  void 승인_2xx_본문이_비면_결과_모름이다() {
    server.expect(requestTo(BASE + "/v1/billing/bk-1")).andRespond(withSuccess());

    TossPaymentsResult<TossPaymentsPayment> result = approve("bk-1", null);

    assertThat(result).isInstanceOf(TossPaymentsResult.Unknown.class);
  }

  @Test
  void 오류_본문이_JSON이_아니어도_상태로_판정한다() {
    server
        .expect(requestTo(BASE + "/v1/billing/bk-1"))
        .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("<html>bad</html>"));

    TossPaymentsResult<TossPaymentsPayment> result = approve("bk-1", null);

    assertThat(result).isEqualTo(new TossPaymentsResult.Rejected<>(400, null, null));
  }

  @Test
  void 주문번호_조회에_있으면_성공이다() {
    server
        .expect(requestTo(BASE + "/v1/payments/orders/order-000001"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("Authorization", basic(SECRET_KEY)))
        .andRespond(withSuccess(PAYMENT_BODY, MediaType.APPLICATION_JSON));

    TossPaymentsResult<TossPaymentsPayment> result =
        client.findPaymentByOrderId(SECRET_KEY, "order-000001");

    assertThat(success(result))
        .usingRecursiveComparison()
        .withComparatorForType(SAME_INSTANT, OffsetDateTime.class)
        .isEqualTo(PAYMENT);
    assertThat(recordedCount("find_payment_by_order_id", "success")).isEqualTo(1);
  }

  @Test
  void 주문번호_조회에_없으면_404_거절이다() {
    server
        .expect(requestTo(BASE + "/v1/payments/orders/order-000001"))
        .andExpect(method(HttpMethod.GET))
        .andRespond(
            withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"code\":\"NOT_FOUND_PAYMENT\",\"message\":\"없음\"}"));

    TossPaymentsResult<TossPaymentsPayment> result =
        client.findPaymentByOrderId(SECRET_KEY, "order-000001");

    assertThat(result).isEqualTo(new TossPaymentsResult.Rejected<>(404, "NOT_FOUND_PAYMENT", "없음"));
  }

  @Test
  void 결제키_조회에_취소_내역이_없으면_빈_목록이다() {
    server
        .expect(requestTo(BASE + "/v1/payments/pk-1"))
        .andRespond(
            withSuccess(
                """
                {"paymentKey":"pk-1","orderId":"order-000001","status":"DONE",
                 "totalAmount":10000,"balanceAmount":10000,
                 "approvedAt":"2026-10-25T10:00:00+09:00"}
                """,
                MediaType.APPLICATION_JSON));

    TossPaymentsPayment payment = success(client.findPayment(SECRET_KEY, "pk-1"));

    assertThat(payment.cancels()).isEmpty();
  }

  @Test
  void 결제키_조회_2xx에_잔액이_빠지면_결과_모름이다() {
    server
        .expect(requestTo(BASE + "/v1/payments/pk-1"))
        .andRespond(
            withSuccess(
                """
                {"paymentKey":"pk-1","orderId":"order-000001","status":"DONE",
                 "totalAmount":10000,"approvedAt":"2026-10-25T10:00:00+09:00","cancels":null}
                """,
                MediaType.APPLICATION_JSON));

    TossPaymentsResult<TossPaymentsPayment> result = client.findPayment(SECRET_KEY, "pk-1");

    assertThat(result).isInstanceOf(TossPaymentsResult.Unknown.class);
    assertThat(recordedCount("find_payment", "unknown")).isEqualTo(1);
  }

  @Test
  void 결제키_조회는_총액과_잔액과_취소_내역을_읽는다() {
    server
        .expect(requestTo(BASE + "/v1/payments/pk-1"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("Authorization", basic(SECRET_KEY)))
        .andRespond(
            withSuccess(
                """
                {"paymentKey":"pk-1","orderId":"order-000001","status":"PARTIAL_CANCELED",
                 "totalAmount":10000,"balanceAmount":7000,
                 "approvedAt":"2026-10-25T10:00:00+09:00",
                 "cancels":[{"transactionKey":"tx-1","cancelAmount":3000,
                   "canceledAt":"2026-10-25T11:00:00+09:00","cancelStatus":"DONE",
                   "cancelReason":"고객 요청"}]}
                """,
                MediaType.APPLICATION_JSON));

    TossPaymentsPayment payment = success(client.findPayment(SECRET_KEY, "pk-1"));

    assertThat(payment.totalAmount()).isEqualTo(10000);
    assertThat(payment.balanceAmount()).isEqualTo(7000);
    assertThat(payment.cancels())
        .usingRecursiveComparison()
        .withComparatorForType(SAME_INSTANT, OffsetDateTime.class)
        .isEqualTo(
            List.of(
                new TossPaymentsPayment.Cancel(
                    "tx-1", 3000, OffsetDateTime.parse("2026-10-25T11:00:00+09:00"), "DONE")));
    assertThat(recordedCount("find_payment", "success")).isEqualTo(1);
  }

  @Test
  void 취소는_멱등키와_사유와_금액을_보내고_거래키를_읽는다() {
    server
        .expect(requestTo(BASE + "/v1/payments/pk-1/cancel"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("Authorization", basic(SECRET_KEY)))
        .andExpect(header("Idempotency-Key", "cancel-000001"))
        .andExpect(
            content()
                .json("{\"cancelReason\":\"고객 요청\",\"cancelAmount\":3000}", JsonCompareMode.STRICT))
        .andRespond(
            withSuccess(
                """
                {"paymentKey":"pk-1","orderId":"order-000001","status":"PARTIAL_CANCELED",
                 "totalAmount":10000,"balanceAmount":7000,
                 "approvedAt":"2026-10-25T10:00:00+09:00",
                 "cancels":[{"transactionKey":"tx-1","cancelAmount":3000,
                   "canceledAt":"2026-10-25T11:00:00+09:00","cancelStatus":"DONE"}]}
                """,
                MediaType.APPLICATION_JSON));

    TossPaymentsPayment payment =
        success(
            client.cancel(
                SECRET_KEY,
                "pk-1",
                new TossPaymentsCancelRequest("고객 요청", 3000L),
                "cancel-000001"));

    assertThat(payment.cancels().getFirst().transactionKey()).isEqualTo("tx-1");
    assertThat(recordedCount("cancel", "success")).isEqualTo(1);
    server.verify();
  }

  @Test
  void 취소_금액이_없으면_본문에_금액_키를_넣지_않는다() {
    server
        .expect(requestTo(BASE + "/v1/payments/pk-1/cancel"))
        .andExpect(content().json("{\"cancelReason\":\"고객 요청\"}", JsonCompareMode.STRICT))
        .andRespond(withSuccess(PAYMENT_BODY, MediaType.APPLICATION_JSON));

    client.cancel(
        SECRET_KEY, "pk-1", new TossPaymentsCancelRequest("고객 요청", null), "cancel-000001");

    server.verify();
  }

  @Test
  void 거래_목록은_기간과_개수를_쿼리로_보내고_배열을_읽는다() {
    server
        .expect(
            requestTo(
                BASE
                    + "/v1/transactions?startDate=2026-10-25T00:00:00"
                    + "&endDate=2026-10-26T00:00:00&limit=100"))
        .andExpect(method(HttpMethod.GET))
        .andExpect(header("Authorization", basic(SECRET_KEY)))
        .andRespond(
            withSuccess(
                """
                [{"mId":"tvivarepublica","transactionKey":"tx-1","paymentKey":"pk-1",
                  "orderId":"order-000001","method":"카드","status":"DONE",
                  "transactionAt":"2026-10-25T10:00:00+09:00","currency":"KRW","amount":10000},
                 {"mId":"tvivarepublica","transactionKey":"tx-2","paymentKey":"pk-1",
                  "orderId":"order-000001","method":"카드","status":"PARTIAL_CANCELED",
                  "transactionAt":"2026-10-25T11:00:00+09:00","currency":"KRW","amount":3000}]
                """,
                MediaType.APPLICATION_JSON));

    List<TossPaymentsTransaction> transactions =
        success(
            client.listTransactions(
                SECRET_KEY,
                LocalDateTime.parse("2026-10-25T00:00:00"),
                LocalDateTime.parse("2026-10-26T00:00:00"),
                null,
                100));

    assertThat(transactions)
        .usingRecursiveComparison()
        .withComparatorForType(SAME_INSTANT, OffsetDateTime.class)
        .isEqualTo(
            List.of(
                new TossPaymentsTransaction(
                    "tx-1",
                    "pk-1",
                    "order-000001",
                    "DONE",
                    OffsetDateTime.parse("2026-10-25T10:00:00+09:00"),
                    10000),
                new TossPaymentsTransaction(
                    "tx-2",
                    "pk-1",
                    "order-000001",
                    "PARTIAL_CANCELED",
                    OffsetDateTime.parse("2026-10-25T11:00:00+09:00"),
                    3000)));
    assertThat(recordedCount("list_transactions", "success")).isEqualTo(1);
    server.verify();
  }

  @Test
  void 거래_목록에_시작_거래키를_주면_쿼리에_붙인다() {
    server
        .expect(
            requestTo(
                BASE
                    + "/v1/transactions?startDate=2026-10-25T00:00:00"
                    + "&endDate=2026-10-26T00:00:00&startingAfter=tx-2&limit=100"))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    List<TossPaymentsTransaction> transactions =
        success(
            client.listTransactions(
                SECRET_KEY,
                LocalDateTime.parse("2026-10-25T00:00:00"),
                LocalDateTime.parse("2026-10-26T00:00:00"),
                "tx-2",
                100));

    assertThat(transactions).isEmpty();
    server.verify();
  }

  @Test
  void 거래_목록_조회도_외부_호출_지표의_uri에_경로_틀을_남긴다() {
    SimpleMeterRegistry observedMeterRegistry = new SimpleMeterRegistry();
    ObservationRegistry observationRegistry = ObservationRegistry.create();
    observationRegistry
        .observationConfig()
        .observationHandler(new DefaultMeterObservationHandler(observedMeterRegistry));
    RestClient.Builder builder = RestClient.builder().observationRegistry(observationRegistry);
    MockRestServiceServer observedServer = MockRestServiceServer.bindTo(builder).build();
    TossPaymentsConfig.withTossDefaults(builder, new TossPaymentsProperties(URI.create(BASE)));
    observedServer
        .expect(
            requestTo(
                BASE
                    + "/v1/transactions?startDate=2026-10-25T00:00:00"
                    + "&endDate=2026-10-26T00:00:00&limit=100"))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    new TossPaymentsClient(builder.build(), observedMeterRegistry)
        .listTransactions(
            SECRET_KEY,
            LocalDateTime.parse("2026-10-25T00:00:00"),
            LocalDateTime.parse("2026-10-26T00:00:00"),
            null,
            100);

    assertThat(
            observedMeterRegistry
                .get("http.client.requests")
                .tag("uri", "/v1/transactions")
                .timer()
                .count())
        .isEqualTo(1);
    observedServer.verify();
  }

  @Test
  void 빌링키_삭제는_빈_본문_200이면_성공이다() {
    server
        .expect(requestTo(BASE + "/v1/billing/bk-1"))
        .andExpect(method(HttpMethod.DELETE))
        .andExpect(header("Authorization", basic(SECRET_KEY)))
        .andRespond(withSuccess());

    assertThat(client.deleteBillingKey(SECRET_KEY, "bk-1"))
        .isEqualTo(new TossPaymentsResult.Success<Void>(null));
    assertThat(recordedCount("delete_billing_key", "success")).isEqualTo(1);
  }

  @Test
  void 요청_타이머는_오퍼레이션과_결과의_모든_조합을_미리_등록한다() {
    List<String> tagPairs =
        meterRegistry.find(TIMER).timers().stream()
            .filter(timer -> timer.count() == 0)
            .map(timer -> timer.getId().getTag("operation") + "/" + timer.getId().getTag("outcome"))
            .toList();

    List<String> expected =
        List.of(
                "issue_billing_key",
                "approve_billing",
                "delete_billing_key",
                "find_payment_by_order_id",
                "find_payment",
                "cancel",
                "list_transactions")
            .stream()
            .flatMap(
                operation ->
                    List.of("success", "rejected", "unknown").stream()
                        .map(outcome -> operation + "/" + outcome))
            .toList();
    assertThat(tagPairs).containsExactlyInAnyOrderElementsOf(expected);
  }

  private TossPaymentsResult<TossPaymentsPayment> approve(String billingKey, String testCode) {
    return client.approveBilling(
        SECRET_KEY,
        billingKey,
        new TossPaymentsApproveBillingRequest("c", 1, "order-000001", "n"),
        "order-000001",
        testCode);
  }

  private long recordedCount(String operation, String outcome) {
    Timer timer =
        meterRegistry.get(TIMER).tag("operation", operation).tag("outcome", outcome).timer();
    return timer.count();
  }

  private static String basic(String secretKey) {
    return "Basic "
        + Base64.getEncoder().encodeToString((secretKey + ":").getBytes(StandardCharsets.UTF_8));
  }

  private static <T> T success(TossPaymentsResult<T> result) {
    assertThat(result).isInstanceOf(TossPaymentsResult.Success.class);
    return ((TossPaymentsResult.Success<T>) result).value();
  }

  private static <T> String unknownReason(TossPaymentsResult<T> result) {
    assertThat(result).isInstanceOf(TossPaymentsResult.Unknown.class);
    return ((TossPaymentsResult.Unknown<T>) result).reason();
  }
}
