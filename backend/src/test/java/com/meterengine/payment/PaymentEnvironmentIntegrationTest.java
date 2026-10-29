package com.meterengine.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.jayway.jsonpath.JsonPath;
import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.global.security.BusinessEnvironment;
import com.meterengine.global.security.BusinessEnvironmentContext;
import com.meterengine.invoice.entity.Invoice;
import com.meterengine.invoice.repository.InvoiceRepository;
import com.meterengine.payment.client.TossPaymentsApproveBillingRequest;
import com.meterengine.payment.client.TossPaymentsBilling;
import com.meterengine.payment.client.TossPaymentsClient;
import com.meterengine.payment.client.TossPaymentsPayment;
import com.meterengine.payment.client.TossPaymentsResult;
import com.meterengine.payment.client.TossPaymentsTransaction;
import com.meterengine.payment.dto.PaymentJobResult;
import com.meterengine.payment.entity.BillingKey;
import com.meterengine.payment.entity.TossPaymentsCredential;
import com.meterengine.payment.repository.BillingKeyRepository;
import com.meterengine.payment.repository.TossPaymentsCredentialRepository;
import com.meterengine.payment.service.PaymentAttemptConfirmationService;
import com.meterengine.payment.service.PaymentReconciliationService;
import com.meterengine.payment.service.PaymentRetryService;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
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
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PaymentEnvironmentIntegrationTest {
  private static final OffsetDateTime KEY_TIME = OffsetDateTime.parse("2026-10-01T00:00:00Z");
  private static final String TEST_SECRET = "test_sk_original_environment";
  private static final String LIVE_SECRET = "live_sk_original_environment";
  @Autowired private WebApplicationContext context;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private DataSource dataSource;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private TossPaymentsCredentialRepository credentials;
  @Autowired private BillingKeyRepository billingKeys;
  @Autowired private PaymentAttemptConfirmationService confirmation;
  @Autowired private PaymentRetryService retry;
  @Autowired private PaymentReconciliationService reconciliation;
  @MockitoBean private TossPaymentsClient toss;
  private MockMvcTester mvc;
  private UUID organizationId;
  private String session;

  @BeforeEach
  void setUp() {
    mvc = MockMvcTester.from(context, builder -> builder.apply(springSecurity()).build());
    organizationId = UUID.randomUUID();
    session = TestCredentials.issueMemberSession(jdbcTemplate, organizationId);
    when(toss.findPaymentByOrderId(anyString(), anyString()))
        .thenReturn(new TossPaymentsResult.Rejected<>(404, "NOT_FOUND_PAYMENT", "없음"));
    when(toss.approveBilling(anyString(), anyString(), any(), anyString(), isNull()))
        .thenAnswer(
            invocation -> {
              TossPaymentsApproveBillingRequest request = invocation.getArgument(2);
              return new TossPaymentsResult.Success<>(
                  new TossPaymentsPayment(
                      "payment-" + request.orderId(),
                      request.orderId(),
                      "DONE",
                      request.amount(),
                      request.amount(),
                      List.of()));
            });
  }

  @Test
  void 환경별_PG키와_카드로_청구하고_다른_환경_고객과_청구서는_조회하거나_청구하지_못한다() {
    Payable test = payable("test");
    Payable live = payable("live");
    jdbcTemplate.update(
        "INSERT INTO payment_setting (organization_id, mode, updated_at) VALUES (?, 'live', now())",
        organizationId);
    UUID testAttempt = createAttempt("test", test.invoice());
    UUID liveAttempt = createAttempt("live", live.invoice());
    verify(toss)
        .approveBilling(
            eq(TEST_SECRET), eq("billing-test"), any(), eq(testAttempt.toString()), isNull());
    verify(toss)
        .approveBilling(
            eq(LIVE_SECRET), eq("billing-live"), any(), eq(liveAttempt.toString()), isNull());
    assertThat(get("test", "/v1/invoices/" + live.invoice() + "/payment-attempts")).hasStatus(404);
    assertThat(post("test", "/v1/invoices/" + live.invoice() + "/payment-attempts", null))
        .hasStatus(404);
    assertThat(get("test", "/v1/customers/" + live.customer() + "/payment-method")).hasStatus(404);
    assertThat(get("test", "/v1/payment-methods"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.payment_methods")
        .asArray()
        .hasSize(1);
    assertThat(
            jdbcTemplate.queryForMap(
                "SELECT environment_id, mode FROM payment_attempt WHERE id=?", testAttempt))
        .containsEntry("environment_id", environment("test"))
        .containsEntry("mode", "test");
    assertThat(
            jdbcTemplate.queryForMap(
                "SELECT environment_id, mode FROM payment_attempt WHERE id=?", liveAttempt))
        .containsEntry("environment_id", environment("live"))
        .containsEntry("mode", "live");
  }

  @Test
  void 실제환경_설정이_없으면_테스트_PG키로_대신하지_않고_환경의_모드도_바꾸지_못한다() {
    payable("test");
    Payable live = invoice("live");
    assertThat(post("live", "/v1/invoices/" + live.invoice() + "/payment-attempts", null))
        .hasStatus(409)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("toss_payments_credential_missing");
    assertThat(
            mvc.put()
                .uri("/v1/payment-setting")
                .header("Authorization", session)
                .header("X-MeterEngine-Environment", "test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"live\"}")
                .exchange())
        .hasStatus(409);
    assertThat(get("test", "/v1/payment-setting"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.mode")
        .isEqualTo("test");
    assertThat(putCredential("test", "live", "live_sk_rejected_environment")).hasStatus(409);
    assertThat(get("live", "/v1/toss-payments-credentials"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.toss_payments_credentials")
        .asArray()
        .isEmpty();
  }

  @Test
  void 같은_취소_멱등키는_각_환경에서_독립적이고_중복요청은_PG를_다시_부르지_않는다() {
    UUID test = createAttempt("test", payable("test").invoice());
    UUID live = createAttempt("live", payable("live").invoice());
    assertThat(putCredential("test", "test", "test_sk_rotated_environment")).hasStatusOk();
    assertThat(putCredential("live", "live", "live_sk_rotated_environment")).hasStatusOk();
    UUID key = UUID.randomUUID();
    when(toss.cancel(anyString(), anyString(), any(), anyString()))
        .thenReturn(new TossPaymentsResult.Unknown<>("응답 유실"));
    MvcTestResult testCancel = cancel("test", test, key);
    MvcTestResult liveCancel = cancel("live", live, key);
    assertThat(testCancel).hasStatus(201);
    assertThat(liveCancel).hasStatus(201);
    UUID testId = id(testCancel);
    UUID liveId = id(liveCancel);
    assertThat(testId).isNotEqualTo(liveId);
    assertThat(cancel("test", test, key))
        .hasStatus(201)
        .bodyJson()
        .extractingPath("$.id")
        .isEqualTo(testId.toString());
    verify(toss).cancel(eq(TEST_SECRET), eq("payment-" + test), any(), eq(testId.toString()));
    verify(toss).cancel(eq(LIVE_SECRET), eq("payment-" + live), any(), eq(liveId.toString()));
    assertThat(cancel("test", live, UUID.randomUUID())).hasStatus(404);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM payment_cancellation WHERE organization_id=?",
                Integer.class,
                organizationId))
        .isEqualTo(2);
  }

  @Test
  void 키_교체_뒤에도_등록토큰은_원래_키로_카드를_발급하고_새_시도도_그_카드의_키로_승인한다() {
    Payable payable = payable("test");
    MvcTestResult created =
        post("test", "/v1/customers/" + payable.customer() + "/payment-method-registrations", null);
    assertThat(created).hasStatus(201);
    String token = JsonPath.read(body(created), "$.token");
    assertThat(putCredential("test", "test", "test_sk_rotated_environment")).hasStatusOk();
    assertThat(mvc.get().uri("/v1/payment-method-registrations/{token}", token).exchange())
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.client_key")
        .isEqualTo("test_ck_original_environment");
    when(toss.issueBillingKey(eq(TEST_SECRET), eq("auth-key"), eq(payable.customer().toString())))
        .thenReturn(
            new TossPaymentsResult.Success<>(
                new TossPaymentsBilling(
                    "registered-original-key",
                    OffsetDateTime.now(),
                    new TossPaymentsBilling.Card("61", "4330****1234"))));
    when(toss.deleteBillingKey(anyString(), anyString()))
        .thenReturn(new TossPaymentsResult.Success<>(null));
    MvcTestResult issued =
        mvc.post()
            .uri("/v1/payment-method-registrations/{token}/billing-keys", token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"auth_key\":\"auth-key\",\"customer_key\":\""
                    + payable.customer()
                    + "\",\"consent_document_version\":\"2026-10-01\"}")
            .exchange();
    assertThat(issued).hasStatus(201);
    UUID attempt = createAttempt("test", payable.invoice());
    verify(toss)
        .approveBilling(
            eq(TEST_SECRET),
            eq("registered-original-key"),
            any(),
            eq(attempt.toString()),
            isNull());
    assertThat(
            jdbcTemplate.queryForMap(
                "SELECT environment_id, credential_updated_at FROM payment_attempt WHERE id=?",
                attempt))
        .containsEntry("environment_id", environment("test"));
  }

  @Test
  void PG키를_보관하면_발급한_등록링크도_조회와_빌링키_발급을_거절하고_토스를_부르지_않는다() {
    Payable payable = invoice("test");
    in(
        "test",
        () ->
            credentials.saveAndFlush(
                new TossPaymentsCredential(
                    organizationId,
                    "test",
                    "test_ck_original_environment",
                    TEST_SECRET,
                    KEY_TIME)));
    MvcTestResult created =
        post("test", "/v1/customers/" + payable.customer() + "/payment-method-registrations", null);
    assertThat(created).hasStatus(201);
    String token = JsonPath.read(body(created), "$.token");
    assertThat(
            mvc.delete()
                .uri("/v1/toss-payments-credentials/test")
                .header("Authorization", session)
                .header("X-MeterEngine-Environment", "test")
                .exchange())
        .hasStatus(204);
    assertThat(mvc.get().uri("/v1/payment-method-registrations/{token}", token).exchange())
        .hasStatus(409)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("toss_payments_credential_missing");
    assertThat(
            mvc.post()
                .uri("/v1/payment-method-registrations/{token}/billing-keys", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"auth_key\":\"auth-key\",\"customer_key\":\""
                        + payable.customer()
                        + "\",\"consent_document_version\":\"2026-10-01\"}")
                .exchange())
        .hasStatus(409)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("toss_payments_credential_missing");
    verify(toss, never()).issueBillingKey(anyString(), anyString(), anyString());
    verify(toss, never()).deleteBillingKey(anyString(), anyString());
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM billing_key WHERE customer_id=?",
                Integer.class,
                payable.customer()))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT used_at FROM payment_method_registration WHERE customer_id=?",
                OffsetDateTime.class,
                payable.customer()))
        .isNull();
  }

  @Test
  void 새_PG키로_등록한_카드를_저장해도_옛_카드는_발급했던_키로_삭제한다() {
    Payable payable = payable("test");
    String rotated = "test_sk_replacement_environment";
    assertThat(putCredential("test", "test", rotated)).hasStatusOk();
    MvcTestResult registered =
        post("test", "/v1/customers/" + payable.customer() + "/payment-method-registrations", null);
    assertThat(registered).hasStatus(201);
    String token = JsonPath.read(body(registered), "$.token");
    when(toss.issueBillingKey(
            eq(rotated), eq("replacement-auth"), eq(payable.customer().toString())))
        .thenReturn(
            new TossPaymentsResult.Success<>(
                new TossPaymentsBilling(
                    "replacement-card",
                    OffsetDateTime.now(),
                    new TossPaymentsBilling.Card("61", "4330****4567"))));
    when(toss.deleteBillingKey(anyString(), anyString()))
        .thenReturn(new TossPaymentsResult.Success<>(null));
    assertThat(
            mvc.post()
                .uri("/v1/payment-method-registrations/{token}/billing-keys", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"auth_key\":\"replacement-auth\",\"customer_key\":\""
                        + payable.customer()
                        + "\",\"consent_document_version\":\"v1\"}")
                .exchange())
        .hasStatus(201);
    verify(toss).deleteBillingKey(eq(TEST_SECRET), eq("billing-test"));
    UUID attempt = createAttempt("test", payable.invoice());
    verify(toss)
        .approveBilling(
            eq(rotated), eq("replacement-card"), any(), eq(attempt.toString()), isNull());
  }

  @Test
  void PG키를_교체한_뒤_카드를_보관하면_커밋_후_발급했던_키로_폐기한다() {
    Payable payable = payable("test");
    String rotated = "test_sk_replacement_environment";
    assertThat(putCredential("test", "test", rotated)).hasStatusOk();
    var committed = new ArrayList<Boolean>();
    when(toss.deleteBillingKey(anyString(), anyString()))
        .thenAnswer(
            invocation -> {
              try (var connection = dataSource.getConnection();
                  var statement =
                      connection.prepareStatement(
                          "SELECT archived_at IS NOT NULL FROM billing_key WHERE organization_id=? AND environment_id=? AND customer_id=? AND mode='test'")) {
                statement.setObject(1, organizationId);
                statement.setObject(2, environment("test"));
                statement.setObject(3, payable.customer());
                try (var rows = statement.executeQuery()) {
                  assertThat(rows.next()).isTrue();
                  committed.add(rows.getBoolean(1));
                }
              }
              return new TossPaymentsResult.Success<Void>(null);
            });

    assertThat(
            mvc.delete()
                .uri("/v1/customers/{id}/payment-method", payable.customer())
                .header("Authorization", session)
                .header("X-MeterEngine-Environment", "test")
                .exchange())
        .hasStatus(204);

    verify(toss).deleteBillingKey(eq(TEST_SECRET), eq("billing-test"));
    verify(toss, never()).deleteBillingKey(eq(rotated), anyString());
    assertThat(committed).containsExactly(true);
  }

  @Test
  void 원래_PG키_이력이_없으면_카드를_보관해도_다른_키로_폐기하지_않는다() {
    Payable payable = payable("test");
    assertThat(putCredential("test", "test", "test_sk_replacement_environment")).hasStatusOk();
    assertThat(
            jdbcTemplate.update(
                "DELETE FROM toss_payments_credential_history WHERE organization_id=? AND environment_id=? AND mode='test' AND updated_at=?",
                organizationId,
                environment("test"),
                KEY_TIME))
        .isEqualTo(1);
    when(toss.deleteBillingKey(anyString(), anyString()))
        .thenReturn(new TossPaymentsResult.Success<>(null));

    assertThat(
            mvc.delete()
                .uri("/v1/customers/{id}/payment-method", payable.customer())
                .header("Authorization", session)
                .header("X-MeterEngine-Environment", "test")
                .exchange())
        .hasStatus(204);

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT archived_at IS NOT NULL FROM billing_key WHERE organization_id=? AND environment_id=? AND customer_id=? AND mode='test'",
                Boolean.class,
                organizationId,
                environment("test"),
                payable.customer()))
        .isTrue();
    verify(toss, never()).deleteBillingKey(anyString(), anyString());
  }

  @Test
  void 현재_키가_바뀌어도_공개_웹훅은_원본환경의_키로_pending을_완료한다() {
    Payable test = payable("test");
    Payable live = payable("live");
    doReturn(new TossPaymentsResult.Unknown<TossPaymentsPayment>("응답 유실"))
        .when(toss)
        .approveBilling(anyString(), anyString(), any(), anyString(), isNull());
    UUID testAttempt = createAttempt("test", test.invoice());
    UUID liveAttempt = createAttempt("live", live.invoice());
    assertThat(putCredential("test", "test", "test_sk_rotated_environment")).hasStatusOk();
    when(toss.findPayment(eq(TEST_SECRET), eq("confirmed-key")))
        .thenReturn(
            new TossPaymentsResult.Success<>(
                new TossPaymentsPayment(
                    "confirmed-key", testAttempt.toString(), "DONE", 1100, 1100, List.of())));
    assertThat(
            mvc.post()
                .uri("/v1/toss-payments-webhooks/{organization_id}", organizationId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"eventType\":\"PAYMENT_STATUS_CHANGED\",\"data\":{\"orderId\":\""
                        + testAttempt
                        + "\",\"paymentKey\":\"confirmed-key\"}}")
                .exchange())
        .hasStatusOk();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM payment_attempt WHERE id=?", String.class, testAttempt))
        .isEqualTo("done");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM payment_attempt WHERE id=?", String.class, liveAttempt))
        .isEqualTo("pending");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT environment_id FROM webhook_event WHERE organization_id=? AND"
                    + " type='payment_succeeded' AND payload->'data'->>'payment_attempt_id'=?",
                UUID.class,
                organizationId,
                testAttempt.toString()))
        .isEqualTo(environment("test"));
  }

  @Test
  void 요청문맥_없는_확인과_재시도_작업도_원본환경과_교체전_키를_사용한다() {
    Payable test = payable("test");
    Payable live = payable("live");
    UUID pending = insertAttempt(test, "test", false);
    UUID failed = insertAttempt(live, "live", true);
    assertThat(putCredential("test", "test", "test_sk_rotated_environment")).hasStatusOk();
    assertThat(putCredential("live", "live", "live_sk_rotated_environment")).hasStatusOk();
    when(toss.findPaymentByOrderId(eq(TEST_SECRET), eq(pending.toString())))
        .thenReturn(
            new TossPaymentsResult.Success<>(
                new TossPaymentsPayment(
                    "confirmed-job", pending.toString(), "DONE", 1100, 1100, List.of())));
    assertThat(BusinessEnvironmentContext.current()).isNull();
    confirmation.confirm();
    retry.retry();
    assertThat(BusinessEnvironmentContext.current()).isNull();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM payment_attempt WHERE id=?", String.class, pending))
        .isEqualTo("done");
    List<Map<String, Object>> retries =
        jdbcTemplate.queryForList(
            "SELECT id, environment_id, mode, status FROM payment_attempt WHERE invoice_id=? AND"
                + " id<>?",
            live.invoice(),
            failed);
    assertThat(retries).hasSize(1);
    Map<String, Object> retried = retries.getFirst();
    assertThat(retried)
        .containsEntry("environment_id", environment("live"))
        .containsEntry("mode", "live")
        .containsEntry("status", "done");
    verify(toss)
        .approveBilling(
            eq(LIVE_SECRET), eq("billing-live"), any(), eq(retried.get("id").toString()), isNull());
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT environment_id FROM webhook_event WHERE organization_id=? AND"
                    + " type='payment_succeeded' AND payload->'data'->>'payment_attempt_id'=?",
                UUID.class,
                organizationId,
                pending.toString()))
        .isEqualTo(environment("test"));
  }

  @Test
  void 요청문맥_없는_취소_확인도_원본환경과_결제당시_키를_사용한다() {
    Payable live = payable("live");
    payable("test");
    assertThat(putCredential("live", "live", "live_sk_rotated_environment")).hasStatusOk();
    jdbcTemplate.update(
        "UPDATE toss_payments_credential SET updated_at = now() - interval '20 minutes' WHERE"
            + " organization_id=? AND environment_id=? AND mode='live'",
        organizationId,
        environment("live"));
    UUID attempt = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO payment_attempt (id, organization_id, environment_id, customer_id,"
            + " invoice_id, mode, amount, order_name, status, payment_key, requested_at,"
            + " completed_at, credential_updated_at) VALUES (?, ?, ?, ?, ?, 'live', 1100, '사용료',"
            + " 'done', 'cancel-confirm-live', now() - interval '10 minutes', now() - interval '9"
            + " minutes', ?)",
        attempt,
        organizationId,
        environment("live"),
        live.customer(),
        live.invoice(),
        KEY_TIME);
    UUID cancellation = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO payment_cancellation (id, organization_id, environment_id,"
            + " payment_attempt_id, mode, amount, reason, status, requested_at) VALUES (?, ?, ?,"
            + " ?, 'live', 400, '부분 취소', 'pending', now() - interval '3 minutes')",
        cancellation,
        organizationId,
        environment("live"),
        attempt);
    when(toss.findPayment(eq(LIVE_SECRET), eq("cancel-confirm-live")))
        .thenReturn(
            new TossPaymentsResult.Success<>(
                new TossPaymentsPayment(
                    "cancel-confirm-live",
                    attempt.toString(),
                    "PARTIAL_CANCELED",
                    1100,
                    700,
                    List.of(
                        new TossPaymentsPayment.Cancel(
                            "cancel-transaction-live",
                            400,
                            OffsetDateTime.now().minusMinutes(2),
                            "DONE")))));
    assertThat(BusinessEnvironmentContext.current()).isNull();

    confirmation.confirm();

    assertThat(BusinessEnvironmentContext.current()).isNull();
    assertThat(
            jdbcTemplate.queryForMap(
                "SELECT status, transaction_key FROM payment_cancellation WHERE id=?",
                cancellation))
        .containsEntry("status", "done")
        .containsEntry("transaction_key", "cancel-transaction-live");
    verify(toss, never()).findPayment(eq("live_sk_rotated_environment"), anyString());
    verify(toss, never()).findPayment(eq(TEST_SECRET), anyString());
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT environment_id FROM webhook_event WHERE organization_id=? AND"
                    + " type='payment_canceled'",
                UUID.class,
                organizationId))
        .containsExactly(environment("live"));
  }

  @Test
  void 대사는_교체전_키도_사용하고_다른_환경의_거래를_불일치로_기록하지_않는다() {
    String originalSecret = "test_sk_reconciliation_original";
    Payable test = payable("test", originalSecret);
    Payable live = payable("live");
    UUID testAttempt = insertAttempt(test, "test", false);
    UUID liveAttempt = insertAttempt(live, "live", false);
    OffsetDateTime completed = OffsetDateTime.now().minusMinutes(20);
    jdbcTemplate.update(
        "UPDATE payment_attempt SET status='done', payment_key=?, completed_at=? WHERE id=?",
        "reconciled-test",
        completed,
        testAttempt);
    jdbcTemplate.update(
        "UPDATE payment_attempt SET status='done', payment_key=?, completed_at=? WHERE id=?",
        "reconciled-live",
        completed,
        liveAttempt);
    assertThat(putCredential("test", "test", "test_sk_reconciliation_rotated")).hasStatusOk();
    TossPaymentsTransaction testTransaction =
        new TossPaymentsTransaction(
            "transaction-test", "reconciled-test", testAttempt.toString(), "DONE", completed, 1100);
    TossPaymentsTransaction liveTransaction =
        new TossPaymentsTransaction(
            "transaction-live", "reconciled-live", liveAttempt.toString(), "DONE", completed, 1100);
    when(toss.listTransactions(anyString(), any(), any(), any(), eq(1000)))
        .thenReturn(new TossPaymentsResult.Success<>(List.of()));
    when(toss.listTransactions(eq(originalSecret), any(), any(), isNull(), eq(1000)))
        .thenReturn(new TossPaymentsResult.Success<>(List.of(testTransaction, liveTransaction)));
    when(toss.listTransactions(eq(LIVE_SECRET), any(), any(), isNull(), eq(1000)))
        .thenReturn(new TossPaymentsResult.Success<>(List.of(liveTransaction)));
    reconciliation.reconcile(OffsetDateTime.now());
    verify(toss).listTransactions(eq(originalSecret), any(), any(), isNull(), eq(1000));
    verify(toss, never()).findPayment(eq(originalSecret), eq("reconciled-test"));
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM payment_reconciliation_mismatch WHERE organization_id=?",
                Integer.class,
                organizationId))
        .isZero();
    assertThat(BusinessEnvironmentContext.current()).isNull();
  }

  @Test
  void 대사는_창_안에_쓰이지_않은_옛_키를_부르지_않고_한_버전이_실패해도_다른_버전을_대사한다() {
    String oldSecret = "test_sk_reconciliation_revoked";
    String midSecret = "test_sk_reconciliation_retired";
    Payable test = payable("test", oldSecret);
    assertThat(putCredential("test", "test", midSecret)).hasStatusOk();
    UUID midAttempt = insertAttempt(test, "test", false);
    OffsetDateTime completed = OffsetDateTime.now().minusMinutes(20);
    jdbcTemplate.update(
        "UPDATE payment_attempt SET status='done', payment_key=?, completed_at=? WHERE id=?",
        "reconciled-retired",
        completed,
        midAttempt);
    assertThat(putCredential("test", "test", "test_sk_reconciliation_current")).hasStatusOk();
    when(toss.listTransactions(anyString(), any(), any(), any(), eq(1000)))
        .thenReturn(new TossPaymentsResult.Success<>(List.of()));
    when(toss.listTransactions(eq(oldSecret), any(), any(), any(), eq(1000)))
        .thenReturn(new TossPaymentsResult.Rejected<>(401, "UNAUTHORIZED_KEY", "폐기한 키"));
    when(toss.listTransactions(eq(midSecret), any(), any(), isNull(), eq(1000)))
        .thenReturn(
            new TossPaymentsResult.Success<>(
                List.of(
                    new TossPaymentsTransaction(
                        "transaction-retired",
                        "reconciled-retired",
                        midAttempt.toString(),
                        "DONE",
                        completed,
                        1100))));

    PaymentJobResult result = reconciliation.reconcile(OffsetDateTime.now());

    assertThat(result.failedCount()).isZero();
    verify(toss, never()).listTransactions(eq(oldSecret), any(), any(), any(), eq(1000));
    verify(toss).listTransactions(eq(midSecret), any(), any(), isNull(), eq(1000));
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM payment_reconciliation_mismatch WHERE organization_id=?",
                Integer.class,
                organizationId))
        .isZero();
  }

  @Test
  void 대사는_현재_키가_실패해도_창_안에_쓰인_옛_키를_대사하고_실패를_한_번_센다() {
    String retiredSecret = "test_sk_reconciliation_partial_retired";
    String currentSecret = "test_sk_reconciliation_partial_current";
    when(toss.listTransactions(anyString(), any(), any(), any(), eq(1000)))
        .thenReturn(new TossPaymentsResult.Success<>(List.of()));
    // 같은 DB에 남은 다른 시험의 도입사 실패는 빼고 이 도입사의 실패만 센다.
    int othersFailed = reconciliation.reconcile(OffsetDateTime.now()).failedCount();
    Payable test = payable("test");
    assertThat(putCredential("test", "test", retiredSecret)).hasStatusOk();
    UUID retiredAttempt = insertAttempt(test, "test", false);
    OffsetDateTime completed = OffsetDateTime.now().minusMinutes(20);
    jdbcTemplate.update(
        "UPDATE payment_attempt SET status='done', payment_key=?, completed_at=? WHERE id=?",
        "reconciled-partial",
        completed,
        retiredAttempt);
    assertThat(putCredential("test", "test", currentSecret)).hasStatusOk();
    when(toss.listTransactions(eq(currentSecret), any(), any(), any(), eq(1000)))
        .thenReturn(new TossPaymentsResult.Rejected<>(503, "PROVIDER_ERROR", "일시 장애"));
    when(toss.listTransactions(eq(retiredSecret), any(), any(), isNull(), eq(1000)))
        .thenReturn(
            new TossPaymentsResult.Success<>(
                List.of(
                    new TossPaymentsTransaction(
                        "transaction-partial",
                        "reconciled-partial",
                        retiredAttempt.toString(),
                        "DONE",
                        completed,
                        1100))));

    PaymentJobResult result = reconciliation.reconcile(OffsetDateTime.now());

    assertThat(result.failedCount() - othersFailed).isEqualTo(1);
    verify(toss).listTransactions(eq(currentSecret), any(), any(), any(), eq(1000));
    verify(toss).listTransactions(eq(retiredSecret), any(), any(), isNull(), eq(1000));
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM payment_reconciliation_mismatch WHERE organization_id=?",
                Integer.class,
                organizationId))
        .isZero();
  }

  private UUID insertAttempt(Payable payable, String mode, boolean failed) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO payment_attempt (id, organization_id, environment_id, customer_id, invoice_id,"
            + " mode, amount, order_name, status, requested_at, completed_at, failure_code,"
            + " failure_message, credential_updated_at) VALUES (?, ?, ?, ?, ?, ?, 1100, '사용료', ?,"
            + " ?, ?, ?, ?, ?)",
        id,
        organizationId,
        environment(mode),
        payable.customer(),
        payable.invoice(),
        mode,
        failed ? "failed" : "pending",
        OffsetDateTime.now().minusHours(26),
        failed ? OffsetDateTime.now().minusHours(25) : null,
        failed ? "REJECT_CARD_PAYMENT" : null,
        failed ? "거절" : null,
        jdbcTemplate.queryForObject(
            "SELECT updated_at FROM toss_payments_credential WHERE organization_id=? AND"
                + " environment_id=? AND mode=?",
            OffsetDateTime.class,
            organizationId,
            environment(mode),
            mode));
    return id;
  }

  private Payable payable(String mode) {
    return payable(mode, mode.equals("test") ? TEST_SECRET : LIVE_SECRET);
  }

  private Payable payable(String mode, String secret) {
    Payable value = invoice(mode);
    in(
        mode,
        () -> {
          credentials.saveAndFlush(
              new TossPaymentsCredential(
                  organizationId, mode, mode + "_ck_original_environment", secret, KEY_TIME));
          billingKeys.saveAndFlush(
              new BillingKey(
                      organizationId,
                      value.customer(),
                      mode,
                      "billing-" + mode,
                      "61",
                      "4330****1234",
                      KEY_TIME,
                      "2026-10-01",
                      KEY_TIME)
                  .pinCredential(KEY_TIME));
        });
    return value;
  }

  private Payable invoice(String mode) {
    UUID environmentId = environment(mode);
    UUID customer =
        jdbcTemplate.queryForObject(
            "INSERT INTO customer (organization_id, environment_id, external_id, name) VALUES (?,"
                + " ?, 'same', '고객') RETURNING id",
            UUID.class,
            organizationId,
            environmentId);
    Invoice invoice =
        invoiceRepository.saveAndFlush(
            new Invoice(
                UUID.randomUUID(),
                organizationId,
                customer,
                "고객",
                "2026-08",
                1000,
                100,
                OffsetDateTime.now(),
                environmentId));
    return new Payable(customer, invoice.getId());
  }

  private UUID environment(String mode) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM organization_environment WHERE organization_id=? AND mode=?",
        UUID.class,
        organizationId,
        mode);
  }

  private void in(String mode, Runnable action) {
    BusinessEnvironmentContext.run(
        new BusinessEnvironment(organizationId, environment(mode), mode), action);
  }

  private UUID createAttempt(String mode, UUID invoice) {
    MvcTestResult result = post(mode, "/v1/invoices/" + invoice + "/payment-attempts", null);
    assertThat(result).hasStatus(201);
    return id(result);
  }

  private MvcTestResult cancel(String mode, UUID attempt, UUID key) {
    return mvc.post()
        .uri("/v1/payment-attempts/{id}/cancellations", attempt)
        .header("Authorization", session)
        .header("X-MeterEngine-Environment", mode)
        .header("Idempotency-Key", key)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"amount\":100,\"reason\":\"일부 취소\"}")
        .exchange();
  }

  private MvcTestResult putCredential(String environment, String mode, String secret) {
    return mvc.put()
        .uri("/v1/toss-payments-credentials/{mode}", mode)
        .header("Authorization", session)
        .header("X-MeterEngine-Environment", environment)
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            "{\"client_key\":\""
                + mode
                + "_ck_rotated_environment\",\"secret_key\":\""
                + secret
                + "\"}")
        .exchange();
  }

  private MvcTestResult get(String mode, String path) {
    return mvc.get()
        .uri(path)
        .header("Authorization", session)
        .header("X-MeterEngine-Environment", mode)
        .exchange();
  }

  private MvcTestResult post(String mode, String path, String json) {
    var request =
        mvc.post()
            .uri(path)
            .header("Authorization", session)
            .header("X-MeterEngine-Environment", mode);
    if (json != null) request.contentType(MediaType.APPLICATION_JSON).content(json);
    return request.exchange();
  }

  private UUID id(MvcTestResult result) {
    return UUID.fromString(JsonPath.read(body(result), "$.id"));
  }

  private String body(MvcTestResult result) {
    return new String(
        result.getResponse().getContentAsByteArray(), java.nio.charset.StandardCharsets.UTF_8);
  }

  private record Payable(UUID customer, UUID invoice) {}
}
