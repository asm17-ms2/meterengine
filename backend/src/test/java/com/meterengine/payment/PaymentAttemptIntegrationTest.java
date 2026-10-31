package com.meterengine.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.global.error.BusinessException;
import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.invoice.entity.Invoice;
import com.meterengine.invoice.repository.InvoiceRepository;
import com.meterengine.payment.client.TossPaymentsApproveBillingRequest;
import com.meterengine.payment.client.TossPaymentsClient;
import com.meterengine.payment.client.TossPaymentsPayment;
import com.meterengine.payment.client.TossPaymentsResult;
import com.meterengine.payment.dto.PaymentAttemptResponse;
import com.meterengine.payment.entity.BillingKey;
import com.meterengine.payment.entity.TossPaymentsCredential;
import com.meterengine.payment.repository.BillingKeyRepository;
import com.meterengine.payment.repository.TossPaymentsCredentialRepository;
import com.meterengine.payment.service.PaymentAttemptService;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PaymentAttemptIntegrationTest {

  private static final String TEST_CLIENT_KEY = "test_ck_D5GePWvyJnrK0W0k6q8gLzN97Eoq";
  private static final String TEST_SECRET_KEY = "test_sk_zXLkKEypNArWmo50nX3lmeaxYG5R";
  private static final String LIVE_CLIENT_KEY = "live_ck_Ba5PzR0ArnGq1xZ7k6EbVgvLQ2DY";
  private static final String LIVE_SECRET_KEY = "live_sk_Wd8ZnYkP2Lq4Xj6Bv0Mr3Ts5Ue7Ia9Oc";
  private static final String TEST_BILLING_KEY = "testBillingKeyIssuedByToss0001";
  private static final String LIVE_BILLING_KEY = "liveBillingKeyIssuedByToss0001";
  private static final String PAYMENT_KEY = "tgen_20261026101500AbCd1";
  private static final String LIVE_PAYMENT_KEY = "tviva20261026101500EfGh2";
  private static final String TEST_CODE_HEADER = "TossPayments-Test-Code";

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private TossPaymentsCredentialRepository tossPaymentsCredentialRepository;
  @Autowired private BillingKeyRepository billingKeyRepository;
  @Autowired private PaymentAttemptService paymentAttemptService;
  @Autowired private MeterRegistry meterRegistry;

  @MockitoBean private TossPaymentsClient tossPaymentsClient;

  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
  }

  @Test
  void 토스가_승인하면_201_done이고_payment_key와_mode가_남는다() {
    Payable payable = payableInvoice("test");
    tossReturns(approved(PAYMENT_KEY));

    MvcTestResult result = createPaymentAttempt(payable.organizationId(), payable.invoiceId());

    assertThat(result).hasStatus(201);
    Map<String, Object> attempt = onlyAttemptRow(payable.invoiceId());
    assertThat(attempt)
        .containsEntry("customer_id", payable.customerId())
        .containsEntry("mode", "test")
        .containsEntry("amount", 13200L)
        .containsEntry("order_name", "2026-08 사용료")
        .containsEntry("status", "done")
        .containsEntry("payment_key", PAYMENT_KEY)
        .containsEntry("failure_code", null);
    assertThat(result)
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {
              "id": "%s", "invoice_id": "%s", "mode": "test", "status": "done", "amount": 13200,
              "payment_key": "%s", "failure_code": null, "failure_message": null,
              "requested_at": "%s", "completed_at": "%s"
            }
            """
                .formatted(
                    attempt.get("id"),
                    payable.invoiceId(),
                    PAYMENT_KEY,
                    kst(attempt.get("requested_at")),
                    kst(attempt.get("completed_at"))));
  }

  @Test
  void 토스가_거절하면_201_failed이고_토스의_code와_message가_남는다() {
    Payable payable = payableInvoice("test");
    tossReturns(new TossPaymentsResult.Rejected<>(403, "REJECT_CARD_PAYMENT", "한도초과"));
    double failedBefore = attemptCount("test", "failed");

    MvcTestResult result = createPaymentAttempt(payable.organizationId(), payable.invoiceId());

    assertThat(result).hasStatus(201);
    assertThat(attemptCount("test", "failed")).isEqualTo(failedBefore + 1);
    Map<String, Object> attempt = onlyAttemptRow(payable.invoiceId());
    assertThat(attempt)
        .containsEntry("status", "failed")
        .containsEntry("payment_key", null)
        .containsEntry("failure_code", "REJECT_CARD_PAYMENT")
        .containsEntry("failure_message", "한도초과");
    assertThat(attempt.get("completed_at")).isNotNull();
    assertThat(result)
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {
              "id": "%s", "invoice_id": "%s", "mode": "test", "status": "failed", "amount": 13200,
              "payment_key": null, "failure_code": "REJECT_CARD_PAYMENT", "failure_message": "한도초과",
              "requested_at": "%s", "completed_at": "%s"
            }
            """
                .formatted(
                    attempt.get("id"),
                    payable.invoiceId(),
                    kst(attempt.get("requested_at")),
                    kst(attempt.get("completed_at"))));
  }

  @Test
  void 거절에_토스_code가_없으면_HTTP_상태로_failure_code를_남기고_여러_줄_message는_그대로_둔다() {
    Payable payable = payableInvoice("test");
    tossReturns(new TossPaymentsResult.Rejected<>(400, null, "첫 줄\n둘째 줄"));

    assertThat(createPaymentAttempt(payable.organizationId(), payable.invoiceId())).hasStatus(201);

    assertThat(onlyAttemptRow(payable.invoiceId()))
        .containsEntry("status", "failed")
        .containsEntry("failure_code", "HTTP_400")
        .containsEntry("failure_message", "첫 줄\n둘째 줄");
  }

  @Test
  void 토스_결과를_알_수_없으면_201_pending이고_시도는_pending으로_남는다() {
    Payable payable = payableInvoice("test");
    tossReturns(new TossPaymentsResult.Unknown<>("HttpTimeoutException: request timed out"));
    double unknownBefore = attemptCount("test", "unknown");

    MvcTestResult result = createPaymentAttempt(payable.organizationId(), payable.invoiceId());

    assertThat(result).hasStatus(201);
    assertThat(attemptCount("test", "unknown")).isEqualTo(unknownBefore + 1);
    Map<String, Object> attempt = onlyAttemptRow(payable.invoiceId());
    assertThat(attempt)
        .containsEntry("status", "pending")
        .containsEntry("payment_key", null)
        .containsEntry("completed_at", null);
    assertThat(result)
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {
              "id": "%s", "invoice_id": "%s", "mode": "test", "status": "pending", "amount": 13200,
              "payment_key": null, "failure_code": null, "failure_message": null,
              "requested_at": "%s", "completed_at": null
            }
            """
                .formatted(
                    attempt.get("id"), payable.invoiceId(), kst(attempt.get("requested_at"))));
  }

  @Test
  void 토스를_부르는_동안_시도는_다른_커넥션에서_pending으로_보이고_트랜잭션_밖이다() {
    Payable payable = payableInvoice("test");
    List<String> statusesDuringCall = new ArrayList<>();
    List<Boolean> transactionActiveDuringCall = new ArrayList<>();
    when(tossPaymentsClient.approveBilling(anyString(), anyString(), any(), anyString(), any()))
        .thenAnswer(
            invocation -> {
              transactionActiveDuringCall.add(
                  TransactionSynchronizationManager.isActualTransactionActive());
              statusesDuringCall.addAll(
                  jdbcTemplate.queryForList(
                      "SELECT status FROM payment_attempt WHERE invoice_id = ?",
                      String.class,
                      payable.invoiceId()));
              return approved(PAYMENT_KEY);
            });

    assertThat(createPaymentAttempt(payable.organizationId(), payable.invoiceId())).hasStatus(201);

    assertThat(statusesDuringCall).containsExactly("pending");
    assertThat(transactionActiveDuringCall).containsExactly(false);
    assertThat(onlyAttemptRow(payable.invoiceId())).containsEntry("status", "done");
  }

  @Test
  void 토스에_시도_id를_orderId와_Idempotency_Key로_넘기고_테스트_코드를_그대로_넘긴다() {
    Payable payable = payableInvoice("test");
    tossReturns(new TossPaymentsResult.Rejected<>(403, "REJECT_CARD_PAYMENT", "한도초과"));

    assertThat(
            mvc.post()
                .uri("/v1/invoices/{id}/payment-attempts", payable.invoiceId())
                .header(
                    "Authorization",
                    TestCredentials.issueMemberSession(jdbcTemplate, payable.organizationId()))
                .header(TEST_CODE_HEADER, "REJECT_CARD_PAYMENT")
                .exchange())
        .hasStatus(201);

    String attemptId = onlyAttemptRow(payable.invoiceId()).get("id").toString();
    verify(tossPaymentsClient, times(1))
        .approveBilling(
            TEST_SECRET_KEY,
            TEST_BILLING_KEY,
            new TossPaymentsApproveBillingRequest(
                payable.customerId().toString(), 13200L, attemptId, "2026-08 사용료"),
            attemptId,
            "REJECT_CARD_PAYMENT");
  }

  @Test
  void 토스가_성공을_주어도_DONE이_아니거나_payment_key가_없으면_201_pending이고_시도는_pending으로_남는다() {
    Payable notDone = payableInvoice("test");
    Payable withoutPaymentKey = payableInvoice("test");
    double unknownBefore = attemptCount("test", "unknown");

    tossReturns(payment(PAYMENT_KEY, "IN_PROGRESS"));
    MvcTestResult notDoneResult =
        createPaymentAttempt(notDone.organizationId(), notDone.invoiceId());
    tossReturns(payment(null, "DONE"));
    MvcTestResult withoutPaymentKeyResult =
        createPaymentAttempt(withoutPaymentKey.organizationId(), withoutPaymentKey.invoiceId());

    assertThat(notDoneResult)
        .hasStatus(201)
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo("pending");
    assertThat(withoutPaymentKeyResult)
        .hasStatus(201)
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo("pending");
    assertThat(onlyAttemptRow(notDone.invoiceId()))
        .containsEntry("status", "pending")
        .containsEntry("payment_key", null);
    assertThat(onlyAttemptRow(withoutPaymentKey.invoiceId()))
        .containsEntry("status", "pending")
        .containsEntry("payment_key", null);
    assertThat(attemptCount("test", "unknown")).isEqualTo(unknownBefore + 2);
  }

  @Test
  void live_모드에서_빈_테스트_코드_헤더는_보내지_않은_것으로_보고_토스에_null을_넘긴다() {
    Payable payable = payableInvoice("live");
    tossReturns(approved(LIVE_PAYMENT_KEY));

    assertThat(
            mvc.post()
                .uri("/v1/invoices/{id}/payment-attempts", payable.invoiceId())
                .header(
                    "Authorization",
                    TestCredentials.issueMemberSession(jdbcTemplate, payable.organizationId()))
                .header(TEST_CODE_HEADER, "")
                .exchange())
        .hasStatus(201)
        .bodyJson()
        .extractingPath("$.status")
        .isEqualTo("done");

    verify(tossPaymentsClient)
        .approveBilling(eq(LIVE_SECRET_KEY), eq(LIVE_BILLING_KEY), any(), anyString(), isNull());
  }

  @Test
  void live_모드에서_테스트_코드를_보내면_400이고_시도를_남기지_않는다() {
    Payable payable = payableInvoice("live");

    assertError(
        mvc.post()
            .uri("/v1/invoices/{id}/payment-attempts", payable.invoiceId())
            .header(
                "Authorization",
                TestCredentials.issueMemberSession(jdbcTemplate, payable.organizationId()))
            .header(TEST_CODE_HEADER, "REJECT_CARD_PAYMENT")
            .exchange(),
        400,
        ErrorCode.TOSS_PAYMENTS_TEST_CODE_IN_LIVE_MODE);

    assertThat(countAttempts(payable.invoiceId())).isZero();
    verifyNoInteractions(tossPaymentsClient);
  }

  @Test
  void 결제_모드의_키가_없거나_카드가_없으면_409이고_시도를_남기지_않는다() {
    UUID withoutCredential = insertOrganization();
    UUID customerWithoutCredential = insertCustomer(withoutCredential);
    insertBillingKey(withoutCredential, customerWithoutCredential, "test");
    UUID invoiceWithoutCredential =
        insertInvoice(withoutCredential, customerWithoutCredential, 12000);

    UUID withoutCard = insertOrganization();
    UUID customerWithoutCard = insertCustomer(withoutCard);
    insertCredential(withoutCard, "test");
    insertBillingKey(withoutCard, customerWithoutCard, "live");
    UUID invoiceWithoutCard = insertInvoice(withoutCard, customerWithoutCard, 12000);

    assertError(
        createPaymentAttempt(withoutCredential, invoiceWithoutCredential),
        409,
        ErrorCode.TOSS_PAYMENTS_CREDENTIAL_MISSING);
    assertError(
        createPaymentAttempt(withoutCard, invoiceWithoutCard),
        409,
        ErrorCode.PAYMENT_METHOD_MISSING);

    assertThat(countAttempts(invoiceWithoutCredential)).isZero();
    assertThat(countAttempts(invoiceWithoutCard)).isZero();
    verifyNoInteractions(tossPaymentsClient);
  }

  @Test
  void 보관한_카드나_보관한_결제_모드의_키로는_409이고_시도를_남기지_않으며_토스를_부르지_않는다() {
    Payable archivedCard = payableInvoice("test");
    jdbcTemplate.update(
        "UPDATE billing_key SET archived_at = now() WHERE organization_id = ?",
        archivedCard.organizationId());
    Payable archivedCredential = payableInvoice("test");
    jdbcTemplate.update(
        "UPDATE toss_payments_credential SET archived_at = now() WHERE organization_id = ?",
        archivedCredential.organizationId());

    assertError(
        createPaymentAttempt(archivedCard.organizationId(), archivedCard.invoiceId()),
        409,
        ErrorCode.PAYMENT_METHOD_MISSING);
    assertError(
        createPaymentAttempt(archivedCredential.organizationId(), archivedCredential.invoiceId()),
        409,
        ErrorCode.TOSS_PAYMENTS_CREDENTIAL_MISSING);

    assertThat(countAttempts(archivedCard.invoiceId())).isZero();
    assertThat(countAttempts(archivedCredential.invoiceId())).isZero();
    verifyNoInteractions(tossPaymentsClient);
  }

  @Test
  void test_모드에서_결제한_인보이스도_live_모드로_바꾸면_live_키와_카드로_결제할_수_있다() {
    Payable payable = payableInvoice("test");
    tossReturns(approved(PAYMENT_KEY));
    assertThat(createPaymentAttempt(payable.organizationId(), payable.invoiceId())).hasStatus(201);
    insertCredential(payable.organizationId(), "live");
    insertBillingKey(payable.organizationId(), payable.customerId(), "live");
    switchToLive(payable.organizationId());
    tossReturns(approved(LIVE_PAYMENT_KEY));

    MvcTestResult live = createPaymentAttempt(payable.organizationId(), payable.invoiceId());

    assertThat(live).hasStatus(201).bodyJson().extractingPath("$.mode").isEqualTo("live");
    assertThat(live).bodyJson().extractingPath("$.status").isEqualTo("done");
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT mode || ':' || status FROM payment_attempt WHERE invoice_id = ?",
                String.class,
                payable.invoiceId()))
        .containsExactlyInAnyOrder("test:done", "live:done");
    verify(tossPaymentsClient)
        .approveBilling(eq(LIVE_SECRET_KEY), eq(LIVE_BILLING_KEY), any(), anyString(), isNull());
  }

  @Test
  void 같은_인보이스에_동시에_요청하면_시도는_하나이고_나머지는_409나_500이다() throws Exception {
    Payable payable = payableInvoice("test");
    CountDownLatch tossCalled = new CountDownLatch(1);
    CountDownLatch tossResponds = new CountDownLatch(1);
    when(tossPaymentsClient.approveBilling(anyString(), anyString(), any(), anyString(), any()))
        .thenAnswer(
            invocation -> {
              tossCalled.countDown();
              assertThat(tossResponds.await(10, TimeUnit.SECONDS)).isTrue();
              return approved(PAYMENT_KEY);
            });
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      CompletionService<Integer> requests = new ExecutorCompletionService<>(executor);
      for (int i = 0; i < 2; i++) {
        requests.submit(
            () -> {
              start.await();
              return createPaymentAttempt(payable.organizationId(), payable.invoiceId())
                  .getResponse()
                  .getStatus();
            });
      }
      start.countDown();

      assertThat(tossCalled.await(10, TimeUnit.SECONDS)).isTrue();
      int loser = requests.poll(10, TimeUnit.SECONDS).get();
      tossResponds.countDown();
      int winner = requests.poll(10, TimeUnit.SECONDS).get();

      assertThat(loser).isIn(409, 500);
      assertThat(winner).isEqualTo(201);
    } finally {
      tossResponds.countDown();
      executor.shutdownNow();
    }
    assertThat(countAttempts(payable.invoiceId())).isEqualTo(1);
    verify(tossPaymentsClient, times(1))
        .approveBilling(anyString(), anyString(), any(), anyString(), any());
  }

  @Test
  void 인증_주체의_모드가_결제_모드와_다르면_409이고_주체_모드가_없으면_통과한다() {
    Payable payable = payableInvoice("test");
    tossReturns(approved(PAYMENT_KEY));

    assertThatThrownBy(
            () ->
                paymentAttemptService.create(
                    payable.organizationId(), payable.invoiceId(), "live", null))
        .isInstanceOf(ConflictException.class)
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(ErrorCode.PAYMENT_MODE_MISMATCH);
    assertThat(countAttempts(payable.invoiceId())).isZero();

    PaymentAttemptResponse response =
        paymentAttemptService.create(payable.organizationId(), payable.invoiceId(), null, null);

    assertThat(response.status()).isEqualTo("done");
    assertThat(response.mode()).isEqualTo("test");
  }

  @Test
  void 목록은_모든_모드의_시도를_mode와_failure_code와_함께_최신순으로_돌려준다() {
    Payable payable = payableInvoice("test");
    tossReturns(new TossPaymentsResult.Rejected<>(403, "REJECT_CARD_PAYMENT", "한도초과"));
    assertThat(createPaymentAttempt(payable.organizationId(), payable.invoiceId())).hasStatus(201);
    tossReturns(approved(PAYMENT_KEY));
    assertThat(createPaymentAttempt(payable.organizationId(), payable.invoiceId())).hasStatus(201);
    insertCredential(payable.organizationId(), "live");
    insertBillingKey(payable.organizationId(), payable.customerId(), "live");
    switchToLive(payable.organizationId());
    tossReturns(new TossPaymentsResult.Unknown<>("HttpTimeoutException: request timed out"));
    assertThat(createPaymentAttempt(payable.organizationId(), payable.invoiceId())).hasStatus(201);

    MvcTestResult listed = listPaymentAttempts(payable.organizationId(), payable.invoiceId());

    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            "SELECT * FROM payment_attempt WHERE invoice_id = ? ORDER BY requested_at DESC",
            payable.invoiceId());
    assertThat(rows).hasSize(3);
    assertThat(listed)
        .hasStatus(200)
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {"payment_attempts": [
              {
                "id": "%s", "invoice_id": "%s", "mode": "live", "status": "pending", "amount": 13200,
                "payment_key": null, "failure_code": null, "failure_message": null,
                "requested_at": "%s", "completed_at": null
              },
              {
                "id": "%s", "invoice_id": "%s", "mode": "test", "status": "done", "amount": 13200,
                "payment_key": "%s", "failure_code": null, "failure_message": null,
                "requested_at": "%s", "completed_at": "%s"
              },
              {
                "id": "%s", "invoice_id": "%s", "mode": "test", "status": "failed", "amount": 13200,
                "payment_key": null, "failure_code": "REJECT_CARD_PAYMENT", "failure_message": "한도초과",
                "requested_at": "%s", "completed_at": "%s"
              }
            ]}
            """
                .formatted(
                    rows.get(0).get("id"),
                    payable.invoiceId(),
                    kst(rows.get(0).get("requested_at")),
                    rows.get(1).get("id"),
                    payable.invoiceId(),
                    PAYMENT_KEY,
                    kst(rows.get(1).get("requested_at")),
                    kst(rows.get(1).get("completed_at")),
                    rows.get(2).get("id"),
                    payable.invoiceId(),
                    kst(rows.get(2).get("requested_at")),
                    kst(rows.get(2).get("completed_at"))));
  }

  @Test
  void 목록은_없는_인보이스나_다른_도입사의_인보이스면_404다() {
    UUID organizationId = insertOrganization();
    UUID otherOrganizationId = insertOrganization();
    UUID otherInvoiceId =
        insertInvoice(otherOrganizationId, insertCustomer(otherOrganizationId), 12000);

    assertError(
        listPaymentAttempts(organizationId, UUID.randomUUID()), 404, ErrorCode.INVOICE_NOT_FOUND);
    assertError(
        listPaymentAttempts(organizationId, otherInvoiceId), 404, ErrorCode.INVOICE_NOT_FOUND);
  }

  @Test
  void 결제한_인보이스에_다시_요청하면_409이고_시도는_하나만_남는다() {
    Payable payable = payableInvoice("test");
    tossReturns(approved(PAYMENT_KEY));
    assertThat(createPaymentAttempt(payable.organizationId(), payable.invoiceId())).hasStatus(201);

    assertError(
        createPaymentAttempt(payable.organizationId(), payable.invoiceId()),
        409,
        ErrorCode.INVOICE_ALREADY_PAID);
    assertThat(countAttempts(payable.invoiceId())).isEqualTo(1);
    verify(tossPaymentsClient, times(1))
        .approveBilling(anyString(), anyString(), any(), anyString(), any());
  }

  @Test
  void pending_시도에_다시_요청하면_기존_409_invoice_already_paid이고_추가_승인을_하지_않는다() {
    Payable payable = payableInvoice("test");
    tossReturns(new TossPaymentsResult.Unknown<>("응답 없음"));
    assertThat(createPaymentAttempt(payable.organizationId(), payable.invoiceId())).hasStatus(201);

    assertError(
        createPaymentAttempt(payable.organizationId(), payable.invoiceId()),
        409,
        ErrorCode.INVOICE_ALREADY_PAID);
    assertThat(onlyAttemptRow(payable.invoiceId())).containsEntry("status", "pending");
    assertThat(countAttempts(payable.invoiceId())).isEqualTo(1);
    verify(tossPaymentsClient).approveBilling(anyString(), anyString(), any(), anyString(), any());
  }

  @Test
  void 합계가_0원인_인보이스는_409이고_시도를_남기지_않는다() {
    UUID organizationId = insertOrganization();
    UUID customerId = insertCustomer(organizationId);
    insertCredential(organizationId, "test");
    insertBillingKey(organizationId, customerId, "test");
    UUID invoiceId = insertInvoice(organizationId, customerId, 0);

    assertError(
        createPaymentAttempt(organizationId, invoiceId), 409, ErrorCode.INVOICE_AMOUNT_ZERO);
    assertThat(countAttempts(invoiceId)).isZero();
    verifyNoInteractions(tossPaymentsClient);
  }

  @Test
  void 없는_인보이스나_다른_도입사의_인보이스는_404다() {
    UUID organizationId = insertOrganization();
    UUID otherOrganizationId = insertOrganization();
    UUID otherInvoiceId =
        insertInvoice(otherOrganizationId, insertCustomer(otherOrganizationId), 12000);

    assertError(
        createPaymentAttempt(organizationId, UUID.randomUUID()), 404, ErrorCode.INVOICE_NOT_FOUND);
    assertError(
        createPaymentAttempt(organizationId, otherInvoiceId), 404, ErrorCode.INVOICE_NOT_FOUND);
    assertThat(countAttempts(otherInvoiceId)).isZero();
  }

  @Test
  void 인증이_없으면_401이고_인보이스_id가_UUID가_아니면_400이다() {
    assertThat(mvc.post().uri("/v1/invoices/" + UUID.randomUUID() + "/payment-attempts").exchange())
        .hasStatus(401);
    assertError(
        mvc.post()
            .uri("/v1/invoices/not-a-uuid/payment-attempts")
            .header(
                "Authorization",
                TestCredentials.issueMemberSession(jdbcTemplate, UUID.randomUUID()))
            .exchange(),
        400,
        ErrorCode.VALIDATION_ERROR);
  }

  @Test
  void 테스트와_실제_읽기_키는_마스킹된_카드와_수납_정보를_조회한다() throws Exception {
    Payable payable = payableInvoice("test");
    for (String mode : List.of("test", "live")) {
      String authorization =
          TestCredentials.issueApiKey(jdbcTemplate, payable.organizationId(), mode, "read");
      MvcTestResult payments =
          mvc.get().uri("/v1/invoice-payments").header("Authorization", authorization).exchange();
      assertThat(payments)
          .hasStatus(200)
          .bodyJson()
          .extractingPath("$.invoice_payments[0].invoice_id")
          .isEqualTo(payable.invoiceId().toString());
      MvcTestResult methods =
          mvc.get().uri("/v1/payment-methods").header("Authorization", authorization).exchange();
      assertThat(methods).hasStatus(200);
      assertThat(methods.getResponse().getContentAsString()).doesNotContain(TEST_BILLING_KEY);
      MvcTestResult credentials =
          mvc.get()
              .uri("/v1/toss-payments-credentials")
              .header("Authorization", authorization)
              .exchange();
      assertThat(credentials).hasStatus(200);
      assertThat(credentials.getResponse().getContentAsString()).doesNotContain(TEST_SECRET_KEY);
      assertThat(
              mvc.get()
                  .uri("/v1/payment-setting")
                  .header("Authorization", authorization)
                  .exchange())
          .hasStatus(200);
      assertThat(
              mvc.get()
                  .uri("/v1/invoices/{id}", payable.invoiceId())
                  .header("Authorization", authorization)
                  .exchange())
          .hasStatus(200);
    }
  }

  @Test
  void 읽기_키는_결제를_요청할_수_없다() {
    Payable payable = payableInvoice("test");

    assertError(
        mvc.post()
            .uri("/v1/invoices/{id}/payment-attempts", payable.invoiceId())
            .header(
                "Authorization",
                TestCredentials.issueApiKey(jdbcTemplate, payable.organizationId(), "test", "read"))
            .exchange(),
        403,
        ErrorCode.PERMISSION_DENIED);
    assertThat(countAttempts(payable.invoiceId())).isZero();
    verifyNoInteractions(tossPaymentsClient);
  }

  @Test
  void 테스트_키는_실제_모드_설정으로_결제를_요청할_수_없다() {
    Payable payable = payableInvoice("live");

    assertError(
        mvc.post()
            .uri("/v1/invoices/{id}/payment-attempts", payable.invoiceId())
            .header(
                "Authorization",
                TestCredentials.issueApiKey(
                    jdbcTemplate, payable.organizationId(), "test", "admin"))
            .exchange(),
        409,
        ErrorCode.PAYMENT_MODE_MISMATCH);
    assertThat(countAttempts(payable.invoiceId())).isZero();
    verifyNoInteractions(tossPaymentsClient);
  }

  @Test
  void 결제_요청의_감사는_선기록과_함께_남는다() {
    Payable payable = payableInvoice("test");
    tossReturns(new TossPaymentsResult.Unknown<>("timeout"));

    MvcTestResult result = createPaymentAttempt(payable.organizationId(), payable.invoiceId());

    assertThat(result).hasStatus(201);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE organization_id = ? AND action = 'payment_attempt_created' AND target_id = ?",
                Long.class,
                payable.organizationId(),
                onlyAttemptRow(payable.invoiceId()).get("id").toString()))
        .isEqualTo(1L);
  }

  @Test
  void 수동_생성은_NOT_FOUND_PAYMENT_실패의_이전_주문이_DONE이면_409이고_추가_승인을_하지_않는다() {
    Payable payable = payableInvoice("test");
    UUID failedAttemptId =
        insertFailedAttempt(payable, "NOT_FOUND_PAYMENT", OffsetDateTime.now().minusHours(25));
    lookupReturns(
        failedAttemptId,
        new TossPaymentsResult.Success<>(
            new TossPaymentsPayment(
                PAYMENT_KEY, failedAttemptId.toString(), "DONE", 13200, 13200, List.of())));

    assertManualRetryBlocked(payable, 1);
  }

  @Test
  void 수동_생성은_미승인이_확정된_ABORTED나_EXPIRED_주문이면_새로_승인한다() {
    for (String status : List.of("ABORTED", "EXPIRED")) {
      Payable payable = payableInvoice("test");
      UUID failedAttemptId =
          insertFailedAttempt(payable, "NOT_FOUND_PAYMENT", OffsetDateTime.now().minusHours(25));
      lookupReturns(
          failedAttemptId,
          new TossPaymentsResult.Success<>(
              new TossPaymentsPayment(
                  null, failedAttemptId.toString(), status, 13200, 13200, List.of())));
      tossReturns(approved(PAYMENT_KEY));

      assertThat(createPaymentAttempt(payable.organizationId(), payable.invoiceId()))
          .hasStatus(201);
      assertThat(countAttempts(payable.invoiceId())).isEqualTo(2);
    }
    verify(tossPaymentsClient, times(2))
        .approveBilling(anyString(), anyString(), any(), anyString(), any());
  }

  @Test
  void 수동_생성은_변경_없는_키의_404면_재조회와_새_승인을_트랜잭션_밖에서_한다() {
    Payable payable = payableInvoice("test");
    UUID failedAttemptId =
        insertFailedAttempt(payable, "NOT_FOUND_PAYMENT", OffsetDateTime.now().minusHours(25));
    when(tossPaymentsClient.findPaymentByOrderId(TEST_SECRET_KEY, failedAttemptId.toString()))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              assertThat(countAttempts(payable.invoiceId())).isEqualTo(1);
              return new TossPaymentsResult.Rejected<>(404, "NOT_FOUND_PAYMENT", "없는 결제");
            });
    when(tossPaymentsClient.approveBilling(anyString(), anyString(), any(), anyString(), any()))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              assertThat(countAttempts(payable.invoiceId())).isEqualTo(2);
              return approved(PAYMENT_KEY);
            });

    assertThat(createPaymentAttempt(payable.organizationId(), payable.invoiceId())).hasStatus(201);
    assertThat(countAttempts(payable.invoiceId())).isEqualTo(2);
    verify(tossPaymentsClient).approveBilling(anyString(), anyString(), any(), anyString(), any());
  }

  @Test
  void 수동_생성은_조회_결과_모름이나_거절이나_다른_주문이나_취소된_주문이면_409다() {
    List<TossPaymentsResult<TossPaymentsPayment>> results =
        List.of(
            new TossPaymentsResult.Unknown<>("응답 없음"),
            new TossPaymentsResult.Rejected<>(401, "UNAUTHORIZED_KEY", "인증되지 않은 키"),
            new TossPaymentsResult.Rejected<>(404, "OTHER_ERROR", "다른 오류"),
            new TossPaymentsResult.Success<>(
                new TossPaymentsPayment(null, "other-order", "ABORTED", 13200, 13200, List.of())));
    for (TossPaymentsResult<TossPaymentsPayment> result : results) {
      Payable payable = payableInvoice("test");
      UUID failedAttemptId =
          insertFailedAttempt(payable, "NOT_FOUND_PAYMENT", OffsetDateTime.now().minusHours(25));
      lookupReturns(failedAttemptId, result);

      assertManualRetryBlocked(payable, 1);
    }
    Payable canceled = payableInvoice("test");
    UUID canceledAttemptId =
        insertFailedAttempt(canceled, "NOT_FOUND_PAYMENT", OffsetDateTime.now().minusHours(25));
    lookupReturns(
        canceledAttemptId,
        new TossPaymentsResult.Success<>(
            new TossPaymentsPayment(
                PAYMENT_KEY, canceledAttemptId.toString(), "CANCELED", 13200, 0, List.of())));

    assertManualRetryBlocked(canceled, 1);
  }

  @Test
  void 수동_생성은_실패_뒤에_키가_바뀌었거나_조회_도중_키가_바뀌면_404여도_409다() {
    for (boolean duringLookup : List.of(false, true)) {
      Payable payable = payableInvoice("test");
      UUID failedAttemptId =
          insertFailedAttempt(payable, "NOT_FOUND_PAYMENT", OffsetDateTime.now().minusHours(25));
      if (!duringLookup) {
        changeCredentialTimestamp(payable.organizationId());
      }
      when(tossPaymentsClient.findPaymentByOrderId(TEST_SECRET_KEY, failedAttemptId.toString()))
          .thenAnswer(
              invocation -> {
                if (duringLookup) {
                  changeCredentialTimestamp(payable.organizationId());
                }
                return new TossPaymentsResult.Rejected<>(404, "NOT_FOUND_PAYMENT", "없는 결제");
              });

      assertManualRetryBlocked(payable, 1);
    }
  }

  @Test
  void 수동_생성은_더_최근_실패가_있어도_이전_NOT_FOUND_PAYMENT_주문의_DONE을_확인한다() {
    Payable payable = payableInvoice("test");
    UUID failedAttemptId =
        insertFailedAttempt(payable, "NOT_FOUND_PAYMENT", OffsetDateTime.now().minusHours(25));
    insertFailedAttempt(payable, "REJECT_CARD_PAYMENT", OffsetDateTime.now().minusHours(24));
    lookupReturns(
        failedAttemptId,
        new TossPaymentsResult.Success<>(
            new TossPaymentsPayment(
                PAYMENT_KEY, failedAttemptId.toString(), "DONE", 13200, 13200, List.of())));

    assertManualRetryBlocked(payable, 2);
  }

  @Test
  void 수동_생성은_재조회_도중_NOT_FOUND_PAYMENT_실패가_추가되면_409다() {
    Payable payable = payableInvoice("test");
    UUID failedAttemptId =
        insertFailedAttempt(payable, "NOT_FOUND_PAYMENT", OffsetDateTime.now().minusHours(25));
    when(tossPaymentsClient.findPaymentByOrderId(TEST_SECRET_KEY, failedAttemptId.toString()))
        .thenAnswer(
            invocation -> {
              insertFailedAttempt(
                  payable, "NOT_FOUND_PAYMENT", OffsetDateTime.now().minusHours(24));
              return new TossPaymentsResult.Rejected<>(404, "NOT_FOUND_PAYMENT", "없는 결제");
            });

    assertManualRetryBlocked(payable, 2);
  }

  @Test
  void 수동_생성은_NOT_FOUND_PAYMENT_실패가_7일을_넘으면_409이고_토스를_부르지_않는다() {
    Payable payable = payableInvoice("test");
    insertFailedAttempt(payable, "NOT_FOUND_PAYMENT", OffsetDateTime.now().minusDays(8));

    assertManualRetryBlocked(payable, 1);
    verifyNoInteractions(tossPaymentsClient);
  }

  @Test
  void 수동_생성은_더_새_시도가_있는_NOT_FOUND_PAYMENT_실패면_7일이_지나도_다시_조회해_새로_승인한다() {
    Payable payable = payableInvoice("test");
    UUID notFoundAttemptId =
        insertFailedAttempt(payable, "NOT_FOUND_PAYMENT", OffsetDateTime.now().minusDays(8));
    insertFailedAttempt(payable, "REJECT_CARD_PAYMENT", OffsetDateTime.now().minusDays(7));
    lookupReturns(
        notFoundAttemptId, new TossPaymentsResult.Rejected<>(404, "NOT_FOUND_PAYMENT", "없는 결제"));
    tossReturns(approved(PAYMENT_KEY));

    assertThat(createPaymentAttempt(payable.organizationId(), payable.invoiceId())).hasStatus(201);
    assertThat(countAttempts(payable.invoiceId())).isEqualTo(3);
    verify(tossPaymentsClient).findPaymentByOrderId(TEST_SECRET_KEY, notFoundAttemptId.toString());
    verify(tossPaymentsClient).approveBilling(anyString(), anyString(), any(), anyString(), any());
  }

  @Test
  void 수동_생성은_더_새_시도가_있는_NOT_FOUND_PAYMENT_실패_뒤에_키가_바뀌었으면_다시_조회하지_않고_새로_승인한다() {
    Payable payable = payableInvoice("test");
    insertFailedAttempt(payable, "NOT_FOUND_PAYMENT", OffsetDateTime.now().minusDays(8));
    insertFailedAttempt(payable, "REJECT_CARD_PAYMENT", OffsetDateTime.now().minusDays(7));
    changeCredentialTimestamp(payable.organizationId());
    tossReturns(approved(PAYMENT_KEY));

    assertThat(createPaymentAttempt(payable.organizationId(), payable.invoiceId())).hasStatus(201);
    assertThat(countAttempts(payable.invoiceId())).isEqualTo(3);
    verify(tossPaymentsClient, never()).findPaymentByOrderId(anyString(), anyString());
    verify(tossPaymentsClient).approveBilling(anyString(), anyString(), any(), anyString(), any());
  }

  @Test
  void 같은_NOT_FOUND_PAYMENT_실패를_동시에_수동_재조회해도_새_승인은_하나만_한다() throws Exception {
    Payable payable = payableInvoice("test");
    UUID failedAttemptId =
        insertFailedAttempt(payable, "NOT_FOUND_PAYMENT", OffsetDateTime.now().minusHours(25));
    CountDownLatch lookups = new CountDownLatch(2);
    when(tossPaymentsClient.findPaymentByOrderId(TEST_SECRET_KEY, failedAttemptId.toString()))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              lookups.countDown();
              assertThat(lookups.await(10, TimeUnit.SECONDS)).isTrue();
              return new TossPaymentsResult.Rejected<>(404, "NOT_FOUND_PAYMENT", "없는 결제");
            });
    tossReturns(approved(PAYMENT_KEY));
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      CompletionService<MvcTestResult> requests = new ExecutorCompletionService<>(executor);
      for (int i = 0; i < 2; i++) {
        requests.submit(() -> createPaymentAttempt(payable.organizationId(), payable.invoiceId()));
      }
      List<MvcTestResult> results =
          List.of(
              requests.poll(10, TimeUnit.SECONDS).get(), requests.poll(10, TimeUnit.SECONDS).get());
      assertThat(results)
          .extracting(result -> result.getResponse().getStatus())
          .containsExactlyInAnyOrder(201, 409);
      for (MvcTestResult result : results) {
        if (result.getResponse().getStatus() == 409) {
          assertError(result, 409, ErrorCode.INVOICE_ALREADY_PAID);
        }
      }
    } finally {
      executor.shutdownNow();
    }
    assertThat(countAttempts(payable.invoiceId())).isEqualTo(2);
    verify(tossPaymentsClient).approveBilling(anyString(), anyString(), any(), anyString(), any());
  }

  @Test
  void 수동_생성은_실패_뒤에_키가_바뀌면_ABORTED_응답만으로도_새_청구를_허용하지_않는다() {
    Payable payable = payableInvoice("test");
    UUID failedAttemptId =
        insertFailedAttempt(payable, "NOT_FOUND_PAYMENT", OffsetDateTime.now().minusHours(25));
    changeCredentialTimestamp(payable.organizationId());
    lookupReturns(
        failedAttemptId,
        new TossPaymentsResult.Success<>(
            new TossPaymentsPayment(
                null, failedAttemptId.toString(), "ABORTED", 13200, 13200, List.of())));

    assertManualRetryBlocked(payable, 1);
  }

  private void assertManualRetryBlocked(Payable payable, int expectedAttempts) {
    MvcTestResult result = createPaymentAttempt(payable.organizationId(), payable.invoiceId());
    assertError(result, 409, ErrorCode.PAYMENT_RESULT_UNCONFIRMED);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.message")
        .isEqualTo("이전 결제 결과를 확인한 뒤 다시 요청해 주세요");
    assertThat(countAttempts(payable.invoiceId())).isEqualTo(expectedAttempts);
    verify(tossPaymentsClient, never())
        .approveBilling(anyString(), anyString(), any(), anyString(), any());
  }

  private void lookupReturns(UUID attemptId, TossPaymentsResult<TossPaymentsPayment> result) {
    when(tossPaymentsClient.findPaymentByOrderId(TEST_SECRET_KEY, attemptId.toString()))
        .thenReturn(result);
  }

  private void changeCredentialTimestamp(UUID organizationId) {
    jdbcTemplate.update(
        "UPDATE toss_payments_credential SET updated_at = now() WHERE organization_id = ?",
        organizationId);
  }

  private UUID insertFailedAttempt(
      Payable payable, String failureCode, OffsetDateTime requestedAt) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO payment_attempt
          (id, organization_id, customer_id, invoice_id, mode, amount, order_name, status,
           failure_code, failure_message, requested_at, completed_at)
        VALUES (?, ?, ?, ?, 'test', 13200, '2026-08 사용료', 'failed', ?, '거절', ?, ?)
        """,
        id,
        payable.organizationId(),
        payable.customerId(),
        payable.invoiceId(),
        failureCode,
        requestedAt,
        requestedAt.plusSeconds(1));
    return id;
  }

  private void tossReturns(TossPaymentsResult<TossPaymentsPayment> result) {
    when(tossPaymentsClient.approveBilling(anyString(), anyString(), any(), anyString(), any()))
        .thenReturn(result);
  }

  private static TossPaymentsResult<TossPaymentsPayment> approved(String paymentKey) {
    return payment(paymentKey, "DONE");
  }

  private static TossPaymentsResult<TossPaymentsPayment> payment(String paymentKey, String status) {
    return new TossPaymentsResult.Success<>(
        new TossPaymentsPayment(paymentKey, "order", status, 13200L, 13200L, null));
  }

  private double attemptCount(String mode, String result) {
    return meterRegistry
        .get("meterengine.payment.attempt")
        .tags("mode", mode, "result", result)
        .counter()
        .count();
  }

  private MvcTestResult createPaymentAttempt(UUID organizationId, UUID invoiceId) {
    return withOrganization(
            mvc.post().uri("/v1/invoices/{id}/payment-attempts", invoiceId), organizationId)
        .exchange();
  }

  private MvcTestResult listPaymentAttempts(UUID organizationId, UUID invoiceId) {
    return withOrganization(
            mvc.get().uri("/v1/invoices/{id}/payment-attempts", invoiceId), organizationId)
        .exchange();
  }

  private MockMvcTester.MockMvcRequestBuilder withOrganization(
      MockMvcTester.MockMvcRequestBuilder request, UUID organizationId) {
    return request.header(
        "Authorization", TestCredentials.issueMemberSession(jdbcTemplate, organizationId));
  }

  private void assertError(MvcTestResult result, int status, ErrorCode errorCode) {
    assertThat(result)
        .hasStatus(status)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(errorCode.getCode());
  }

  private Map<String, Object> onlyAttemptRow(UUID invoiceId) {
    return jdbcTemplate.queryForMap(
        "SELECT * FROM payment_attempt WHERE invoice_id = ?", invoiceId);
  }

  private int countAttempts(UUID invoiceId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM payment_attempt WHERE invoice_id = ?", Integer.class, invoiceId);
  }

  private static String kst(Object timestamp) {
    return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
        ((Timestamp) timestamp).toInstant().atOffset(ZoneOffset.ofHours(9)));
  }

  private Payable payableInvoice(String mode) {
    UUID organizationId = insertOrganization();
    UUID customerId = insertCustomer(organizationId);
    insertCredential(organizationId, mode);
    insertBillingKey(organizationId, customerId, mode);
    if ("live".equals(mode)) {
      switchToLive(organizationId);
    }
    return new Payable(
        organizationId, customerId, insertInvoice(organizationId, customerId, 12000));
  }

  private void switchToLive(UUID organizationId) {
    jdbcTemplate.update(
        "INSERT INTO payment_setting (organization_id, mode, updated_at) VALUES (?, 'live', now())"
            + " ON CONFLICT (organization_id) DO UPDATE SET mode = 'live'",
        organizationId);
  }

  private void insertCredential(UUID organizationId, String mode) {
    boolean live = "live".equals(mode);
    tossPaymentsCredentialRepository.saveAndFlush(
        new TossPaymentsCredential(
            organizationId,
            mode,
            live ? LIVE_CLIENT_KEY : TEST_CLIENT_KEY,
            live ? LIVE_SECRET_KEY : TEST_SECRET_KEY,
            OffsetDateTime.parse("2026-10-01T00:00:00Z")));
  }

  private void insertBillingKey(UUID organizationId, UUID customerId, String mode) {
    billingKeyRepository.saveAndFlush(
        new BillingKey(
            organizationId,
            customerId,
            mode,
            "live".equals(mode) ? LIVE_BILLING_KEY : TEST_BILLING_KEY,
            "61",
            "43301234****123*",
            OffsetDateTime.parse("2026-10-01T00:00:00Z"),
            "2026-10-01",
            OffsetDateTime.parse("2026-10-01T00:00:00Z")));
  }

  private UUID insertOrganization() {
    return jdbcTemplate.queryForObject(
        "INSERT INTO organization (name) VALUES ('도입사') RETURNING id", UUID.class);
  }

  private UUID insertCustomer(UUID organizationId) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO customer (organization_id, name) VALUES (?, '아크메') RETURNING id",
        UUID.class,
        organizationId);
  }

  private UUID insertInvoice(UUID organizationId, UUID customerId, long supplyAmount) {
    Invoice invoice =
        new Invoice(
            UUID.randomUUID(),
            organizationId,
            customerId,
            "아크메",
            "2026-08",
            supplyAmount,
            supplyAmount / 10,
            OffsetDateTime.parse("2026-09-01T04:00:00Z"),
            OffsetDateTime.parse("2026-09-01T05:00:00Z"));
    return invoiceRepository.save(invoice).getId();
  }

  private record Payable(UUID organizationId, UUID customerId, UUID invoiceId) {}
}
