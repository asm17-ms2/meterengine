package com.meterengine.invoiceview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.jayway.jsonpath.JsonPath;
import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.payment.client.TossPaymentsApproveBillingRequest;
import com.meterengine.payment.client.TossPaymentsClient;
import com.meterengine.payment.client.TossPaymentsPayment;
import com.meterengine.payment.client.TossPaymentsResult;
import com.meterengine.payment.entity.BillingKey;
import com.meterengine.payment.entity.BillingKeyId;
import com.meterengine.payment.entity.TossPaymentsCredential;
import com.meterengine.payment.repository.BillingKeyRepository;
import com.meterengine.payment.repository.TossPaymentsCredentialRepository;
import com.meterengine.payment.service.PaymentRetryService;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(
    properties = {
      "payment.retry-max-attempts=3",
      "payment.retry-interval=PT24H",
      "payment.retry-cron=-"
    })
class CustomerPaymentRetryIntegrationTest {
  private static final String SECRET_KEY = "test_sk_customer_retry_mock_00000001";
  private static final String BILLING_KEY = "customer-retry-mock-billing-key";
  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private TossPaymentsCredentialRepository credentialRepository;
  @Autowired private BillingKeyRepository billingKeyRepository;
  @Autowired private PaymentRetryService paymentRetryService;
  @Autowired private DataSource dataSource;
  @MockitoBean private TossPaymentsClient tossPaymentsClient;
  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
  }

  @Test
  void 기존_조회_링크와_옵션을_생략한_링크는_고객의_결제를_허용하지_않는다() {
    Fixture fixture = fixture(true, "REJECT_CARD_PAYMENT");
    MvcTestResult created = create(fixture, false);
    assertThat(created).bodyJson().extractingPath("$.allow_payment_retry").isEqualTo(false);
    String token = value(created, "$.token");
    assertThat(lookup(token))
        .bodyJson()
        .extractingPath("$.payment_retry.reason")
        .isEqualTo("link_read_only");
    assertThat(lookup(token)).bodyJson().extractingPath("$.payment_retry.version").isNull();
    assertThat(retry(token, "0".repeat(64)))
        .hasStatus(409)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("payment_retry_unavailable");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT allow_payment_retry FROM invoice_view_link WHERE id=?",
                Boolean.class,
                UUID.fromString(value(created, "$.id"))))
        .isFalse();
    verifyNoInteractions(tossPaymentsClient);
  }

  @Test
  void 고객은_24시간을_기다리지_않고_서버의_확정액과_등록_카드로_재시도하며_행위자를_남긴다() {
    Fixture fixture = fixture(true, "REJECT_CARD_PAYMENT");
    MvcTestResult created = create(fixture, true);
    String token = value(created, "$.token");
    MvcTestResult initial = lookup(token);
    assertThat(initial).bodyJson().extractingPath("$.payment_retry.allowed").isEqualTo(true);
    String version = value(initial, "$.payment_retry.version");
    assertThat(version).matches("[a-f0-9]{64}");
    when(tossPaymentsClient.approveBilling(anyString(), anyString(), any(), anyString(), any()))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              TossPaymentsApproveBillingRequest body = invocation.getArgument(2);
              assertThat(body.amount()).isEqualTo(1100);
              assertThat(body.customerKey()).isEqualTo(fixture.customerId().toString());
              assertThat(invocation.<String>getArgument(1)).isEqualTo(BILLING_KEY);
              assertThat(invocation.<String>getArgument(3)).isEqualTo(body.orderId());
              assertThat(invocation.<String>getArgument(4)).isNull();
              return approved(fixture);
            });
    MvcTestResult result =
        mvc.post()
            .uri("/v1/public/invoice-links/payment-retries")
            .header("TossPayments-Test-Code", "DECLINE_FROM_BROWSER")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"token\":\""
                    + token
                    + "\",\"retry_version\":\""
                    + version
                    + "\",\"amount\":1,\"customer_id\":\""
                    + UUID.randomUUID()
                    + "\"}")
            .exchange();
    assertThat(result)
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.payment.status")
        .isEqualTo("paid");
    assertThat(result)
        .bodyText()
        .doesNotContain(
            token,
            fixture.invoiceId().toString(),
            fixture.customerId().toString(),
            SECRET_KEY,
            BILLING_KEY,
            "mock-payment-");
    assertThat(count(fixture)).isEqualTo(2);
    Map<String, Object> audit =
        jdbcTemplate.queryForMap(
            "SELECT actor_type,actor_id FROM audit_log WHERE organization_id=? AND action='payment_attempt_created'",
            fixture.organizationId());
    assertThat(audit)
        .containsEntry("actor_type", "invoice_view_link")
        .containsEntry("actor_id", UUID.fromString(value(created, "$.id")));
    String details =
        jdbcTemplate
            .queryForList(
                "SELECT detail::text FROM audit_log WHERE organization_id=?",
                String.class,
                fixture.organizationId())
            .toString();
    assertThat(details).doesNotContain(token, version, SECRET_KEY, BILLING_KEY);
  }

  @Test
  void 즉시_실패해도_같은_버전은_한번만_시도하고_새_확인은_총_세번_상한을_넘지_않는다() {
    Fixture fixture = fixture(true, "REJECT_CARD_PAYMENT");
    String token = value(create(fixture, true), "$.token");
    String version = value(lookup(token), "$.payment_retry.version");
    rejectApproval();
    assertThat(retry(token, version)).hasStatusOk();
    assertThat(retry(token, version)).hasStatus(409);
    assertThat(count(fixture)).isEqualTo(2);
    String next = value(lookup(token), "$.payment_retry.version");
    assertThat(next).isNotEqualTo(version);
    assertThat(retry(token, next)).hasStatusOk();
    assertThat(count(fixture)).isEqualTo(3);
    assertThat(lookup(token))
        .bodyJson()
        .extractingPath("$.payment_retry.reason")
        .isEqualTo("retry_limit_reached");
    assertThat(retry(token, next)).hasStatus(409);
    verify(tossPaymentsClient, times(2))
        .approveBilling(anyString(), anyString(), any(), anyString(), any());
  }

  @Test
  void 결과를_모르면_pending으로_남겨_새_결제를_막는다() {
    Fixture fixture = fixture(true, "REJECT_CARD_PAYMENT");
    String token = value(create(fixture, true), "$.token");
    String version = value(lookup(token), "$.payment_retry.version");
    when(tossPaymentsClient.approveBilling(anyString(), anyString(), any(), anyString(), any()))
        .thenReturn(new TossPaymentsResult.Unknown<>("로컬 응답 유실"));
    MvcTestResult result = retry(token, version);
    assertThat(result)
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.payment.status")
        .isEqualTo("pending");
    assertThat(result).bodyJson().extractingPath("$.payment.paid_amount").isNull();
    assertThat(result).bodyJson().extractingPath("$.payment_retry.allowed").isEqualTo(false);
    assertThat(retry(token, version)).hasStatus(409);
    assertThat(count(fixture)).isEqualTo(2);
    verify(tossPaymentsClient).approveBilling(anyString(), anyString(), any(), anyString(), any());
  }

  @Test
  void 최초_결제와_진행중_승인_부분취소와_전액취소는_고객_재시도로_청구할_수_없다() {
    for (String state : List.of("unpaid", "pending", "done", "partial", "canceled")) {
      Fixture fixture = fixture(false, null);
      if (!"unpaid".equals(state)) {
        UUID attempt =
            insertAttempt(
                fixture,
                "pending".equals(state) ? "pending" : "done",
                null,
                OffsetDateTime.now().minusMinutes(1));
        if (List.of("partial", "canceled").contains(state))
          jdbcTemplate.update(
              "INSERT INTO payment_cancellation(id,organization_id,payment_attempt_id,mode,amount,reason,status,transaction_key,requested_at,completed_at) VALUES (?,?,?,'test',?,'검증','done',?,now(),now())",
              UUID.randomUUID(),
              fixture.organizationId(),
              attempt,
              "partial".equals(state) ? 400 : 1100,
              UUID.randomUUID().toString());
      }
      String token = value(create(fixture, true), "$.token");
      assertThat(lookup(token))
          .bodyJson()
          .extractingPath("$.payment_retry.allowed")
          .isEqualTo(false);
      assertThat(retry(token, "0".repeat(64))).hasStatus(409);
    }
    verifyNoInteractions(tossPaymentsClient);
  }

  @Test
  void 링크_폐기와_만료_비활성_도입사는_같은_404로_차단된다() {
    for (String condition : List.of("revoked", "expired", "inactive")) {
      Fixture fixture = fixture(true, "REJECT_CARD_PAYMENT");
      MvcTestResult created = create(fixture, true);
      String token = value(created, "$.token");
      String version = value(lookup(token), "$.payment_retry.version");
      if ("revoked".equals(condition)) revoke(fixture, value(created, "$.id"));
      if ("expired".equals(condition))
        jdbcTemplate.update(
            "UPDATE invoice_view_link SET created_at=now()-interval '2 days',expires_at=now()-interval '1 second' WHERE id=?",
            UUID.fromString(value(created, "$.id")));
      if ("inactive".equals(condition))
        jdbcTemplate.update(
            "UPDATE organization SET status='suspended' WHERE id=?", fixture.organizationId());
      assertThat(retry(token, version)).hasStatus(404);
      assertThat(count(fixture)).isEqualTo(1);
    }
    verifyNoInteractions(tossPaymentsClient);
  }

  @Test
  void 조회_후_결제모드를_바꾸면_다른_모드의_카드로_재시도하지_않는다() {
    Fixture fixture = fixture(true, "REJECT_CARD_PAYMENT");
    String token = value(create(fixture, true), "$.token");
    String version = value(lookup(token), "$.payment_retry.version");
    jdbcTemplate.update(
        "INSERT INTO payment_setting(organization_id,mode,updated_at) VALUES (?,'live',now())",
        fixture.organizationId());
    assertThat(lookup(token))
        .bodyJson()
        .extractingPath("$.payment_retry.reason")
        .isEqualTo("payment_mode_changed");
    assertThat(retry(token, version)).hasStatus(409);
    assertThat(count(fixture)).isEqualTo(1);
    verifyNoInteractions(tossPaymentsClient);
  }

  @Test
  void 카드와_PG키를_보관하면_조회한_허용_상태로도_재시도할_수_없다() {
    for (String table : List.of("billing_key", "toss_payments_credential")) {
      Fixture fixture = fixture(true, "REJECT_CARD_PAYMENT");
      String token = value(create(fixture, true), "$.token");
      String version = value(lookup(token), "$.payment_retry.version");
      jdbcTemplate.update(
          "UPDATE " + table + " SET archived_at=now() WHERE organization_id=?",
          fixture.organizationId());
      assertThat(lookup(token))
          .bodyJson()
          .extractingPath("$.payment_retry.allowed")
          .isEqualTo(false);
      assertThat(retry(token, version)).hasStatus(409);
      assertThat(count(fixture)).isEqualTo(1);
    }
    verifyNoInteractions(tossPaymentsClient);
  }

  @Test
  void 카드_오류는_카드를_다시_인증한_뒤에만_고객_재시도를_허용한다() {
    Fixture fixture = fixture(true, "INVALID_STOPPED_CARD");
    String token = value(create(fixture, true), "$.token");
    assertThat(lookup(token)).bodyJson().extractingPath("$.payment_retry.allowed").isEqualTo(false);
    assertThat(lookup(token))
        .bodyJson()
        .extractingPath("$.payment_retry.reason")
        .isEqualTo("card_update_required");
    BillingKey card =
        billingKeyRepository
            .findById(new BillingKeyId(fixture.organizationId(), fixture.customerId(), "test"))
            .orElseThrow();
    card.update(
        "customer-renewed-billing-key",
        "61",
        "1234****5678",
        OffsetDateTime.now(),
        "2026-10-01",
        OffsetDateTime.now());
    billingKeyRepository.saveAndFlush(card);
    assertThat(lookup(token)).bodyJson().extractingPath("$.payment_retry.allowed").isEqualTo(true);
    verifyNoInteractions(tossPaymentsClient);
  }

  @Test
  void 이전_NOT_FOUND_결제가_실제로_승인돼_있으면_새_승인을_하지_않는다() {
    Fixture fixture = fixture(true, "NOT_FOUND_PAYMENT");
    String token = value(create(fixture, true), "$.token");
    String version = value(lookup(token), "$.payment_retry.version");
    when(tossPaymentsClient.findPaymentByOrderId(anyString(), anyString()))
        .thenReturn(
            new TossPaymentsResult.Success<>(
                new TossPaymentsPayment(
                    "old-approved-key",
                    fixture.failedAttemptId().toString(),
                    "DONE",
                    1100L,
                    1100L,
                    List.of())));
    MvcTestResult result = retry(token, version);
    assertThat(result)
        .hasStatus(409)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("payment_result_unconfirmed");
    assertThat(count(fixture)).isEqualTo(1);
    verify(tossPaymentsClient, never())
        .approveBilling(anyString(), anyString(), any(), anyString(), any());
  }

  @Test
  void NOT_FOUND_실패_후_키가_교체되면_옛_키의_없음으로_청구하지_않는다() {
    Fixture fixture = fixture(true, "NOT_FOUND_PAYMENT");
    String token = value(create(fixture, true), "$.token");
    String version = value(lookup(token), "$.payment_retry.version");
    jdbcTemplate.update(
        "UPDATE toss_payments_credential SET updated_at=now() WHERE organization_id=?",
        fixture.organizationId());
    assertThat(lookup(token))
        .bodyJson()
        .extractingPath("$.payment_retry.reason")
        .isEqualTo("payment_result_unconfirmed");
    assertThat(retry(token, version)).hasStatus(409);
    verifyNoInteractions(tossPaymentsClient);
  }

  @Test
  void 과거_승인이_있으면_최신_실패가_있어도_재시도를_노출하지_않는다() {
    Fixture fixture = fixture(true, "REJECT_CARD_PAYMENT");
    String token = value(create(fixture, true), "$.token");
    String version = value(lookup(token), "$.payment_retry.version");
    insertAttempt(fixture, "done", null, OffsetDateTime.now().minusMinutes(5));
    assertThat(lookup(token)).bodyJson().extractingPath("$.payment_retry.allowed").isEqualTo(false);
    assertThat(retry(token, version)).hasStatus(409);
    verifyNoInteractions(tossPaymentsClient);
  }

  @Test
  void 같은_실패를_동시에_고객이_재시도해도_즉시_실패한_새_승인은_한번뿐이다() throws Exception {
    concurrentRetry("customer");
  }

  @Test
  void 자동_재시도와_고객_재시도가_겹쳐도_새_승인은_한번뿐이다() throws Exception {
    concurrentRetry("automatic");
  }

  @Test
  void 관리자_수동_재시도와_고객_재시도가_겹쳐도_새_승인은_한번뿐이다() throws Exception {
    concurrentRetry("admin");
  }

  @Test
  void 일반_거절_뒤_자동_재시도와_고객_재시도가_겹치면_인보이스_잠금을_기다려_새_승인은_한번뿐이다() throws Exception {
    Fixture fixture = fixture(true, "REJECT_CARD_PAYMENT", OffsetDateTime.now().minusHours(25));
    String token = value(create(fixture, true), "$.token");
    String version = value(lookup(token), "$.payment_retry.version");
    rejectApproval();
    try (var executor = Executors.newFixedThreadPool(2);
        Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try (var statement =
          connection.prepareStatement("SELECT id FROM invoice WHERE id=? FOR UPDATE")) {
        statement.setObject(1, fixture.invoiceId());
        statement.executeQuery().close();
      }
      var customer = executor.submit(() -> retry(token, version));
      var automatic = executor.submit(() -> paymentRetryService.retry());
      try {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        int waiting = 0;
        while (System.nanoTime() < deadline) {
          waiting =
              jdbcTemplate.queryForObject(
                  "SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND state='active' AND wait_event_type='Lock' AND lower(query) LIKE '%from invoice%'",
                  Integer.class);
          if (waiting >= 2) break;
          Thread.sleep(10);
        }
        assertThat(waiting).isEqualTo(2);
      } finally {
        connection.commit();
      }
      assertThat(customer.get(15, TimeUnit.SECONDS).getResponse().getStatus()).isIn(200, 409);
      automatic.get(15, TimeUnit.SECONDS);
    }
    assertThat(count(fixture)).isEqualTo(2);
    verify(tossPaymentsClient).approveBilling(anyString(), anyString(), any(), anyString(), any());
  }

  @Test
  void 승인_중_링크가_폐기돼도_수락한_결제_결과를_정확히_돌려준다() throws Exception {
    retireDuringApproval(false);
  }

  @Test
  void 승인_중_링크가_만료돼도_수락한_결제_결과를_정확히_돌려준다() throws Exception {
    retireDuringApproval(true);
  }

  @Test
  void 인보이스_예약을_기다리는_동안_링크가_만료되면_승인하지_않는다() throws Exception {
    changeWhileWaiting("invoice", "expired");
  }

  @Test
  void 인보이스_예약을_기다리는_동안_모드가_바뀌면_옛_캐시로_승인하지_않는다() throws Exception {
    changeWhileWaiting("invoice", "mode");
  }

  @Test
  void 이전_실패행_잠금을_기다리는_동안_키가_교체되면_옛_캐시로_승인하지_않는다() throws Exception {
    changeWhileWaiting("payment_attempt", "credential");
  }

  @Test
  void 최신_실패가_일반_거절이면_더_새_시도가_있는_옛_NOT_FOUND는_7일_창으로_막지_않는다() {
    Fixture fixture = fixture(false, null);
    insertAttempt(fixture, "failed", "NOT_FOUND_PAYMENT", OffsetDateTime.now().minusDays(8));
    insertAttempt(fixture, "failed", "REJECT_CARD_PAYMENT", OffsetDateTime.now().minusMinutes(1));
    String token = value(create(fixture, true), "$.token");
    assertThat(lookup(token)).bodyJson().extractingPath("$.payment_retry.allowed").isEqualTo(true);
    verifyNoInteractions(tossPaymentsClient);
  }

  @Test
  void 요청_시각이_같은_두_실패에서도_재결제는_조회와_같은_실패를_최신으로_본다() {
    Fixture fixture = fixture(false, null);
    OffsetDateTime requestedAt = OffsetDateTime.now().minusDays(8);
    List<UUID> ids =
        List.of(UUID.randomUUID(), UUID.randomUUID()).stream()
            .sorted((left, right) -> left.toString().compareTo(right.toString()))
            .toList();
    insertAttempt(ids.get(0), fixture, "failed", "NOT_FOUND_PAYMENT", requestedAt);
    insertAttempt(ids.get(1), fixture, "failed", "REJECT_CARD_PAYMENT", requestedAt);
    String token = value(create(fixture, true), "$.token");
    String version = value(lookup(token), "$.payment_retry.version");
    when(tossPaymentsClient.approveBilling(anyString(), anyString(), any(), anyString(), any()))
        .thenReturn(approved(fixture));
    assertThat(retry(token, version))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.payment.status")
        .isEqualTo("paid");
  }

  @Test
  void 최신_실패가_확인할_수_없는_NOT_FOUND이면_재시도를_안내하지_않는다() {
    Fixture fixture = fixture(false, null);
    insertAttempt(fixture, "failed", "REJECT_CARD_PAYMENT", OffsetDateTime.now().minusDays(9));
    insertAttempt(fixture, "failed", "NOT_FOUND_PAYMENT", OffsetDateTime.now().minusDays(8));
    String token = value(create(fixture, true), "$.token");
    assertThat(lookup(token))
        .bodyJson()
        .extractingPath("$.payment_retry.reason")
        .isEqualTo("payment_result_unconfirmed");
    assertThat(retry(token, "0".repeat(64))).hasStatus(409);
    assertThat(count(fixture)).isEqualTo(2);
    verifyNoInteractions(tossPaymentsClient);
  }

  private void changeWhileWaiting(String table, String change) throws Exception {
    Fixture fixture =
        fixture(
            true, "payment_attempt".equals(table) ? "NOT_FOUND_PAYMENT" : "REJECT_CARD_PAYMENT");
    MvcTestResult created = create(fixture, true);
    String token = value(created, "$.token");
    String version = value(lookup(token), "$.payment_retry.version");
    when(tossPaymentsClient.findPaymentByOrderId(anyString(), anyString()))
        .thenReturn(new TossPaymentsResult.Rejected<>(404, "NOT_FOUND_PAYMENT", "없는 결제"));
    try (var executor = Executors.newFixedThreadPool(1);
        Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try (var statement =
          connection.prepareStatement("SELECT id FROM " + table + " WHERE id=? FOR UPDATE")) {
        statement.setObject(
            1, "invoice".equals(table) ? fixture.invoiceId() : fixture.failedAttemptId());
        statement.executeQuery().close();
      }
      var response = executor.submit(() -> retry(token, version));
      try {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(8);
        int waiting = 0;
        while (System.nanoTime() < deadline) {
          waiting =
              jdbcTemplate.queryForObject(
                  "SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND state='active' AND wait_event_type='Lock' AND lower(query) LIKE '%"
                      + table
                      + "%'",
                  Integer.class);
          if (waiting > 0) break;
          Thread.sleep(10);
        }
        assertThat(waiting).isPositive();
        if ("expired".equals(change))
          jdbcTemplate.update(
              "UPDATE invoice_view_link SET created_at=now()-interval '2 days',expires_at=now()-interval '1 second' WHERE id=?",
              UUID.fromString(value(created, "$.id")));
        if ("mode".equals(change))
          jdbcTemplate.update(
              "INSERT INTO payment_setting(organization_id,mode,updated_at) VALUES (?,'live',now())",
              fixture.organizationId());
        if ("credential".equals(change))
          jdbcTemplate.update(
              "UPDATE toss_payments_credential SET updated_at=now() WHERE organization_id=?",
              fixture.organizationId());
      } finally {
        connection.commit();
      }
      assertThat(response.get(10, TimeUnit.SECONDS))
          .hasStatus("expired".equals(change) ? 404 : 409);
    }
    assertThat(count(fixture)).isEqualTo(1);
    verify(tossPaymentsClient, never())
        .approveBilling(anyString(), anyString(), any(), anyString(), any());
  }

  private void retireDuringApproval(boolean isExpired) throws Exception {
    Fixture fixture = fixture(true, "REJECT_CARD_PAYMENT");
    MvcTestResult created = create(fixture, true);
    String token = value(created, "$.token");
    String version = value(lookup(token), "$.payment_retry.version");
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    when(tossPaymentsClient.approveBilling(anyString(), anyString(), any(), anyString(), any()))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              entered.countDown();
              assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
              return approved(fixture);
            });
    try (var executor = Executors.newFixedThreadPool(1)) {
      var response = executor.submit(() -> retry(token, version));
      assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
      if (isExpired)
        jdbcTemplate.update(
            "UPDATE invoice_view_link SET created_at=now()-interval '2 days',expires_at=now()-interval '1 second' WHERE id=?",
            UUID.fromString(value(created, "$.id")));
      else assertThat(revoke(fixture, value(created, "$.id"))).hasStatusOk();
      release.countDown();
      MvcTestResult result = response.get(10, TimeUnit.SECONDS);
      assertThat(result)
          .hasStatusOk()
          .bodyJson()
          .extractingPath("$.payment.status")
          .isEqualTo("paid");
      assertThat(result).bodyJson().extractingPath("$.payment_retry.allowed").isEqualTo(false);
      assertThat(lookup(token)).hasStatus(404);
      assertThat(retry(token, version)).hasStatus(404);
    } finally {
      release.countDown();
    }
    assertThat(count(fixture)).isEqualTo(2);
    verify(tossPaymentsClient).approveBilling(anyString(), anyString(), any(), anyString(), any());
  }

  private void concurrentRetry(String otherKind) throws Exception {
    Fixture fixture =
        fixture(
            true,
            "NOT_FOUND_PAYMENT",
            "automatic".equals(otherKind)
                ? OffsetDateTime.now().minusHours(25)
                : OffsetDateTime.now().minusMinutes(1));
    String token = value(create(fixture, true), "$.token");
    String version = value(lookup(token), "$.payment_retry.version");
    CountDownLatch lookups = new CountDownLatch(2);
    when(tossPaymentsClient.findPaymentByOrderId(anyString(), anyString()))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              lookups.countDown();
              assertThat(lookups.await(10, TimeUnit.SECONDS)).isTrue();
              return new TossPaymentsResult.Rejected<>(404, "NOT_FOUND_PAYMENT", "없는 결제");
            });
    rejectApproval();
    try (var executor = Executors.newFixedThreadPool(2)) {
      var customer = executor.submit(() -> retry(token, version));
      var other =
          executor.submit(
              () -> {
                if ("automatic".equals(otherKind)) {
                  paymentRetryService.retry();
                  return 200;
                }
                if ("admin".equals(otherKind))
                  return mvc.post()
                      .uri("/v1/invoices/{id}/payment-attempts", fixture.invoiceId())
                      .header("Authorization", fixture.authorization())
                      .exchange()
                      .getResponse()
                      .getStatus();
                return retry(token, version).getResponse().getStatus();
              });
      int customerStatus = customer.get(15, TimeUnit.SECONDS).getResponse().getStatus();
      int otherStatus = other.get(15, TimeUnit.SECONDS);
      assertThat(customerStatus).isIn(200, 409);
      assertThat(otherStatus).isIn(200, 201, 409);
      if (!"automatic".equals(otherKind))
        assertThat(List.of(customerStatus, otherStatus)).contains(409);
    }
    assertThat(count(fixture)).isEqualTo(2);
    verify(tossPaymentsClient).approveBilling(anyString(), anyString(), any(), anyString(), any());
  }

  private void rejectApproval() {
    when(tossPaymentsClient.approveBilling(anyString(), anyString(), any(), anyString(), any()))
        .thenReturn(new TossPaymentsResult.Rejected<>(403, "REJECT_CARD_PAYMENT", "로컬 거절"));
  }

  private TossPaymentsResult<TossPaymentsPayment> approved(Fixture fixture) {
    return new TossPaymentsResult.Success<>(
        new TossPaymentsPayment(
            "mock-payment-" + fixture.organizationId(),
            "mock-order",
            "DONE",
            1100L,
            1100L,
            List.of()));
  }

  private Fixture fixture(boolean hasFailure, String failureCode) {
    return fixture(hasFailure, failureCode, OffsetDateTime.now().minusMinutes(1));
  }

  private Fixture fixture(boolean hasFailure, String failureCode, OffsetDateTime requestedAt) {
    UUID organizationId = UUID.randomUUID();
    String authorization =
        TestCredentials.issueApiKey(jdbcTemplate, organizationId, "test", "admin");
    UUID customerId = UUID.randomUUID();
    UUID invoiceId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO customer(id,organization_id,name) VALUES (?,?,'재시도 고객')",
        customerId,
        organizationId);
    jdbcTemplate.update(
        "INSERT INTO invoice(id,organization_id,customer_id,customer_name,period,supply_amount,tax_amount,total_amount,cutoff_at,finalized_at) VALUES (?,?,?,'재시도 고객','2026-10',1000,100,1100,now(),now())",
        invoiceId,
        organizationId,
        customerId);
    OffsetDateTime before = OffsetDateTime.now().minusDays(3);
    credentialRepository.saveAndFlush(
        new TossPaymentsCredential(
            organizationId, "test", "test_ck_customer_retry_mock_00000001", SECRET_KEY, before));
    billingKeyRepository.saveAndFlush(
        new BillingKey(
            organizationId,
            customerId,
            "test",
            BILLING_KEY,
            "61",
            "1234****5678",
            before,
            "2026-10-01",
            before));
    Fixture fixture = new Fixture(organizationId, customerId, invoiceId, authorization, null);
    UUID failed = hasFailure ? insertAttempt(fixture, "failed", failureCode, requestedAt) : null;
    return new Fixture(organizationId, customerId, invoiceId, authorization, failed);
  }

  private UUID insertAttempt(
      Fixture fixture, String status, String code, OffsetDateTime requestedAt) {
    return insertAttempt(UUID.randomUUID(), fixture, status, code, requestedAt);
  }

  private UUID insertAttempt(
      UUID id, Fixture fixture, String status, String code, OffsetDateTime requestedAt) {
    jdbcTemplate.update(
        "INSERT INTO payment_attempt(id,organization_id,customer_id,invoice_id,mode,amount,order_name,status,payment_key,failure_code,requested_at,completed_at) VALUES (?,?,?,?,'test',1100,'2026-10 사용료',?,?,?,?,?)",
        id,
        fixture.organizationId(),
        fixture.customerId(),
        fixture.invoiceId(),
        status,
        "done".equals(status) ? "existing-mock-" + id : null,
        code,
        requestedAt,
        "pending".equals(status) ? null : requestedAt.plusSeconds(1));
    return id;
  }

  private int count(Fixture fixture) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM payment_attempt WHERE invoice_id=?",
        Integer.class,
        fixture.invoiceId());
  }

  private MvcTestResult create(Fixture fixture, boolean allow) {
    return mvc.post()
        .uri("/v1/invoices/{id}/view-links", fixture.invoiceId())
        .header("Authorization", fixture.authorization())
        .contentType(MediaType.APPLICATION_JSON)
        .content(allow ? "{\"allow_payment_retry\":true}" : "{}")
        .exchange();
  }

  private MvcTestResult revoke(Fixture fixture, String id) {
    return mvc.post()
        .uri("/v1/invoices/{id}/view-links/{link}/revoke", fixture.invoiceId(), id)
        .header("Authorization", fixture.authorization())
        .exchange();
  }

  private MvcTestResult lookup(String token) {
    return mvc.post()
        .uri("/v1/public/invoice-links/lookup")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"token\":\"" + token + "\"}")
        .exchange();
  }

  private MvcTestResult retry(String token, String version) {
    return mvc.post()
        .uri("/v1/public/invoice-links/payment-retries")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"token\":\"" + token + "\",\"retry_version\":\"" + version + "\"}")
        .exchange();
  }

  private static String value(MvcTestResult result, String path) {
    return JsonPath.read(
        new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8), path);
  }

  private record Fixture(
      UUID organizationId,
      UUID customerId,
      UUID invoiceId,
      String authorization,
      UUID failedAttemptId) {}
}
