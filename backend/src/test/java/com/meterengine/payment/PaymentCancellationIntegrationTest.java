package com.meterengine.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.invoice.entity.Invoice;
import com.meterengine.invoice.repository.InvoiceRepository;
import com.meterengine.payment.client.TossPaymentsCancelRequest;
import com.meterengine.payment.client.TossPaymentsClient;
import com.meterengine.payment.client.TossPaymentsPayment;
import com.meterengine.payment.client.TossPaymentsResult;
import com.meterengine.payment.entity.TossPaymentsCredential;
import com.meterengine.payment.repository.TossPaymentsCredentialRepository;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PaymentCancellationIntegrationTest {

  private static final String TEST_CLIENT_KEY = "test_ck_D5GePWvyJnrK0W0k6q8gLzN97Eoq";
  private static final String TEST_SECRET_KEY = "test_sk_zXLkKEypNArWmo50nX3lmeaxYG5R";
  private static final String PAYMENT_KEY_PREFIX = "tgen_20261026101500";
  private static final String REASON = "고객 요청";
  private static final OffsetDateTime APPROVED_AT =
      OffsetDateTime.parse("2026-10-26T10:15:00+09:00");
  private static final OffsetDateTime CANCELED_AT =
      OffsetDateTime.parse("2026-10-26T11:00:00+09:00");

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private TossPaymentsCredentialRepository tossPaymentsCredentialRepository;
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
  void 테스트_키는_라이브_결제를_취소하지_못한다() {
    assertMismatchedKeyCannotCreate("live", "test");
  }

  @Test
  void 라이브_키는_테스트_결제를_취소하지_못한다() {
    assertMismatchedKeyCannotCreate("test", "live");
  }

  @Test
  void 테스트_키는_라이브_취소를_멱등_재요청하지_못한다() {
    assertMismatchedKeyCannotReplay("live", "test");
  }

  @Test
  void 라이브_키는_테스트_취소를_멱등_재요청하지_못한다() {
    assertMismatchedKeyCannotReplay("test", "live");
  }

  @Test
  void 테스트_키는_테스트_취소와_멱등_재요청을_허용한다() {
    assertMatchingKeyCanCancelAndReplay("test");
  }

  @Test
  void 라이브_키는_라이브_취소와_멱등_재요청을_허용한다() {
    assertMatchingKeyCanCancelAndReplay("live");
  }

  private void assertMismatchedKeyCannotCreate(String attemptMode, String keyMode) {
    Paid paid = attempt("done", attemptMode);
    tossReturns(canceledPayment(paid, 10200L, cancel("txkey-mode", 3000L)));

    MvcTestResult result = cancellationWithApiKey(paid, keyMode, UUID.randomUUID());

    assertError(result, 409, ErrorCode.PAYMENT_MODE_MISMATCH);
    assertThat(countCancellations(paid.attemptId())).isZero();
    verifyNoInteractions(tossPaymentsClient);
  }

  private void assertMismatchedKeyCannotReplay(String attemptMode, String keyMode) {
    Paid paid = attempt("done", attemptMode);
    UUID key = UUID.randomUUID();
    tossReturns(canceledPayment(paid, 10200L, cancel("txkey-replay-mode", 3000L)));
    assertThat(createCancellation(paid, "{\"amount\":3000,\"reason\":\"고객 요청\"}", key))
        .hasStatus(201);

    assertError(cancellationWithApiKey(paid, keyMode, key), 409, ErrorCode.PAYMENT_MODE_MISMATCH);
    assertThat(countCancellations(paid.attemptId())).isEqualTo(1);
    verify(tossPaymentsClient, times(1)).cancel(anyString(), anyString(), any(), anyString());
  }

  private void assertMatchingKeyCanCancelAndReplay(String mode) {
    Paid paid = attempt("done", mode);
    UUID key = UUID.randomUUID();
    tossReturns(canceledPayment(paid, 10200L, cancel("txkey-same-mode", 3000L)));

    assertThat(cancellationWithApiKey(paid, mode, key)).hasStatus(201);
    assertThat(cancellationWithApiKey(paid, mode, key)).hasStatus(201);
    assertThat(countCancellations(paid.attemptId())).isEqualTo(1);
    verify(tossPaymentsClient, times(1)).cancel(anyString(), anyString(), any(), anyString());
  }

  private MvcTestResult cancellationWithApiKey(Paid paid, String mode, UUID key) {
    return mvc.post()
        .uri("/v1/payment-attempts/{id}/cancellations", paid.attemptId())
        .header(
            "Authorization", TestCredentials.issueApiKey(jdbcTemplate, paid.organizationId(), mode))
        .header("Idempotency-Key", key.toString())
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"amount\":3000,\"reason\":\"고객 요청\"}")
        .exchange();
  }

  @Test
  void 부분_취소가_done이면_201이고_결제_상태는_partially_canceled다() {
    Paid paid = paidAttempt();
    tossReturns(canceledPayment(paid, 10200L, cancel("txkey-first", 3000L)));
    double doneBefore = cancellationCount("test", "done");

    MvcTestResult result = createCancellation(paid, "{\"amount\": 3000, \"reason\": \"고객 요청\"}");

    assertThat(result).hasStatus(201);
    Map<String, Object> cancellation = onlyCancellationRow(paid.attemptId());
    assertThat(cancellation)
        .containsEntry("organization_id", paid.organizationId())
        .containsEntry("mode", "test")
        .containsEntry("amount", 3000L)
        .containsEntry("reason", REASON)
        .containsEntry("status", "done")
        .containsEntry("transaction_key", "txkey-first")
        .containsEntry("failure_code", null);
    assertThat(result)
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {
              "id": "%s", "payment_attempt_id": "%s", "mode": "test", "amount": 3000,
              "reason": "고객 요청", "status": "done", "failure_code": null, "failure_message": null,
              "requested_at": "%s", "completed_at": "%s"
            }
            """
                .formatted(
                    cancellation.get("id"),
                    paid.attemptId(),
                    kst(cancellation.get("requested_at")),
                    kst(cancellation.get("completed_at"))));
    assertThat(cancellationCount("test", "done")).isEqualTo(doneBefore + 1);
    String cancellationId = cancellation.get("id").toString();
    verify(tossPaymentsClient)
        .cancel(
            TEST_SECRET_KEY,
            paid.paymentKey(),
            new TossPaymentsCancelRequest(REASON, 3000L),
            cancellationId);
    assertInvoicePayment(paid, "partially_canceled", 3000);
  }

  @Test
  void amount를_빼면_남은_금액을_cancelAmount로_보내_전부_취소하고_결제_상태는_canceled다() {
    Paid paid = paidAttempt();
    tossReturns(canceledPayment(paid, 10200L, cancel("txkey-first", 3000L)));
    assertThat(createCancellation(paid, "{\"amount\": 3000, \"reason\": \"고객 요청\"}"))
        .hasStatus(201);
    tossReturns(
        canceledPayment(paid, 0L, cancel("txkey-first", 3000L), cancel("txkey-rest", 10200L)));

    MvcTestResult result = createCancellation(paid, "{\"reason\": \"전액 환불\"}");

    assertThat(result).hasStatus(201).bodyJson().extractingPath("$.amount").isEqualTo(10200);
    assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("done");
    Map<String, Object> rest =
        jdbcTemplate.queryForMap(
            "SELECT * FROM payment_cancellation WHERE payment_attempt_id = ? AND reason = ?",
            paid.attemptId(),
            "전액 환불");
    assertThat(rest)
        .containsEntry("amount", 10200L)
        .containsEntry("status", "done")
        .containsEntry("transaction_key", "txkey-rest");
    assertThat(result).bodyJson().extractingPath("$.id").isEqualTo(rest.get("id").toString());
    verify(tossPaymentsClient)
        .cancel(
            TEST_SECRET_KEY,
            paid.paymentKey(),
            new TossPaymentsCancelRequest("전액 환불", 10200L),
            rest.get("id").toString());
    assertInvoicePayment(paid, "canceled", 13200);
  }

  @Test
  void 부분_취소의_합이_결제_금액을_넘으면_두번째는_409이고_토스를_부르지_않는다() {
    Paid paid = paidAttempt();
    tossReturns(canceledPayment(paid, 5200L, cancel("txkey-first", 8000L)));
    assertThat(createCancellation(paid, "{\"amount\": 8000, \"reason\": \"고객 요청\"}"))
        .hasStatus(201);

    assertError(
        createCancellation(paid, "{\"amount\": 6000, \"reason\": \"고객 요청\"}"),
        409,
        ErrorCode.PAYMENT_CANCELLATION_AMOUNT_EXCEEDED);

    assertThat(countCancellations(paid.attemptId())).isEqualTo(1);
    verify(tossPaymentsClient, times(1)).cancel(anyString(), anyString(), any(), anyString());
  }

  @Test
  void 진행_중인_취소가_있으면_409이고_토스를_부르지_않는다() {
    Paid paid = paidAttempt();
    insertPendingCancellation(paid, 1000L);

    assertError(
        createCancellation(paid, "{\"amount\": 1000, \"reason\": \"고객 요청\"}"),
        409,
        ErrorCode.PAYMENT_CANCELLATION_IN_PROGRESS);

    assertThat(countCancellations(paid.attemptId())).isEqualTo(1);
    verifyNoInteractions(tossPaymentsClient);
  }

  @Test
  void 완료되지_않은_시도는_409_not_cancelable이고_전액_취소한_시도는_409_fully_canceled이며_토스를_부르지_않는다() {
    Paid failed = attempt("failed");
    Paid pending = attempt("pending");
    Paid fullyCanceled = paidAttempt();
    insertDoneCancellation(fullyCanceled, 13200L);

    for (Paid notCancelable : List.of(failed, pending)) {
      assertError(
          createCancellation(notCancelable, "{\"reason\": \"고객 요청\"}"),
          409,
          ErrorCode.PAYMENT_ATTEMPT_NOT_CANCELABLE);
    }
    assertError(
        createCancellation(fullyCanceled, "{\"reason\": \"고객 요청\"}"),
        409,
        ErrorCode.PAYMENT_ATTEMPT_FULLY_CANCELED);

    assertThat(countCancellations(failed.attemptId())).isZero();
    assertThat(countCancellations(pending.attemptId())).isZero();
    assertThat(countCancellations(fullyCanceled.attemptId())).isEqualTo(1);
    verifyNoInteractions(tossPaymentsClient);
  }

  @Test
  void amount가_0이하이거나_reason이_비었거나_200자를_넘으면_400이다() {
    Paid paid = paidAttempt();

    assertValidationError(
        createCancellation(paid, "{\"amount\": 0, \"reason\": \"고객 요청\"}"), "amount");
    assertValidationError(
        createCancellation(paid, "{\"amount\": -1, \"reason\": \"고객 요청\"}"), "amount");
    assertValidationError(createCancellation(paid, "{\"amount\": 1000}"), "reason");
    assertValidationError(
        createCancellation(paid, "{\"amount\": 1000, \"reason\": \" \"}"), "reason");
    assertValidationError(
        createCancellation(
            paid, "{\"amount\": 1000, \"reason\": \"%s\"}".formatted("가".repeat(201))),
        "reason");
    assertValidationError(
        createCancellation(paid, "{\"amount\": 1000, \"reason\": \"a\\u0000b\"}"), "reason");

    assertThat(countCancellations(paid.attemptId())).isZero();
    verifyNoInteractions(tossPaymentsClient);
  }

  @Test
  void reason은_200자까지_받는다() {
    Paid paid = paidAttempt();
    tossReturns(canceledPayment(paid, 12200L, cancel("txkey-first", 1000L)));

    assertThat(
            createCancellation(
                paid, "{\"amount\": 1000, \"reason\": \"%s\"}".formatted("가".repeat(200))))
        .hasStatus(201);
  }

  @Test
  void 토스가_거절하면_201_failed이고_토스의_code와_message가_남는다() {
    Paid paid = paidAttempt();
    tossReturns(
        new TossPaymentsResult.Rejected<>(403, "NOT_CANCELABLE_AMOUNT", "취소 할 수 없는 금액 입니다."));
    double failedBefore = cancellationCount("test", "failed");

    MvcTestResult result = createCancellation(paid, "{\"amount\": 3000, \"reason\": \"고객 요청\"}");

    assertThat(result).hasStatus(201);
    Map<String, Object> cancellation = onlyCancellationRow(paid.attemptId());
    assertThat(cancellation)
        .containsEntry("status", "failed")
        .containsEntry("transaction_key", null)
        .containsEntry("failure_code", "NOT_CANCELABLE_AMOUNT")
        .containsEntry("failure_message", "취소 할 수 없는 금액 입니다.");
    assertThat(result)
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {
              "id": "%s", "payment_attempt_id": "%s", "mode": "test", "amount": 3000,
              "reason": "고객 요청", "status": "failed", "failure_code": "NOT_CANCELABLE_AMOUNT",
              "failure_message": "취소 할 수 없는 금액 입니다.",
              "requested_at": "%s", "completed_at": "%s"
            }
            """
                .formatted(
                    cancellation.get("id"),
                    paid.attemptId(),
                    kst(cancellation.get("requested_at")),
                    kst(cancellation.get("completed_at"))));
    assertThat(cancellationCount("test", "failed")).isEqualTo(failedBefore + 1);
  }

  @Test
  void 거절에_토스_code가_없으면_HTTP_상태로_failure_code를_남긴다() {
    Paid paid = paidAttempt();
    tossReturns(new TossPaymentsResult.Rejected<>(400, null, "첫 줄\n둘째 줄"));

    assertThat(createCancellation(paid, "{\"reason\": \"고객 요청\"}")).hasStatus(201);

    assertThat(onlyCancellationRow(paid.attemptId()))
        .containsEntry("status", "failed")
        .containsEntry("failure_code", "HTTP_400")
        .containsEntry("failure_message", "첫 줄\n둘째 줄");
  }

  @Test
  void 토스_결과를_알_수_없으면_201_pending이고_취소는_pending으로_남는다() {
    Paid paid = paidAttempt();
    tossReturns(new TossPaymentsResult.Unknown<>("HttpTimeoutException: request timed out"));
    double unknownBefore = cancellationCount("test", "unknown");

    MvcTestResult result = createCancellation(paid, "{\"amount\": 3000, \"reason\": \"고객 요청\"}");

    assertThat(result).hasStatus(201);
    Map<String, Object> cancellation = onlyCancellationRow(paid.attemptId());
    assertThat(cancellation)
        .containsEntry("status", "pending")
        .containsEntry("transaction_key", null)
        .containsEntry("completed_at", null);
    assertThat(result)
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {
              "id": "%s", "payment_attempt_id": "%s", "mode": "test", "amount": 3000,
              "reason": "고객 요청", "status": "pending", "failure_code": null,
              "failure_message": null, "requested_at": "%s", "completed_at": null
            }
            """
                .formatted(
                    cancellation.get("id"),
                    paid.attemptId(),
                    kst(cancellation.get("requested_at"))));
    assertThat(cancellationCount("test", "unknown")).isEqualTo(unknownBefore + 1);
  }

  @Test
  void 토스가_성공을_주어도_cancels가_비면_201_pending이고_unknown으로_센다() {
    Paid paid = paidAttempt();
    tossReturns(canceledPayment(paid, 13200L));
    double unknownBefore = cancellationCount("test", "unknown");

    MvcTestResult result = createCancellation(paid, "{\"amount\": 3000, \"reason\": \"고객 요청\"}");

    assertThat(result).hasStatus(201).bodyJson().extractingPath("$.status").isEqualTo("pending");
    assertThat(onlyCancellationRow(paid.attemptId())).containsEntry("status", "pending");
    assertThat(cancellationCount("test", "unknown")).isEqualTo(unknownBefore + 1);
  }

  @Test
  void 시도_모드의_키가_없으면_토스를_부르지_않고_toss_payments_credential_missing으로_실패시킨다() {
    Paid paid = paidAttempt();
    jdbcTemplate.update(
        "DELETE FROM toss_payments_credential WHERE organization_id = ?", paid.organizationId());

    MvcTestResult result = createCancellation(paid, "{\"reason\": \"고객 요청\"}");

    assertThat(result).hasStatus(201);
    assertThat(onlyCancellationRow(paid.attemptId()))
        .containsEntry("status", "failed")
        .containsEntry("failure_code", ErrorCode.TOSS_PAYMENTS_CREDENTIAL_MISSING.getCode())
        .containsEntry("failure_message", ErrorCode.TOSS_PAYMENTS_CREDENTIAL_MISSING.getMessage());
    verifyNoInteractions(tossPaymentsClient);
  }

  @Test
  void 토스를_부르는_동안_트랜잭션_밖이고_취소는_pending으로_보이며_시도_행의_잠금은_풀려_있다() {
    Paid paid = paidAttempt();
    List<Boolean> transactionActiveDuringCall = new ArrayList<>();
    List<String> statusesDuringCall = new ArrayList<>();
    List<UUID> lockedDuringCall = new ArrayList<>();
    when(tossPaymentsClient.cancel(anyString(), anyString(), any(), anyString()))
        .thenAnswer(
            invocation -> {
              transactionActiveDuringCall.add(
                  TransactionSynchronizationManager.isActualTransactionActive());
              statusesDuringCall.addAll(
                  jdbcTemplate.queryForList(
                      "SELECT status FROM payment_cancellation WHERE payment_attempt_id = ?",
                      String.class,
                      paid.attemptId()));
              lockedDuringCall.addAll(
                  jdbcTemplate.queryForList(
                      "SELECT id FROM payment_attempt WHERE id = ? FOR UPDATE NOWAIT",
                      UUID.class,
                      paid.attemptId()));
              return canceledPayment(paid, 10200L, cancel("txkey-first", 3000L));
            });

    assertThat(createCancellation(paid, "{\"amount\": 3000, \"reason\": \"고객 요청\"}"))
        .hasStatus(201);

    assertThat(transactionActiveDuringCall).containsExactly(false);
    assertThat(statusesDuringCall).containsExactly("pending");
    assertThat(lockedDuringCall).containsExactly(paid.attemptId());
    assertThat(onlyCancellationRow(paid.attemptId())).containsEntry("status", "done");
  }

  @Test
  void 같은_시도에_동시에_부분_취소하면_취소는_하나이고_나머지는_409_in_progress다() throws Exception {
    Paid paid = paidAttempt();
    CountDownLatch tossCalled = new CountDownLatch(1);
    CountDownLatch tossResponds = new CountDownLatch(1);
    when(tossPaymentsClient.cancel(anyString(), anyString(), any(), anyString()))
        .thenAnswer(
            invocation -> {
              tossCalled.countDown();
              assertThat(tossResponds.await(10, TimeUnit.SECONDS)).isTrue();
              return canceledPayment(paid, 10200L, cancel("txkey-first", 3000L));
            });
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try {
      CompletionService<MvcTestResult> requests = new ExecutorCompletionService<>(executor);
      for (int i = 0; i < 2; i++) {
        requests.submit(
            () -> {
              start.await();
              return createCancellation(paid, "{\"amount\": 3000, \"reason\": \"고객 요청\"}");
            });
      }
      start.countDown();

      assertThat(tossCalled.await(10, TimeUnit.SECONDS)).isTrue();
      MvcTestResult loser = requests.poll(10, TimeUnit.SECONDS).get();
      tossResponds.countDown();
      MvcTestResult winner = requests.poll(10, TimeUnit.SECONDS).get();

      assertError(loser, 409, ErrorCode.PAYMENT_CANCELLATION_IN_PROGRESS);
      assertThat(winner).hasStatus(201);
    } finally {
      tossResponds.countDown();
      executor.shutdownNow();
    }
    assertThat(countCancellations(paid.attemptId())).isEqualTo(1);
    assertThat(onlyCancellationRow(paid.attemptId())).containsEntry("status", "done");
    verify(tossPaymentsClient, times(1)).cancel(anyString(), anyString(), any(), anyString());
  }

  @Test
  void 토스_응답의_cancels에_이미_done인_우리_취소만_있으면_201_pending이다() {
    Paid paid = paidAttempt();
    tossReturns(canceledPayment(paid, 10200L, cancel("txkey-first", 3000L)));
    assertThat(createCancellation(paid, "{\"amount\": 3000, \"reason\": \"첫 요청\"}")).hasStatus(201);

    MvcTestResult result = createCancellation(paid, "{\"amount\": 3000, \"reason\": \"둘째 요청\"}");

    assertThat(result).hasStatus(201).bodyJson().extractingPath("$.status").isEqualTo("pending");
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT status FROM payment_cancellation WHERE payment_attempt_id = ?"
                    + " ORDER BY requested_at",
                String.class,
                paid.attemptId()))
        .containsExactly("done", "pending");
  }

  @Test
  void 없는_시도나_다른_도입사의_시도는_404다() {
    Paid other = paidAttempt();
    UUID organizationId = insertOrganization();

    assertError(
        createCancellation(
            new Paid(organizationId, UUID.randomUUID(), null), "{\"reason\": \"고객 요청\"}"),
        404,
        ErrorCode.PAYMENT_ATTEMPT_NOT_FOUND);
    assertError(
        createCancellation(
            new Paid(organizationId, other.attemptId(), null), "{\"reason\": \"고객 요청\"}"),
        404,
        ErrorCode.PAYMENT_ATTEMPT_NOT_FOUND);
    assertThat(countCancellations(other.attemptId())).isZero();
    verifyNoInteractions(tossPaymentsClient);
  }

  @Test
  void 목록은_시도의_취소를_최신순으로_돌려준다() {
    Paid paid = paidAttempt();
    tossReturns(new TossPaymentsResult.Rejected<>(403, "NOT_CANCELABLE_AMOUNT", "취소 불가"));
    assertThat(createCancellation(paid, "{\"amount\": 1000, \"reason\": \"첫 요청\"}")).hasStatus(201);
    tossReturns(canceledPayment(paid, 11200L, cancel("txkey-second", 2000L)));
    assertThat(createCancellation(paid, "{\"amount\": 2000, \"reason\": \"둘째 요청\"}"))
        .hasStatus(201);

    MvcTestResult listed = listCancellations(paid.organizationId(), paid.attemptId());

    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            "SELECT * FROM payment_cancellation WHERE payment_attempt_id = ?"
                + " ORDER BY requested_at DESC",
            paid.attemptId());
    assertThat(rows).hasSize(2);
    assertThat(listed)
        .hasStatus(200)
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {"payment_cancellations": [
              {
                "id": "%s", "payment_attempt_id": "%s", "mode": "test", "amount": 2000,
                "reason": "둘째 요청", "status": "done", "failure_code": null,
                "failure_message": null, "requested_at": "%s", "completed_at": "%s"
              },
              {
                "id": "%s", "payment_attempt_id": "%s", "mode": "test", "amount": 1000,
                "reason": "첫 요청", "status": "failed", "failure_code": "NOT_CANCELABLE_AMOUNT",
                "failure_message": "취소 불가", "requested_at": "%s", "completed_at": "%s"
              }
            ]}
            """
                .formatted(
                    rows.get(0).get("id"),
                    paid.attemptId(),
                    kst(rows.get(0).get("requested_at")),
                    kst(rows.get(0).get("completed_at")),
                    rows.get(1).get("id"),
                    paid.attemptId(),
                    kst(rows.get(1).get("requested_at")),
                    kst(rows.get(1).get("completed_at"))));
  }

  @Test
  void 목록은_없는_시도나_다른_도입사의_시도면_404다() {
    Paid other = paidAttempt();
    UUID organizationId = insertOrganization();

    assertError(
        listCancellations(organizationId, UUID.randomUUID()),
        404,
        ErrorCode.PAYMENT_ATTEMPT_NOT_FOUND);
    assertError(
        listCancellations(organizationId, other.attemptId()),
        404,
        ErrorCode.PAYMENT_ATTEMPT_NOT_FOUND);
  }

  @Test
  void 인증이_없으면_401이고_id나_본문의_형식이_틀리면_400이다() {
    assertThat(
            mvc.post()
                .uri("/v1/payment-attempts/{id}/cancellations", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\": \"고객 요청\"}")
                .exchange())
        .hasStatus(401);
    assertError(
        withOrganization(
                mvc.post().uri("/v1/payment-attempts/not-a-uuid/cancellations"), UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"reason\": \"고객 요청\"}")
            .exchange(),
        400,
        ErrorCode.VALIDATION_ERROR);
    assertError(
        withOrganization(
                mvc.post().uri("/v1/payment-attempts/{id}/cancellations", UUID.randomUUID()),
                UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\": \"many\", \"reason\": \"고객 요청\"}")
            .exchange(),
        400,
        ErrorCode.MALFORMED_REQUEST_BODY);
    verifyNoInteractions(tossPaymentsClient);
  }

  private void tossReturns(TossPaymentsResult<TossPaymentsPayment> result) {
    when(tossPaymentsClient.cancel(anyString(), anyString(), any(), anyString()))
        .thenReturn(result);
  }

  private static TossPaymentsResult<TossPaymentsPayment> canceledPayment(
      Paid paid, long balanceAmount, TossPaymentsPayment.Cancel... cancels) {
    return new TossPaymentsResult.Success<>(
        new TossPaymentsPayment(
            paid.paymentKey(),
            paid.attemptId().toString(),
            balanceAmount == 0 ? "CANCELED" : "PARTIAL_CANCELED",
            13200L,
            balanceAmount,
            List.of(cancels)));
  }

  private static TossPaymentsPayment.Cancel cancel(String transactionKey, long cancelAmount) {
    return new TossPaymentsPayment.Cancel(
        transactionKey, cancelAmount, OffsetDateTime.now(), "DONE");
  }

  private double cancellationCount(String mode, String result) {
    return meterRegistry
        .get("meterengine.payment.cancellation")
        .tags("mode", mode, "result", result)
        .counter()
        .count();
  }

  @Test
  void 같은_키의_완료_취소를_다시_보내도_취소와_PG호출은_하나다() {
    Paid paid = paidAttempt();
    UUID key = UUID.randomUUID();
    tossReturns(canceledPayment(paid, 10200L, cancel("txkey-idempotent", 3000L)));
    String body = "{\"amount\":3000,\"reason\":\"고객 요청\"}";

    assertThat(createCancellation(paid, body, key)).hasStatus(201);
    UUID id = (UUID) onlyCancellationRow(paid.attemptId()).get("id");
    MvcTestResult repeated = createCancellation(paid, body, key);

    assertThat(repeated).hasStatus(201).bodyJson().extractingPath("$.id").isEqualTo(id.toString());
    assertThat(repeated).bodyJson().extractingPath("$.status").isEqualTo("done");
    assertThat(countCancellations(paid.attemptId())).isEqualTo(1);
    verify(tossPaymentsClient, times(1)).cancel(anyString(), anyString(), any(), anyString());
  }

  @Test
  void 같은_키의_실패와_미확인_취소도_재실행하지_않는다() {
    for (boolean rejected : List.of(true, false)) {
      Paid paid = paidAttempt();
      UUID key = UUID.randomUUID();
      tossReturns(
          rejected
              ? new TossPaymentsResult.Rejected<>(400, "NOT_CANCELABLE_AMOUNT", "거절")
              : new TossPaymentsResult.Unknown<>("timeout"));
      String body = "{\"amount\":3000,\"reason\":\"고객 요청\"}";
      assertThat(createCancellation(paid, body, key)).hasStatus(201);

      MvcTestResult repeated = createCancellation(paid, body, key);

      assertThat(repeated)
          .hasStatus(201)
          .bodyJson()
          .extractingPath("$.status")
          .isEqualTo(rejected ? "failed" : "pending");
      assertThat(countCancellations(paid.attemptId())).isEqualTo(1);
    }
    verify(tossPaymentsClient, times(2)).cancel(anyString(), anyString(), any(), anyString());
  }

  @Test
  void 같은_키의_금액이나_사유나_결제시도가_다르면_409다() {
    Paid paid = paidAttempt();
    UUID key = UUID.randomUUID();
    tossReturns(canceledPayment(paid, 10200L, cancel("txkey-conflict", 3000L)));
    assertThat(createCancellation(paid, "{\"amount\":3000,\"reason\":\"고객 요청\"}", key))
        .hasStatus(201);

    assertError(
        createCancellation(paid, "{\"amount\":2000,\"reason\":\"고객 요청\"}", key),
        409,
        ErrorCode.PAYMENT_CANCELLATION_IDEMPOTENCY_CONFLICT);
    assertError(
        createCancellation(paid, "{\"amount\":3000,\"reason\":\"다른 사유\"}", key),
        409,
        ErrorCode.PAYMENT_CANCELLATION_IDEMPOTENCY_CONFLICT);
    assertError(
        createCancellation(
            new Paid(paid.organizationId(), UUID.randomUUID(), paid.paymentKey()),
            "{\"amount\":3000,\"reason\":\"고객 요청\"}",
            key),
        409,
        ErrorCode.PAYMENT_CANCELLATION_IDEMPOTENCY_CONFLICT);
    assertThat(countCancellations(paid.attemptId())).isEqualTo(1);
    verify(tossPaymentsClient, times(1)).cancel(anyString(), anyString(), any(), anyString());
  }

  @Test
  void 전액_취소를_다시_보내도_원래_전액이고_명시_금액으로_바꾸면_409다() {
    Paid paid = paidAttempt();
    UUID key = UUID.randomUUID();
    tossReturns(canceledPayment(paid, 0L, cancel("txkey-full-idempotent", 13200L)));
    String body = "{\"reason\":\"고객 요청\"}";
    assertThat(createCancellation(paid, body, key)).hasStatus(201);

    assertThat(createCancellation(paid, body, key))
        .hasStatus(201)
        .bodyJson()
        .extractingPath("$.amount")
        .isEqualTo(13200);
    assertError(
        createCancellation(paid, "{\"amount\":13200,\"reason\":\"고객 요청\"}", key),
        409,
        ErrorCode.PAYMENT_CANCELLATION_IDEMPOTENCY_CONFLICT);
    assertThat(countCancellations(paid.attemptId())).isEqualTo(1);
  }

  @Test
  void 같은_키도_다른_도입사의_취소와_섞이지_않는다() {
    Paid first = paidAttempt();
    Paid second = paidAttempt();
    UUID key = UUID.randomUUID();
    when(tossPaymentsClient.cancel(anyString(), eq(first.paymentKey()), any(), anyString()))
        .thenReturn(canceledPayment(first, 10200L, cancel("txkey-org-first", 3000L)));
    when(tossPaymentsClient.cancel(anyString(), eq(second.paymentKey()), any(), anyString()))
        .thenReturn(canceledPayment(second, 10200L, cancel("txkey-org-second", 3000L)));
    String body = "{\"amount\":3000,\"reason\":\"고객 요청\"}";

    assertThat(createCancellation(first, body, key)).hasStatus(201);
    assertThat(createCancellation(second, body, key)).hasStatus(201);

    assertThat(onlyCancellationRow(first.attemptId()).get("id"))
        .isNotEqualTo(onlyCancellationRow(second.attemptId()).get("id"));
    assertThat(countCancellations(first.attemptId())).isEqualTo(1);
    assertThat(countCancellations(second.attemptId())).isEqualTo(1);
  }

  @Test
  void PG응답이_6초_지연되는_동안_동일키를_재전송해도_선기록과_PG호출은_하나다() throws Exception {
    Paid paid = paidAttempt();
    UUID key = UUID.randomUUID();
    String body = "{\"amount\":3000,\"reason\":\"고객 요청\"}";
    CountDownLatch entered = new CountDownLatch(1);
    when(tossPaymentsClient.cancel(anyString(), anyString(), any(), anyString()))
        .thenAnswer(
            invocation -> {
              entered.countDown();
              Thread.sleep(6000);
              return canceledPayment(paid, 10200L, cancel("txkey-slow", 3000L));
            });
    try (ExecutorService executor = Executors.newSingleThreadExecutor()) {
      var first = executor.submit(() -> createCancellation(paid, body, key));
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

      assertThat(createCancellation(paid, body, key))
          .hasStatus(201)
          .bodyJson()
          .extractingPath("$.status")
          .isEqualTo("pending");
      assertThat(first.get(15, TimeUnit.SECONDS)).hasStatus(201);
      assertThat(createCancellation(paid, body, key))
          .hasStatus(201)
          .bodyJson()
          .extractingPath("$.status")
          .isEqualTo("done");
    }
    assertThat(countCancellations(paid.attemptId())).isEqualTo(1);
    verify(tossPaymentsClient, times(1)).cancel(anyString(), anyString(), any(), anyString());
  }

  @Test
  void 멱등키가_UUID가_아니면_PG호출_전에_400이다() {
    Paid paid = paidAttempt();
    MvcTestResult result =
        withOrganization(
                mvc.post().uri("/v1/payment-attempts/{id}/cancellations", paid.attemptId()),
                paid.organizationId())
            .header("Idempotency-Key", "invalid")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"amount\":3000,\"reason\":\"고객 요청\"}")
            .exchange();

    assertError(result, 400, ErrorCode.VALIDATION_ERROR);
    assertThat(countCancellations(paid.attemptId())).isZero();
    verifyNoInteractions(tossPaymentsClient);
  }

  private MvcTestResult createCancellation(Paid paid, String body, UUID key) {
    return withOrganization(
            mvc.post().uri("/v1/payment-attempts/{id}/cancellations", paid.attemptId()),
            paid.organizationId())
        .header("Idempotency-Key", key.toString())
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)
        .exchange();
  }

  private MvcTestResult createCancellation(Paid paid, String body) {
    return withOrganization(
            mvc.post().uri("/v1/payment-attempts/{id}/cancellations", paid.attemptId()),
            paid.organizationId())
        .contentType(MediaType.APPLICATION_JSON)
        .content(body)
        .exchange();
  }

  private MvcTestResult listCancellations(UUID organizationId, UUID attemptId) {
    return withOrganization(
            mvc.get().uri("/v1/payment-attempts/{id}/cancellations", attemptId), organizationId)
        .exchange();
  }

  private void assertInvoicePayment(Paid paid, String status, int canceledAmount) {
    MvcTestResult result =
        withOrganization(mvc.get().uri("/v1/invoice-payments"), paid.organizationId()).exchange();
    assertThat(result)
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.invoice_payments[0].status")
        .isEqualTo(status);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.invoice_payments[0].canceled_amount")
        .isEqualTo(canceledAmount);
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

  private void assertValidationError(MvcTestResult result, String field) {
    assertError(result, 400, ErrorCode.VALIDATION_ERROR);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.errors[*].field")
        .asArray()
        .containsExactly(field);
  }

  private Map<String, Object> onlyCancellationRow(UUID attemptId) {
    return jdbcTemplate.queryForMap(
        "SELECT * FROM payment_cancellation WHERE payment_attempt_id = ?", attemptId);
  }

  private int countCancellations(UUID attemptId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM payment_cancellation WHERE payment_attempt_id = ?",
        Integer.class,
        attemptId);
  }

  private static String kst(Object timestamp) {
    return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
        ((Timestamp) timestamp).toInstant().atOffset(ZoneOffset.ofHours(9)));
  }

  private Paid paidAttempt() {
    return attempt("done");
  }

  private Paid attempt(String status) {
    return attempt(status, "test");
  }

  private Paid attempt(String status, String mode) {
    UUID organizationId = insertOrganization();
    UUID customerId = insertCustomer(organizationId);
    insertCredential(organizationId, mode);
    UUID invoiceId = insertInvoice(organizationId, customerId);
    UUID attemptId = UUID.randomUUID();
    boolean done = "done".equals(status);
    boolean failed = "failed".equals(status);
    String paymentKey = done ? PAYMENT_KEY_PREFIX + attemptId.toString().substring(0, 8) : null;
    jdbcTemplate.update(
        """
        INSERT INTO payment_attempt
          (id, organization_id, customer_id, invoice_id, mode, amount, order_name, status,
           payment_key, failure_code, failure_message, requested_at, completed_at)
        SELECT ?, organization_id, customer_id, id, ?, total_amount, '2026-08 사용료', ?,
               ?, ?, ?, ?, ?
        FROM invoice
        WHERE organization_id = ? AND id = ?
        """,
        attemptId,
        mode,
        status,
        paymentKey,
        failed ? "REJECT_CARD_PAYMENT" : null,
        failed ? "한도초과" : null,
        APPROVED_AT.minusSeconds(5),
        done || failed ? APPROVED_AT : null,
        organizationId,
        invoiceId);
    return new Paid(organizationId, attemptId, paymentKey);
  }

  private void insertPendingCancellation(Paid paid, long amount) {
    insertCancellation(paid, "pending", amount, null, null);
  }

  private void insertDoneCancellation(Paid paid, long amount) {
    insertCancellation(paid, "done", amount, "txkey-" + UUID.randomUUID(), CANCELED_AT);
  }

  private void insertCancellation(
      Paid paid, String status, long amount, String transactionKey, OffsetDateTime completedAt) {
    jdbcTemplate.update(
        """
        INSERT INTO payment_cancellation
          (id, organization_id, payment_attempt_id, mode, amount, reason, status,
           transaction_key, requested_at, completed_at)
        VALUES (?, ?, ?, 'test', ?, '고객 요청', ?, ?, ?, ?)
        """,
        UUID.randomUUID(),
        paid.organizationId(),
        paid.attemptId(),
        amount,
        status,
        transactionKey,
        CANCELED_AT.minusSeconds(1),
        completedAt);
  }

  private void insertCredential(UUID organizationId, String mode) {
    tossPaymentsCredentialRepository.saveAndFlush(
        new TossPaymentsCredential(
            organizationId,
            mode,
            TEST_CLIENT_KEY,
            TEST_SECRET_KEY,
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

  private UUID insertInvoice(UUID organizationId, UUID customerId) {
    Invoice invoice =
        new Invoice(
            UUID.randomUUID(),
            organizationId,
            customerId,
            "아크메",
            "2026-08",
            12000,
            1200,
            OffsetDateTime.parse("2026-09-01T05:00:00Z"));
    return invoiceRepository.save(invoice).getId();
  }

  private record Paid(UUID organizationId, UUID attemptId, String paymentKey) {}
}
