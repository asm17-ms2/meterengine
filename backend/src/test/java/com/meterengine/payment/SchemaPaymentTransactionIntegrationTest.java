package com.meterengine.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meterengine.MeteringEnvironmentTestSupport;
import com.meterengine.global.database.DatabaseScope;
import com.meterengine.global.database.DatabaseScopes;
import com.meterengine.global.database.PaymentEnvironmentTestConfig;
import com.meterengine.invoice.entity.Invoice;
import com.meterengine.payment.client.TossPaymentsApproveBillingRequest;
import com.meterengine.payment.client.TossPaymentsCancelRequest;
import com.meterengine.payment.client.TossPaymentsClient;
import com.meterengine.payment.client.TossPaymentsPayment;
import com.meterengine.payment.client.TossPaymentsResult;
import com.meterengine.payment.entity.BillingKey;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest(
    classes = PaymentEnvironmentTestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.docker.compose.enabled=false",
      "spring.jpa.open-in-view=false",
      "METERENGINE_DEPLOYMENT_ENVIRONMENT=test",
      "METERENGINE_CUTOVER_MODE=active"
    })
@ActiveProfiles("test")
class SchemaPaymentTransactionIntegrationTest extends MeteringEnvironmentTestSupport {
  @MockitoBean TossPaymentsClient toss;
  @Autowired com.meterengine.invoice.repository.InvoiceRepository invoices;
  @Autowired com.meterengine.payment.repository.BillingKeyRepository keys;
  @Autowired com.meterengine.payment.repository.TossPaymentsCredentialRepository credentials;
  @Autowired jakarta.persistence.EntityManager entityManager;
  @Autowired com.meterengine.payment.service.PaymentMethodRegistrationService registrations;
  @Autowired com.meterengine.payment.service.BillingKeyService billingKeys;
  @Autowired com.meterengine.payment.repository.PaymentArchiveRepository archives;
  @Autowired com.meterengine.payment.service.PaymentAttemptService attempts;
  @Autowired com.meterengine.payment.service.PaymentMethodService paymentMethods;
  @Autowired com.meterengine.payment.repository.PaymentAttemptRepository attemptRepository;

  @BeforeEach
  void prepareToss() {
    when(toss.findPaymentByOrderId(anyString(), anyString()))
        .thenReturn(new TossPaymentsResult.Rejected<>(404, "NOT_FOUND_PAYMENT", "fixture"));
    when(toss.approveBilling(anyString(), anyString(), any(), anyString(), isNull()))
        .thenAnswer(
            invocation -> {
              TossPaymentsApproveBillingRequest approval = invocation.getArgument(2);
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              String mode = DatabaseScopes.current() == DatabaseScope.TEST ? "test" : "live";
              assertThat(invocation.<String>getArgument(0)).isEqualTo(mode + "_sk_fixture");
              assertThat(invocation.<String>getArgument(1)).isEqualTo(mode + "-billing");
              assertThat(invocation.<String>getArgument(3)).isEqualTo(approval.orderId());
              assertThat(approval.amount()).isEqualTo(1100);
              assertThat(
                      ownerJdbc()
                          .queryForObject(
                              "SELECT status FROM " + mode + ".payment_attempt WHERE id=?",
                              String.class,
                              UUID.fromString(approval.orderId())))
                  .isEqualTo("pending");
              assertThat(
                      ownerJdbc()
                          .queryForObject(
                              "SELECT count(*) FROM shared.audit_log WHERE organization_id=? AND action='payment_attempt_created'",
                              Long.class,
                              organizationId))
                  .isGreaterThan(0);
              return new TossPaymentsResult.Success<>(
                  new TossPaymentsPayment(
                      "payment-" + approval.orderId(),
                      approval.orderId(),
                      "DONE",
                      approval.amount(),
                      approval.amount(),
                      List.of()));
            });
    when(toss.cancel(anyString(), anyString(), any(), anyString()))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              TossPaymentsCancelRequest cancel = invocation.getArgument(2);
              String mode = DatabaseScopes.current() == DatabaseScope.TEST ? "test" : "live";
              assertThat(invocation.<String>getArgument(0)).isEqualTo(mode + "_sk_fixture");
              assertThat(
                      ownerJdbc()
                          .queryForObject(
                              "SELECT status FROM " + mode + ".payment_cancellation WHERE id=?",
                              String.class,
                              UUID.fromString(invocation.getArgument(3))))
                  .isEqualTo("pending");
              return new TossPaymentsResult.Success<>(
                  new TossPaymentsPayment(
                      invocation.getArgument(1),
                      "fixture",
                      "DONE",
                      1100,
                      1100 - cancel.cancelAmount(),
                      List.of(
                          new TossPaymentsPayment.Cancel(
                              "transaction-" + invocation.getArgument(3),
                              cancel.cancelAmount(),
                              OffsetDateTime.now(),
                              "DONE"))));
            });
  }

  @org.junit.jupiter.api.AfterEach
  void verifyTransactionsClosed() {
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    assertThat(TransactionSynchronizationManager.getResourceMap().values())
        .noneMatch(
            value ->
                value instanceof org.springframework.orm.jpa.EntityManagerHolder holder
                    && holder.getEntityManager().isOpen());
    assertThat(DatabaseScopes.current()).isEqualTo(DatabaseScope.SHARED);
  }

  @Test
  void 선택한_환경의_PG_자격과_고정_모드를_독립적으로_보존한다() {
    when(toss.findPaymentByOrderId(anyString(), anyString()))
        .thenReturn(new TossPaymentsResult.Rejected<>(404, "NOT_FOUND_PAYMENT", "fixture"));
    for (String mode : List.of("test", "live")) {
      var response =
          request(
              "PUT",
              "/v1/toss-payments-credentials/" + mode,
              session,
              mode,
              "{\"client_key\":\""
                  + mode
                  + "_ck_fixture\",\"secret_key\":\""
                  + mode
                  + "_sk_fixture\"}");
      assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
      var setting = get("/v1/payment-setting", mode);
      assertThat(setting.statusCode()).as(setting.body()).isEqualTo(200);
      assertThat(json(setting).get("mode").asString()).isEqualTo(mode);
      assertThat(json(setting).get("updated_at").isNull()).isTrue();
    }
  }

  @Test
  void 제거된_모드변경_API와_반대_환경_자격은_저장과_감사와_PG호출을_남기지_않는다() {
    for (String mode : List.of("test", "live")) {
      var removed =
          request("PUT", "/v1/payment-setting", session, mode, "{\"mode\":\"" + mode + "\"}");
      assertThat(removed.statusCode()).as(removed.body()).isEqualTo(405);
      String other = mode.equals("test") ? "live" : "test";
      var rejected =
          request(
              "PUT",
              "/v1/toss-payments-credentials/" + other,
              session,
              mode,
              "{\"client_key\":\""
                  + other
                  + "_ck_fixture\",\"secret_key\":\""
                  + other
                  + "_sk_fixture\"}");
      assertThat(rejected.statusCode()).as(rejected.body()).isEqualTo(409);
      transactions.read(
          scope(mode),
          () -> {
            assertThat(
                    jdbc.queryForObject(
                        "SELECT count(*) FROM payment_setting WHERE organization_id=?",
                        Long.class,
                        organizationId))
                .isZero();
            assertThat(
                    jdbc.queryForObject(
                        "SELECT count(*) FROM toss_payments_credential WHERE organization_id=?",
                        Long.class,
                        organizationId))
                .isZero();
            return null;
          });
    }
    verify(toss, never()).findPaymentByOrderId(anyString(), anyString());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM shared.audit_log WHERE organization_id=? AND action <> 'api_request_completed'",
                Long.class,
                organizationId))
        .isZero();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM shared.audit_log WHERE organization_id=? AND action = 'api_request_completed'",
                Long.class,
                organizationId))
        .isEqualTo(4);
  }

  @Test
  void 실제_사용량과_가격의_청구를_승인하고_부분과_전액취소와_멱등재생을_보존한다() {
    UUID customer = UUID.randomUUID();
    UUID key = UUID.randomUUID();
    Map<String, UUID> cancellationIds = new java.util.HashMap<>();
    for (String mode : List.of("test", "live")) {
      UUID invoice = preparePayable(mode, customer, true);
      transactions.write(
          scope(mode),
          () -> {
            jdbc.update(
                "INSERT INTO webhook_endpoint(id,organization_id,url,secret,event_types) VALUES (?,?,'https://fixture.invalid','fixture',ARRAY['payment_succeeded','payment_canceled'])",
                UUID.randomUUID(),
                organizationId);
            return null;
          });
      var attempt = charge(mode, invoice);
      assertThat(attempt.statusCode()).as(attempt.body()).isEqualTo(201);
      assertThat(json(attempt).get("status").asString()).isEqualTo("done");
      assertThat(json(attempt).get("mode").asString()).isEqualTo(mode);
      UUID attemptId = UUID.fromString(json(attempt).get("id").asString());
      saveCredential(mode, "rotated-before-cancellation");
      var partial = cancel(mode, attemptId, key, "{\"amount\":300,\"reason\":\"fixture\"}");
      assertThat(partial.statusCode()).as(partial.body()).isEqualTo(201);
      assertThat(json(partial).get("status").asString()).isEqualTo("done");
      cancellationIds.put(mode, UUID.fromString(json(partial).get("id").asString()));
      var replay = cancel(mode, attemptId, key, "{\"amount\":300,\"reason\":\"fixture\"}");
      assertThat(replay.body()).isEqualTo(partial.body());
      assertThat(
              cancel(mode, attemptId, key, "{\"amount\":301,\"reason\":\"fixture\"}").statusCode())
          .isEqualTo(409);
      assertThat(
              cancel(mode, attemptId, UUID.randomUUID(), "{\"amount\":801,\"reason\":\"fixture\"}")
                  .statusCode())
          .isEqualTo(409);
      var full = cancel(mode, attemptId, UUID.randomUUID(), "{\"reason\":\"fixture\"}");
      assertThat(full.statusCode()).as(full.body()).isEqualTo(201);
      assertThat(json(full).get("amount").asLong()).isEqualTo(800);
      var payments = get("/v1/invoice-payments?month=" + month(), mode);
      assertThat(payments.statusCode()).as(payments.body()).isEqualTo(200);
      assertThat(json(payments).at("/invoice_payments/0/status").asString()).isEqualTo("canceled");
      transactions.read(
          scope(mode),
          () -> {
            assertThat(
                    jdbc.queryForObject(
                        "SELECT count(*) FROM webhook_event WHERE organization_id=?",
                        Long.class,
                        organizationId))
                .isEqualTo(3);
            assertThat(
                    jdbc.queryForObject(
                        "SELECT count(*) FROM webhook_delivery WHERE organization_id=?",
                        Long.class,
                        organizationId))
                .isEqualTo(3);
            assertThat(
                    jdbc.queryForObject(
                        "SELECT sum(amount) FROM payment_cancellation WHERE organization_id=? AND status='done'",
                        Long.class,
                        organizationId))
                .isEqualTo(1100);
            return null;
          });
    }
    assertThat(cancellationIds.get("test")).isEqualTo(contractId(organizationId, key, false));
    assertThat(cancellationIds.get("live"))
        .isEqualTo(contractId(organizationId, key, true))
        .isNotEqualTo(cancellationIds.get("test"));
    verify(toss, times(2)).approveBilling(anyString(), anyString(), any(), anyString(), isNull());
    verify(toss, times(4)).cancel(anyString(), anyString(), any(), anyString());
  }

  @Test
  void 기존_테스트_취소행의_부분완료와_실패와_진행과_null전액은_같은_ID로_재생한다() {
    for (String state : List.of("done", "failed", "pending", "null-full")) {
      UUID customer = UUID.randomUUID();
      UUID invoice = preparePayable("test", customer, false);
      var attempt = charge("test", invoice);
      assertThat(attempt.statusCode()).as(attempt.body()).isEqualTo(201);
      UUID attemptId = UUID.fromString(json(attempt).get("id").asString());
      UUID key = UUID.randomUUID();
      UUID id = contractId(organizationId, key, false);
      Long amount = state.equals("null-full") ? null : 100L;
      String fp = fingerprint(attemptId, amount, "legacy-fixture");
      String status = state.equals("null-full") ? "done" : state;
      ownerJdbc()
          .update(
              "INSERT INTO test.payment_cancellation(id,organization_id,payment_attempt_id,mode,amount,reason,idempotency_fingerprint,status,requested_at,completed_at,transaction_key,failure_code) VALUES (?,?,?,'test',?,'legacy-fixture',?,?,now(),?, ?,?)",
              id,
              organizationId,
              attemptId,
              amount == null ? 1100 : amount,
              fp,
              status,
              status.equals("pending") ? null : OffsetDateTime.now(),
              status.equals("done") ? "legacy-" + id : null,
              status.equals("failed") ? "REJECTED" : null);
      clearInvocations(toss);
      String body =
          amount == null
              ? "{\"reason\":\"legacy-fixture\"}"
              : "{\"amount\":100,\"reason\":\"legacy-fixture\"}";
      var replay = cancel("test", attemptId, key, body);
      assertThat(replay.statusCode()).as(replay.body()).isEqualTo(201);
      assertThat(json(replay).get("id").asString()).isEqualTo(id.toString());
      assertThat(json(replay).get("status").asString()).isEqualTo(status);
      assertThat(json(replay).get("amount").asLong()).isEqualTo(amount == null ? 1100 : 100);
      assertThat(
              cancel("test", attemptId, key, "{\"amount\":1100,\"reason\":\"legacy-fixture\"}")
                  .statusCode())
          .isEqualTo(409);
      assertThat(cancel("test", attemptId, key, "{\"reason\":\"changed\"}").statusCode())
          .isEqualTo(409);
      verify(toss, never()).cancel(anyString(), anyString(), any(), anyString());
      assertThat(
              ownerJdbc()
                  .queryForObject(
                      "SELECT count(*) FROM test.payment_cancellation WHERE id=?", Long.class, id))
          .isEqualTo(1);
    }
  }

  @Test
  void 같은_취소키로_다른_완료시도를_요청하면_409이고_원장과_PG는_변하지_않는다() {
    UUID key = UUID.randomUUID();
    for (String mode : List.of("test", "live")) {
      UUID firstInvoice = preparePayable(mode, UUID.randomUUID(), false);
      var first = charge(mode, firstInvoice);
      assertThat(first.statusCode()).as(first.body()).isEqualTo(201);
      UUID firstAttempt = UUID.fromString(json(first).get("id").asString());
      String body = "{\"amount\":300,\"reason\":\"attempt-conflict\"}";
      var original = cancel(mode, firstAttempt, key, body);
      assertThat(original.statusCode()).as(original.body()).isEqualTo(201);
      UUID cancellation = contractId(organizationId, key, mode.equals("live"));
      assertThat(json(original).get("id").asString()).isEqualTo(cancellation.toString());
      UUID secondInvoice = preparePayable(mode, UUID.randomUUID(), false);
      var second = charge(mode, secondInvoice);
      assertThat(second.statusCode()).as(second.body()).isEqualTo(201);
      assertThat(json(second).get("status").asString()).isEqualTo("done");
      UUID secondAttempt = UUID.fromString(json(second).get("id").asString());
      assertThat(secondAttempt).isNotEqualTo(firstAttempt);
      var before =
          transactions.read(
              scope(mode),
              () ->
                  jdbc.queryForList(
                      "SELECT * FROM payment_cancellation WHERE organization_id=? ORDER BY id",
                      organizationId));
      assertThat(before).hasSize(1);
      assertThat(before.getFirst())
          .containsEntry("id", cancellation)
          .containsEntry("payment_attempt_id", firstAttempt)
          .containsEntry("status", "done")
          .containsEntry("amount", 300L);
      clearInvocations(toss);
      var conflict = cancel(mode, secondAttempt, key, body);
      assertThat(conflict.statusCode()).as(conflict.body()).isEqualTo(409);
      var after =
          transactions.read(
              scope(mode),
              () ->
                  jdbc.queryForList(
                      "SELECT * FROM payment_cancellation WHERE organization_id=? ORDER BY id",
                      organizationId));
      assertThat(after).isEqualTo(before);
      org.mockito.Mockito.verifyNoInteractions(toss);
      var replay = cancel(mode, firstAttempt, key, body);
      assertThat(replay.statusCode()).as(replay.body()).isEqualTo(201);
      assertThat(json(replay).get("id").asString()).isEqualTo(cancellation.toString());
      assertThat(json(replay).get("amount").asLong()).isEqualTo(300);
      org.mockito.Mockito.verifyNoInteractions(toss);
    }
  }

  @Test
  void 자격_교체와_감사실패는_현재키와_이력을_같이_롤백한다() {
    saveCredential("test", "fixture");
    String before = credentials("test");
    rejectAudit(
        "toss_payments_credential_updated",
        () -> assertThat(saveCredentialResponse("test", "new").statusCode()).isEqualTo(500));
    assertThat(credentials("test")).isEqualTo(before);
    assertThat(
            ownerJdbc()
                .queryForObject(
                    "SELECT count(*) FROM test.toss_payments_credential_history WHERE organization_id=?",
                    Long.class,
                    organizationId))
        .isZero();
    saveCredential("test", "new");
    assertThat(
            ownerJdbc()
                .queryForObject(
                    "SELECT count(*) FROM test.toss_payments_credential_history WHERE organization_id=?",
                    Long.class,
                    organizationId))
        .isEqualTo(1);
  }

  @Test
  void JPA와_JDBC는_같은_업무_역할과_스키마와_PID를_쓴다() {
    for (String mode : List.of("test", "live")) {
      transactions.write(
          scope(mode),
          () -> {
            Object[] jpa =
                (Object[])
                    entityManager
                        .createNativeQuery("SELECT current_user,current_schema(),pg_backend_pid()")
                        .getSingleResult();
            var sql =
                jdbc.queryForMap(
                    "SELECT current_user AS role,current_schema() AS schema,pg_backend_pid() AS pid");
            assertThat(jpa).containsExactly("meterengine_" + mode, mode, sql.get("pid"));
            assertThat(sql)
                .containsEntry("role", "meterengine_" + mode)
                .containsEntry("schema", mode);
            return null;
          });
    }
  }

  private String credentials(String mode) {
    return get("/v1/toss-payments-credentials", mode).body();
  }

  @Test
  void 이력조회_SQL은_실제_선택역할에서_사용중인_자격버전과_정렬을_보존한다() {
    UUID customer = UUID.randomUUID();
    UUID invoice = preparePayable("test", customer, false);
    var payment = charge("test", invoice);
    assertThat(payment.statusCode()).as(payment.body()).isEqualTo(201);
    UUID attempt = UUID.fromString(json(payment).get("id").asString());
    saveCredential("test", "unused");
    saveCredential("test", "current");
    var inUse =
        transactions.read(
            scope("test"),
            () ->
                archives.credentialVersionsInUse(
                    organizationId, "test", OffsetDateTime.now().minusDays(1)));
    assertThat(inUse).hasSize(1);
    assertThat(inUse.getFirst().getSecretKey()).isEqualTo("test_sk_fixture");
    var unused =
        transactions.read(
            scope("test"),
            () ->
                archives.credentialVersionsInUse(
                    organizationId, "test", OffsetDateTime.now().plusDays(1)));
    assertThat(unused).isEmpty();
    var history = transactions.read(scope("test"), () -> archives.credentials(organizationId));
    assertThat(history).hasSize(2);
    assertThat(history.getFirst().clientKey()).isEqualTo("test_ck_unused");
    doAnswer(invocation -> new TossPaymentsResult.Unknown<TossPaymentsPayment>("fixture-pending"))
        .when(toss)
        .cancel(anyString(), anyString(), any(), anyString());
    assertThat(
            cancel(
                    "test",
                    attempt,
                    UUID.randomUUID(),
                    "{\"amount\":100,\"reason\":\"pending-version\"}")
                .statusCode())
        .isEqualTo(201);
    var pinned =
        transactions.read(
            scope("test"),
            () ->
                archives.credentialVersionsInUse(
                    organizationId, "test", OffsetDateTime.now().plusDays(1)));
    assertThat(pinned).hasSize(1);
    assertThat(pinned.getFirst().getSecretKey()).isEqualTo("test_sk_fixture");
    transactions.write(
        scope("test"),
        () -> {
          archives.lock(organizationId);
          archives.method(organizationId, customer, "test");
          return null;
        });
    assertThat(transactions.read(scope("test"), () -> archives.methods(organizationId, "test")))
        .hasSize(1);
    assertThat(
            transactions.read(
                scope("test"), () -> archives.methods(organizationId, "test", customer)))
        .hasSize(1);
    assertThat(transactions.read(scope("live"), () -> archives.credentials(organizationId)))
        .isEmpty();
  }

  @Test
  void 다른환경_가격ID와_고객FK와_미터기간FK는_실제_역할에서_거절된다() {
    UUID test = UUID.randomUUID();
    UUID live = UUID.randomUUID();
    createCustomer("test", test, "test-only-customer");
    createCustomer("live", live, "live-only-customer");
    assertThat(createMetric("live", "scope-price", "sum").statusCode()).isEqualTo(201);
    var rate =
        request(
            "POST",
            "/v1/billable-metrics/scope-price/price-rates",
            session,
            "live",
            "{\"dimension_values\":{},\"unit_price\":10}");
    assertThat(rate.statusCode()).as(rate.body()).isEqualTo(201);
    assertThat(
            request(
                    "DELETE",
                    "/v1/billable-metrics/scope-price/price-rates/"
                        + json(rate).get("id").asString(),
                    session,
                    "test",
                    null)
                .statusCode())
        .isEqualTo(404);
    assertThat(createMetric("test", "test-only", "sum").statusCode()).isEqualTo(201);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                transactions.write(
                    scope("test"),
                    () ->
                        jdbc.update(
                            "INSERT INTO price_plan(id,organization_id,customer_id,effective_from,base_fee,minimum_amount) VALUES (?,?,?,?,100,0)",
                            UUID.randomUUID(),
                            organizationId,
                            live,
                            month().toString())))
        .isInstanceOf(org.springframework.dao.DataAccessException.class)
        .rootCause()
        .extracting(e -> ((java.sql.SQLException) e).getSQLState())
        .isEqualTo("23503");
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                transactions.write(
                    scope("live"),
                    () ->
                        jdbc.update(
                            "INSERT INTO billable_metric_period(id,organization_id,billable_metric_code,active_from,active_until) VALUES (?,?,'test-only',?,?)",
                            UUID.randomUUID(),
                            organizationId,
                            month().minusMonths(1).toString(),
                            month().toString())))
        .isInstanceOf(org.springframework.dao.DataAccessException.class)
        .rootCause()
        .extracting(e -> ((java.sql.SQLException) e).getSQLState())
        .isEqualTo("23503");
    assertThat(get("/v1/billable-metrics/test-only", "legacy").statusCode()).isEqualTo(400);
  }

  @Test
  void 실제_PG거절은_실패원장과_이벤트를_남기고_새수동결제는_새시도로_승인한다() {
    UUID invoice = preparePayable("test", UUID.randomUUID(), false);
    org.mockito.Mockito.doReturn(
            new TossPaymentsResult.Rejected<TossPaymentsPayment>(
                400, "REJECT_CARD_COMPANY", "fixture"))
        .when(toss)
        .approveBilling(anyString(), anyString(), any(), anyString(), isNull());
    var failed = charge("test", invoice);
    assertThat(failed.statusCode()).as(failed.body()).isEqualTo(201);
    assertThat(json(failed).get("status").asString()).isEqualTo("failed");
    assertThat(json(failed).get("failure_code").asString()).isEqualTo("REJECT_CARD_COMPANY");
    assertThat(
            ownerJdbc()
                .queryForObject(
                    "SELECT count(*) FROM test.webhook_event WHERE organization_id=? AND type='payment_failed'",
                    Long.class,
                    organizationId))
        .isEqualTo(1);
    stubPinnedApproval("test_sk_fixture", "test-billing");
    var done = charge("test", invoice);
    assertThat(done.statusCode()).as(done.body()).isEqualTo(201);
    assertThat(json(done).get("status").asString()).isEqualTo("done");
    assertThat(json(done).get("id").asString()).isNotEqualTo(json(failed).get("id").asString());
    assertThat(
            ownerJdbc()
                .queryForObject(
                    "SELECT count(*) FROM test.payment_attempt WHERE invoice_id=?",
                    Long.class,
                    invoice))
        .isEqualTo(2);
    assertThat(
            ownerJdbc()
                .queryForObject(
                    "SELECT count(*) FROM test.webhook_event WHERE organization_id=?",
                    Long.class,
                    organizationId))
        .isEqualTo(2);
  }

  @Test
  void 평균과_백분위와_건수는_원래_40과_30과_2와_1을_환경별로_계산한다() {
    UUID customer = UUID.randomUUID();
    for (String mode : List.of("test", "live")) {
      createCustomer(mode, customer, "aggregate-original");
      for (String aggregation : List.of("avg", "percentile", "count")) {
        var response =
            request(
                "POST",
                "/v1/billable-metrics",
                session,
                mode,
                "{\"code\":\""
                    + aggregation
                    + "\",\"name\":\""
                    + aggregation
                    + "\",\"event_type\":\"tokens\",\"aggregation\":\""
                    + aggregation
                    + "\""
                    + (aggregation.equals("count") ? "" : ",\"target_property\":\"quantity\"")
                    + (aggregation.equals("percentile") ? ",\"percentile\":50" : "")
                    + "}");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
      }
      assertThat(
              request(
                      "POST",
                      "/v1/events",
                      session,
                      mode,
                      eventJson(
                          "aggregate-first",
                          customer,
                          "{\"quantity\":" + (mode.equals("test") ? 20 : 30) + "}"))
                  .statusCode())
          .isEqualTo(200);
    }
    assertThat(
            request(
                    "POST",
                    "/v1/events",
                    session,
                    "test",
                    eventJson("aggregate-second", customer, "{\"quantity\":60}"))
                .statusCode())
        .isEqualTo(200);
    for (String mode : List.of("test", "live")) {
      var usage = get("/v1/usage?month=" + month(), mode);
      assertThat(usage.statusCode()).as(usage.body()).isEqualTo(200);
      assertThat(json(usage).get("billable_metric_usages").size()).isEqualTo(3);
      for (var metric : json(usage).get("billable_metric_usages"))
        assertThat(metric.at("/customers/0/quantity").decimalValue())
            .isEqualByComparingTo(
                metric.get("code").asString().equals("count")
                    ? (mode.equals("test") ? "2" : "1")
                    : (mode.equals("test") ? "40" : "30"));
    }
  }

  @Test
  void 원래_이벤트진단과_청구제외_경고는_다른_환경과_가격을_섞지_않는다() {
    UUID test = UUID.randomUUID();
    UUID live = UUID.randomUUID();
    for (String mode : List.of("test", "live")) {
      UUID customer = mode.equals("test") ? test : live;
      createCustomer(mode, customer, "diagnostic-original");
      assertThat(createMetric(mode, "sum", "sum").statusCode()).isEqualTo(201);
      assertThat(
              request(
                      "POST",
                      "/v1/billable-metrics/sum/price-rates",
                      session,
                      mode,
                      "{\"dimension_values\":{},\"unit_price\":10}")
                  .statusCode())
          .isEqualTo(201);
      assertThat(
              request(
                      "POST",
                      "/v1/events",
                      session,
                      mode,
                      eventJson("initial", customer, "{\"quantity\":20}"))
                  .statusCode())
          .isEqualTo(200);
    }
    assertThat(
            request(
                    "POST",
                    "/v1/billable-metrics",
                    session,
                    "live",
                    "{\"code\":\"live-only\",\"name\":\"live-only\",\"event_type\":\"cross-usage\",\"aggregation\":\"sum\",\"target_property\":\"quantity\"}")
                .statusCode())
        .isEqualTo(201);
    assertThat(
            request(
                    "POST",
                    "/v1/events",
                    session,
                    "test",
                    eventJson("unknown-test", test, "{\"quantity\":10}")
                        .replace("\"type\":\"tokens\"", "\"type\":\"cross-usage\""))
                .statusCode())
        .isEqualTo(200);
    assertThat(
            request(
                    "POST",
                    "/v1/events",
                    session,
                    "test",
                    eventJson("negative-test", test, "{\"quantity\":-5}"))
                .statusCode())
        .isEqualTo(200);
    assertThat(
            request(
                    "POST",
                    "/v1/events",
                    session,
                    "live",
                    eventJson("invalid-live", live, "{\"quantity\":\"invalid\"}"))
                .statusCode())
        .isEqualTo(200);
    assertThat(
            request(
                    "POST",
                    "/v1/events",
                    session,
                    "live",
                    eventJson("unpriced-live", live, "{\"quantity\":7}")
                        .replace("\"type\":\"tokens\"", "\"type\":\"cross-usage\""))
                .statusCode())
        .isEqualTo(200);
    for (String mode : List.of("test", "live")) {
      var issues = get("/v1/event-issues/search?month=" + month(), mode);
      assertThat(issues.statusCode()).as(issues.body()).isEqualTo(200);
      var value = json(issues);
      assertThat(value.at("/summary/unknown_event_type").asInt())
          .isEqualTo(mode.equals("test") ? 1 : 0);
      assertThat(value.at("/summary/invalid_target_property").asInt())
          .isEqualTo(mode.equals("test") ? 0 : 1);
      assertThat(value.at("/summary/unpriced_usage").asInt())
          .isEqualTo(mode.equals("test") ? 0 : 1);
      assertThat(value.at("/warnings/negative_event_metric_count").asInt())
          .isEqualTo(mode.equals("test") ? 1 : 0);
      var warnings = get("/v1/unbilled-events?month=" + month(), mode);
      assertThat(warnings.statusCode()).as(warnings.body()).isEqualTo(200);
      assertThat(json(warnings).at("/unmatched_type/event_count").asInt())
          .isEqualTo(mode.equals("test") ? 1 : 0);
      assertThat(json(warnings).at("/negative_quantity/event_count").asInt())
          .isEqualTo(mode.equals("test") ? 1 : 0);
      assertThat(json(warnings).at("/non_numeric_value/event_count").asInt())
          .isEqualTo(mode.equals("test") ? 0 : 1);
    }
    assertThat(get("/v1/event-issues/search?customer_id=" + live, "test").statusCode())
        .isEqualTo(404);
  }

  @Test
  void 반대_스키마의_활성기간으로_현재_미터가_활성화되지_않는다() {
    UUID customer = UUID.randomUUID();
    for (String mode : List.of("test", "live")) {
      createCustomer(mode, customer, "period-original");
      assertThat(createMetric(mode, "period", "sum").statusCode()).isEqualTo(201);
      assertThat(
              request(
                      "POST",
                      "/v1/events",
                      session,
                      mode,
                      eventJson("period", customer, "{\"quantity\":10}"))
                  .statusCode())
          .isEqualTo(200);
    }
    assertThat(request("DELETE", "/v1/billable-metrics/period", session, "live", null).statusCode())
        .isEqualTo(204);
    transactions.write(
        scope("test"),
        () -> {
          jdbc.update(
              "UPDATE billable_metric SET active_from=? WHERE organization_id=? AND code='period'",
              month().plusMonths(1).toString(),
              organizationId);
          return null;
        });
    assertThat(
            json(get("/v1/unbilled-events?month=" + month(), "test"))
                .at("/unmatched_type/event_count")
                .asInt())
        .isEqualTo(1);
    assertThat(
            json(get("/v1/unbilled-events?month=" + month(), "live"))
                .at("/unmatched_type/event_count")
                .asInt())
        .isZero();
    assertThat(
            json(get("/v1/event-issues/search?month=" + month(), "test"))
                .at("/summary/unknown_event_type")
                .asInt())
        .isEqualTo(1);
    assertThat(
            json(get("/v1/event-issues/search?month=" + month(), "live"))
                .at("/summary/unknown_event_type")
                .asInt())
        .isZero();
  }

  @Test
  void 실제_재시도후보_SQL은_간격과_상한과_카드갱신과_조직상태를_보존한다() {
    OffsetDateTime now = OffsetDateTime.now();
    UUID due = null;
    UUID renewed = null;
    for (String kind : List.of("due", "recent", "exhausted", "expired", "renewed")) {
      UUID customer = UUID.randomUUID();
      UUID invoice = preparePayable("test", customer, false);
      OffsetDateTime requested = kind.equals("recent") ? now.minusHours(1) : now.minusDays(2);
      String code =
          kind.equals("expired") || kind.equals("renewed") ? "INVALID_CARD_EXPIRATION" : "HTTP_500";
      UUID attempt = seedAttempt(customer, invoice, "failed", requested, code);
      if (kind.equals("due")) due = attempt;
      if (kind.equals("renewed")) renewed = attempt;
      if (kind.equals("exhausted")) {
        seedAttempt(customer, invoice, "failed", requested.plusMinutes(1), code);
        seedAttempt(customer, invoice, "failed", requested.plusMinutes(2), code);
      }
      ownerJdbc()
          .update(
              "UPDATE test.billing_key SET authenticated_at=? WHERE organization_id=? AND customer_id=?",
              kind.equals("renewed") ? now : requested.minusDays(1),
              organizationId,
              customer);
    }
    var candidates =
        transactions.read(
            scope("test"),
            () ->
                attemptRepository
                    .findRetryCandidates(
                        now.minusDays(1),
                        3,
                        java.util.Set.of("INVALID_CARD_EXPIRATION"),
                        "ALREADY_REMOVED_BILLING_KEY",
                        "NOT_FOUND_PAYMENT",
                        now.minusDays(7))
                    .stream()
                    .filter(c -> c.getOrganizationId().equals(organizationId))
                    .map(com.meterengine.payment.repository.PaymentRetryCandidateProjection::getId)
                    .toList());
    assertThat(candidates).containsExactlyInAnyOrder(due, renewed);
    ownerJdbc()
        .update("UPDATE shared.organization SET status='suspended' WHERE id=?", organizationId);
    var inactive =
        transactions.read(
            scope("test"),
            () ->
                attemptRepository
                    .findRetryCandidates(
                        now.minusDays(1),
                        3,
                        java.util.Set.of("INVALID_CARD_EXPIRATION"),
                        "ALREADY_REMOVED_BILLING_KEY",
                        "NOT_FOUND_PAYMENT",
                        now.minusDays(7))
                    .stream()
                    .filter(c -> c.getOrganizationId().equals(organizationId))
                    .toList());
    assertThat(inactive).isEmpty();
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM shared.organization WHERE id=?", String.class, organizationId))
        .isEqualTo("suspended");
  }

  @Test
  void 자격교체전_등록토큰은_원래키를_발급과_새승인에도_고정한다() {
    UUID customer = UUID.randomUUID();
    UUID invoice = preparePayable("test", customer, false);
    var registration =
        DatabaseScopes.call(scope("test"), () -> registrations.create(organizationId, customer));
    saveCredential("test", "new");
    var reloaded =
        DatabaseScopes.call(scope("test"), () -> registrations.get(registration.token()));
    assertThat(reloaded.clientKey()).isEqualTo("test_ck_fixture");
    when(toss.issueBillingKey(
            org.mockito.ArgumentMatchers.eq("test_sk_fixture"),
            anyString(),
            org.mockito.ArgumentMatchers.eq(customer.toString())))
        .thenReturn(
            new TossPaymentsResult.Success<>(
                new com.meterengine.payment.client.TossPaymentsBilling(
                    "registered-original-key",
                    OffsetDateTime.now(),
                    new com.meterengine.payment.client.TossPaymentsBilling.Card(
                        "61", "4330****1234"))));
    when(toss.deleteBillingKey(anyString(), anyString()))
        .thenReturn(new TossPaymentsResult.Success<>(null));
    assertThat(registerCard(registration.token(), customer).mode()).isEqualTo("test");
    stubPinnedApproval("test_sk_fixture", "registered-original-key");
    var approved = charge("test", invoice);
    assertThat(approved.statusCode()).as(approved.body()).isEqualTo(201);
    verify(toss)
        .approveBilling(
            org.mockito.ArgumentMatchers.eq("test_sk_fixture"),
            org.mockito.ArgumentMatchers.eq("registered-original-key"),
            any(),
            org.mockito.ArgumentMatchers.eq(json(approved).get("id").asString()),
            isNull());
    verify(toss).deleteBillingKey("test_sk_fixture", "test-billing");
    var pinned =
        ownerJdbc()
            .queryForObject(
                "SELECT credential_updated_at FROM test.payment_attempt WHERE invoice_id=?",
                OffsetDateTime.class,
                invoice);
    var original =
        ownerJdbc()
            .queryForObject(
                "SELECT updated_at FROM test.toss_payments_credential_history WHERE organization_id=? ORDER BY retired_at LIMIT 1",
                OffsetDateTime.class,
                organizationId);
    assertThat(pinned.toInstant()).isEqualTo(original.toInstant());
  }

  @Test
  void 현재_PG키를_보관하면_기존_등록토큰도_조회와_발급을_거절한다() {
    UUID customer = UUID.randomUUID();
    saveCredential("test", "fixture");
    createCustomer("test", customer, "registration-only");
    String token =
        DatabaseScopes.call(
            scope("test"), () -> registrations.create(organizationId, customer).token());
    assertThat(
            request("DELETE", "/v1/toss-payments-credentials/test", session, "test", null)
                .statusCode())
        .isEqualTo(204);
    clearInvocations(toss);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> DatabaseScopes.call(scope("test"), () -> registrations.get(token)))
        .isInstanceOf(com.meterengine.global.error.ConflictException.class);
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> registerCard(token, customer))
        .isInstanceOf(com.meterengine.global.error.ConflictException.class);
    verify(toss, never()).issueBillingKey(anyString(), anyString(), anyString());
    verify(toss, never()).deleteBillingKey(anyString(), anyString());
    assertThat(
            ownerJdbc()
                .queryForObject(
                    "SELECT count(*) FROM test.billing_key WHERE organization_id=?",
                    Long.class,
                    organizationId))
        .isZero();
    assertThat(
            ownerJdbc()
                .queryForObject(
                    "SELECT used_at FROM test.payment_method_registration WHERE customer_id=?",
                    OffsetDateTime.class,
                    customer))
        .isNull();
  }

  @Test
  void 새키로_카드를_교체해도_이전카드는_원래키로_삭제하고_새결제는_새키를_쓴다() {
    UUID customer = UUID.randomUUID();
    UUID invoice = preparePayable("test", customer, false);
    saveCredential("test", "new");
    String token =
        DatabaseScopes.call(
            scope("test"), () -> registrations.create(organizationId, customer).token());
    when(toss.issueBillingKey(
            org.mockito.ArgumentMatchers.eq("test_sk_new"), anyString(), anyString()))
        .thenReturn(
            new TossPaymentsResult.Success<>(
                new com.meterengine.payment.client.TossPaymentsBilling(
                    "replacement-card",
                    OffsetDateTime.now(),
                    new com.meterengine.payment.client.TossPaymentsBilling.Card(
                        "61", "4330****4567"))));
    when(toss.deleteBillingKey(anyString(), anyString()))
        .thenReturn(new TossPaymentsResult.Success<>(null));
    registerCard(token, customer);
    verify(toss).deleteBillingKey("test_sk_fixture", "test-billing");
    stubPinnedApproval("test_sk_new", "replacement-card");
    var response = charge("test", invoice);
    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
    verify(toss)
        .approveBilling(
            org.mockito.ArgumentMatchers.eq("test_sk_new"),
            org.mockito.ArgumentMatchers.eq("replacement-card"),
            any(),
            anyString(),
            isNull());
  }

  @Test
  void 카드보관은_커밋뒤_원래키로_폐기하고_원래이력이_없으면_다른키로_폐기하지_않는다() {
    for (boolean missing : List.of(false, true)) {
      UUID customer = UUID.randomUUID();
      preparePayable("test", customer, false);
      saveCredential("test", "new");
      if (missing)
        ownerJdbc()
            .update(
                "DELETE FROM test.toss_payments_credential_history WHERE organization_id=?",
                organizationId);
      clearInvocations(toss);
      doAnswer(
              invocation -> {
                assertThat(invocation.<String>getArgument(0)).isEqualTo("test_sk_fixture");
                assertThat(
                        ownerJdbc()
                            .queryForObject(
                                "SELECT archived_at IS NOT NULL FROM test.billing_key WHERE organization_id=? AND customer_id=?",
                                Boolean.class,
                                organizationId,
                                customer))
                    .isTrue();
                return new TossPaymentsResult.Success<Void>(null);
              })
          .when(toss)
          .deleteBillingKey(anyString(), anyString());
      DatabaseScopes.call(
          scope("test"),
          () -> {
            paymentMethods.delete(organizationId, customer);
            return null;
          });
      assertThat(
              ownerJdbc()
                  .queryForObject(
                      "SELECT archived_at IS NOT NULL FROM test.billing_key WHERE organization_id=? AND customer_id=?",
                      Boolean.class,
                      organizationId,
                      customer))
          .isTrue();
      if (missing) verify(toss, never()).deleteBillingKey(anyString(), anyString());
      else verify(toss).deleteBillingKey("test_sk_fixture", "test-billing");
    }
  }

  private void stubPinnedApproval(String secret, String card) {
    doAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              assertThat(invocation.<String>getArgument(0)).isEqualTo(secret);
              assertThat(invocation.<String>getArgument(1)).isEqualTo(card);
              TossPaymentsApproveBillingRequest a = invocation.getArgument(2);
              return new TossPaymentsResult.Success<>(
                  new TossPaymentsPayment(
                      "payment-" + a.orderId(),
                      a.orderId(),
                      "DONE",
                      a.amount(),
                      a.amount(),
                      List.of()));
            })
        .when(toss)
        .approveBilling(anyString(), anyString(), any(), anyString(), isNull());
  }

  @Test
  void 원래_환경별_수량과_기본료와_확정액과_대시보드_건수를_보존한다() {
    UUID customer = UUID.randomUUID();
    for (String mode : List.of("test", "live")) {
      int quantity = mode.equals("test") ? 20 : 30;
      createCustomer(mode, customer, "same-original");
      assertThat(createMetric(mode, "original", "sum").statusCode()).isEqualTo(201);
      assertThat(
              request(
                      "POST",
                      "/v1/billable-metrics/original/price-rates",
                      session,
                      mode,
                      "{\"dimension_values\":{},\"unit_price\":" + quantity + "}")
                  .statusCode())
          .isEqualTo(201);
      assertThat(
              request(
                      "POST",
                      "/v1/price-plans",
                      session,
                      mode,
                      "{\"base_fee\":" + (quantity * 10) + ",\"minimum_amount\":0}")
                  .statusCode())
          .isEqualTo(201);
      assertThat(
              request(
                      "POST",
                      "/v1/events",
                      session,
                      mode,
                      eventJson("same-original", customer, "{\"quantity\":" + quantity + "}"))
                  .statusCode())
          .isEqualTo(200);
      var usage = get("/v1/usage?month=" + month(), mode);
      assertThat(usage.statusCode()).as(usage.body()).isEqualTo(200);
      assertThat(json(usage).at("/billable_metric_usages/0/customers/0/quantity").decimalValue())
          .isEqualByComparingTo(Integer.toString(quantity));
      var draft = get("/v1/invoices/draft?month=" + month(), mode);
      assertThat(draft.statusCode()).as(draft.body()).isEqualTo(200);
      assertThat(json(draft).get("total_amount").asLong())
          .isEqualTo(mode.equals("test") ? 600 : 1200);
    }
    createCustomer("live", UUID.randomUUID(), "live-extra");
    assertThat(createMetric("live", "extra", "count").statusCode()).isEqualTo(201);
    issueKey("test", "read", true, null);
    transactions.write(
        scope("live"),
        () -> {
          jdbc.update(
              "INSERT INTO supplier(organization_id,business_registration_number,representative_name,business_address) VALUES (?,'1234567890','owner','address')",
              organizationId);
          return null;
        });
    var result = request("POST", "/v1/invoices?month=" + month(), session, "test", "{}");
    assertThat(result.statusCode()).as(result.body()).isEqualTo(200);
    var invoice = json(get("/v1/invoices", "test")).at("/invoices/0");
    assertThat(invoice.get("supply_amount").asLong()).isEqualTo(600);
    assertThat(invoice.get("tax_amount").asLong()).isEqualTo(60);
    assertThat(invoice.get("total_amount").asLong()).isEqualTo(660);
    assertThat(get("/v1/invoices/" + invoice.get("id").asString(), "live").statusCode())
        .isEqualTo(404);
    for (String mode : List.of("test", "live")) {
      var response = get("/v1/dashboard?month=" + month(), mode);
      assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
      var value = json(response);
      assertThat(value.get("customer_count").asInt()).isEqualTo(mode.equals("test") ? 1 : 2);
      assertThat(value.get("billable_metric_count").asInt()).isEqualTo(mode.equals("test") ? 1 : 2);
      assertThat(value.get("active_api_key_count").asInt()).isEqualTo(mode.equals("test") ? 1 : 0);
      assertThat(value.get("event_count").asInt()).isEqualTo(1);
      assertThat(value.get("supplier_configured").asBoolean()).isEqualTo(mode.equals("live"));
      assertThat(value.get("payment_mode").asString()).isEqualTo(mode);
      assertThat(value.get("finalized_total_amount").asLong())
          .isEqualTo(mode.equals("test") ? 660 : 0);
      assertThat(value.get("draft_supply_amount").asLong())
          .isEqualTo(mode.equals("test") ? 0 : 1200);
    }
    var liveFinal = request("POST", "/v1/invoices?month=" + month(), session, "live", "{}");
    assertThat(liveFinal.statusCode()).as(liveFinal.body()).isEqualTo(200);
    assertThat(
            json(get("/v1/invoices?customer_id=" + customer, "live"))
                .at("/invoices/0/total_amount")
                .asLong())
        .isEqualTo(1320);
  }

  @Test
  void 과거월_미수금과_다른환경의_카드와_청구서와_권한은_분리된다() {
    UUID testCustomer = UUID.randomUUID();
    UUID test = preparePayable("test", testCustomer, false);
    UUID liveCustomer = UUID.randomUUID();
    UUID live = preparePayable("live", liveCustomer, false);
    assertThat(get("/v1/invoices/" + live + "/payment-attempts", "test").statusCode())
        .isEqualTo(404);
    assertThat(charge("test", live).statusCode()).isEqualTo(404);
    assertThat(get("/v1/customers/" + liveCustomer + "/payment-method", "test").statusCode())
        .isEqualTo(404);
    assertThat(json(get("/v1/payment-methods", "test")).get("payment_methods").size()).isEqualTo(1);
    String testKey = issueKey("test", "admin", true, null);
    assertThat(request("GET", "/v1/invoices", testKey, "live", null).statusCode()).isEqualTo(403);
    assertThat(get("/v1/invoices/export?mode=live", "test").statusCode()).isEqualTo(403);
    assertThat(get("/v1/billable-metric-prices?customer_id=" + liveCustomer, "test").statusCode())
        .isEqualTo(404);
    assertThat(
            request(
                    "POST",
                    "/v1/price-plans",
                    session,
                    "test",
                    "{\"customer_id\":\""
                        + liveCustomer
                        + "\",\"base_fee\":100,\"minimum_amount\":0}")
                .statusCode())
        .isEqualTo(404);
    for (String mode : List.of("test", "live")) {
      UUID customer = mode.equals("test") ? testCustomer : liveCustomer;
      long supply = mode.equals("test") ? 2000 : 3000;
      transactions.write(
          scope(mode),
          () -> {
            invoices.saveAndFlush(
                new Invoice(
                    UUID.randomUUID(),
                    organizationId,
                    customer,
                    mode + "-past",
                    month().minusMonths(1).toString(),
                    supply,
                    supply / 10,
                    OffsetDateTime.now()));
            return null;
          });
      var response = get("/v1/dashboard?month=" + month().plusMonths(1), mode);
      assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
      var unresolved = json(response).get("unresolved_invoice_payments");
      assertThat(json(response).get("invoice_payments").size()).isZero();
      assertThat(unresolved.size()).isEqualTo(2);
      assertThat(unresolved.get(1).get("month").asString())
          .isEqualTo(month().minusMonths(1).toString());
      assertThat(unresolved.get(1).get("amount").asLong())
          .isEqualTo(mode.equals("test") ? 2200 : 3300);
      assertThat(unresolved.get(1).get("customer_id").asString()).isEqualTo(customer.toString());
    }
    assertThat(charge("test", test).statusCode()).isEqualTo(201);
    assertThat(charge("live", live).statusCode()).isEqualTo(201);
  }

  @Test
  void live_자격이_없으면_test_자격으로_대신하지_않고_선기록도_하지_않는다() {
    preparePayable("test", UUID.randomUUID(), false);
    UUID customer = UUID.randomUUID();
    createCustomer("live", customer, "missing-live");
    UUID invoice =
        transactions.write(
            scope("live"),
            () ->
                invoices
                    .saveAndFlush(
                        new Invoice(
                            UUID.randomUUID(),
                            organizationId,
                            customer,
                            "live",
                            month().toString(),
                            1000,
                            100,
                            OffsetDateTime.now()))
                    .getId());
    clearInvocations(toss);
    var response = charge("live", invoice);
    assertThat(response.statusCode()).as(response.body()).isEqualTo(409);
    assertThat(response.body()).contains("toss_payments_credential_missing");
    assertThat(
            ownerJdbc()
                .queryForObject(
                    "SELECT count(*) FROM live.payment_attempt WHERE organization_id=?",
                    Long.class,
                    organizationId))
        .isZero();
    assertThat(
            json(get("/v1/toss-payments-credentials", "live"))
                .get("toss_payments_credentials")
                .size())
        .isZero();
    verify(toss, never()).approveBilling(anyString(), anyString(), any(), anyString(), isNull());
  }

  @Test
  void 미청구를_확인한_실패의_동시_수동재시도는_새승인을_한번만_한다() throws Exception {
    UUID customer = UUID.randomUUID();
    UUID invoice = preparePayable("test", customer, false);
    OffsetDateTime version =
        transactions.read(
            scope("test"),
            () ->
                credentials
                    .findById(
                        new com.meterengine.payment.entity.TossPaymentsCredentialId(
                            organizationId, "test"))
                    .orElseThrow()
                    .getUpdatedAt());
    seedAttempt(customer, invoice, "failed", version.plusSeconds(1), "NOT_FOUND_PAYMENT");
    clearInvocations(toss);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var responses =
          executor.invokeAll(
              List.<java.util.concurrent.Callable<java.net.http.HttpResponse<String>>>of(
                  () -> charge("test", invoice), () -> charge("test", invoice)));
      var statuses = new java.util.ArrayList<Integer>();
      for (var response : responses) {
        var r = response.get();
        statuses.add(r.statusCode());
        assertThat(r.statusCode()).as(r.body()).isIn(201, 409);
      }
      assertThat(statuses).containsExactlyInAnyOrder(201, 409);
    }
    verify(toss, times(1)).approveBilling(anyString(), anyString(), any(), anyString(), isNull());
    assertThat(
            ownerJdbc()
                .queryForObject(
                    "SELECT count(*) FROM test.payment_attempt WHERE invoice_id=?",
                    Long.class,
                    invoice))
        .isEqualTo(2);
  }

  @Test
  void 미확인실패가_7일을_넘거나_자격이_바뀌면_재승인하지_않는다() {
    UUID customer = UUID.randomUUID();
    UUID invoice = preparePayable("test", customer, false);
    OffsetDateTime original = OffsetDateTime.now().minusDays(10).withNano(0);
    ownerJdbc()
        .update(
            "UPDATE test.toss_payments_credential SET updated_at=?,verified_at=? WHERE organization_id=?",
            original,
            original,
            organizationId);
    ownerJdbc()
        .update(
            "UPDATE test.billing_key SET credential_updated_at=? WHERE organization_id=?",
            original,
            organizationId);
    seedAttempt(customer, invoice, "failed", original.plusDays(2), "NOT_FOUND_PAYMENT");
    clearInvocations(toss);
    var response = charge("test", invoice);
    assertThat(response.statusCode()).as(response.body()).isEqualTo(409);
    assertThat(response.body()).contains("payment_result_unconfirmed");
    verify(toss, never()).findPaymentByOrderId(anyString(), anyString());
    verify(toss, never()).approveBilling(anyString(), anyString(), any(), anyString(), isNull());
  }

  @Test
  void 반대_주체모드와_비양수_청구액은_PG호출과_선기록_전에_거절된다() {
    UUID customer = UUID.randomUUID();
    UUID invoice = preparePayable("test", customer, false);
    clearInvocations(toss);
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () ->
                DatabaseScopes.call(
                    scope("test"), () -> attempts.create(organizationId, invoice, "live", null)))
        .isInstanceOf(com.meterengine.global.error.ConflictException.class);
    for (long amount : List.of(0L, -100L)) {
      UUID other = UUID.randomUUID();
      createCustomer("test", other, "nonpositive-" + other);
      UUID nonpositive =
          transactions.write(
              scope("test"),
              () ->
                  invoices
                      .saveAndFlush(
                          new Invoice(
                              UUID.randomUUID(),
                              organizationId,
                              other,
                              "nonpositive",
                              month().toString(),
                              amount,
                              amount / 10,
                              OffsetDateTime.now()))
                      .getId());
      var response = charge("test", nonpositive);
      assertThat(response.statusCode()).as(response.body()).isEqualTo(409);
      assertThat(response.body()).contains("invoice_amount_zero");
    }
    verify(toss, never()).approveBilling(anyString(), anyString(), any(), anyString(), isNull());
    assertThat(
            ownerJdbc()
                .queryForObject(
                    "SELECT count(*) FROM test.payment_attempt WHERE organization_id=?",
                    Long.class,
                    organizationId))
        .isZero();
  }

  @Test
  void 결제후_현재키를_교체하고_보관해도_취소는_결제당시_키를_사용한다() {
    UUID invoice = preparePayable("test", UUID.randomUUID(), false);
    var approved = charge("test", invoice);
    assertThat(approved.statusCode()).as(approved.body()).isEqualTo(201);
    UUID attempt = UUID.fromString(json(approved).get("id").asString());
    saveCredential("test", "new");
    assertThat(
            request("DELETE", "/v1/toss-payments-credentials/test", session, "test", null)
                .statusCode())
        .isEqualTo(204);
    clearInvocations(toss);
    var canceled =
        cancel("test", attempt, UUID.randomUUID(), "{\"amount\":100,\"reason\":\"old-key\"}");
    assertThat(canceled.statusCode()).as(canceled.body()).isEqualTo(201);
    assertThat(json(canceled).get("status").asString()).isEqualTo("done");
    verify(toss, times(1))
        .cancel(
            org.mockito.ArgumentMatchers.eq("test_sk_fixture"), anyString(), any(), anyString());
  }

  @Test
  void 미수금의_일곱상태와_최신시도와_완료취소합은_원래_금액을_유지한다() {
    List<String> states =
        List.of(
            "not_required",
            "unpaid",
            "pending",
            "paid",
            "partially_canceled",
            "canceled",
            "failed");
    OffsetDateTime first = OffsetDateTime.parse("2026-10-01T00:00:00Z");
    for (int index = 0; index < states.size(); index++) {
      String state = states.get(index);
      UUID customer = UUID.randomUUID();
      createCustomer("test", customer, "state-" + index);
      UUID invoice =
          transactions.write(
              scope("test"),
              () ->
                  invoices
                      .saveAndFlush(
                          new Invoice(
                              UUID.randomUUID(),
                              organizationId,
                              customer,
                              "customer-" + state,
                              month().toString(),
                              state.equals("not_required") ? 0 : 12000,
                              state.equals("not_required") ? 0 : 1200,
                              first))
                      .getId());
      if (state.equals("not_required") || state.equals("unpaid")) {
        var response = get("/v1/invoice-payments?customer_id=" + customer, "test");
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        var payment = json(response).at("/invoice_payments/0");
        assertThat(payment.get("status").asString()).isEqualTo(state);
        assertThat(payment.get("amount").asLong())
            .isEqualTo(state.equals("not_required") ? 0 : 13200);
        assertThat(payment.get("attempt_count").asInt()).isZero();
        assertThat(payment.get("canceled_amount").asLong()).isZero();
        assertThat(payment.get("next_retry_at").isNull()).isTrue();
        continue;
      }
      String status =
          state.equals("pending") ? "pending" : state.equals("failed") ? "failed" : "done";
      if (state.equals("paid"))
        seedAttempt(customer, invoice, "failed", first, "REJECT_CARD_PAYMENT");
      UUID attempt =
          seedAttempt(
              customer,
              invoice,
              status,
              state.equals("paid") ? first.plusDays(1) : first,
              "REJECT_CARD_PAYMENT");
      if (state.equals("partially_canceled")) {
        seedCancellation(attempt, "done", 3000);
        seedCancellation(attempt, "done", 1000);
        seedCancellation(attempt, "failed", 5000);
        seedCancellation(attempt, "pending", 2000);
      }
      if (state.equals("canceled")) {
        seedCancellation(attempt, "done", 10000);
        seedCancellation(attempt, "done", 3200);
      }
      if (state.equals("failed"))
        transactions.write(
            scope("test"),
            () -> {
              keys.saveAndFlush(
                  new BillingKey(
                      organizationId,
                      customer,
                      "test",
                      "fixture",
                      "11",
                      "1234",
                      first,
                      "v1",
                      first));
              return null;
            });
      var response = get("/v1/invoice-payments?customer_id=" + customer, "test");
      assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
      var payment = json(response).at("/invoice_payments/0");
      assertThat(payment.get("status").asString()).isEqualTo(state);
      assertThat(payment.get("amount").asLong()).isEqualTo(13200);
      assertThat(payment.get("attempt_count").asInt()).isEqualTo(state.equals("paid") ? 2 : 1);
      assertThat(payment.get("canceled_amount").asLong())
          .isEqualTo(
              state.equals("partially_canceled") ? 4000 : state.equals("canceled") ? 13200 : 0);
      if (state.equals("failed")) {
        assertThat(payment.get("last_failure_code").asString()).isEqualTo("REJECT_CARD_PAYMENT");
        assertThat(payment.get("next_retry_at").asString()).isEqualTo("2026-10-02T09:00:05+09:00");
      } else assertThat(payment.get("next_retry_at").isNull()).isTrue();
    }
    var all = get("/v1/invoice-payments", "test");
    assertThat(all.statusCode()).as(all.body()).isEqualTo(200);
    assertThat(json(all).get("invoice_payments").size()).isEqualTo(7);
    var live = get("/v1/invoice-payments", "live");
    assertThat(live.statusCode()).as(live.body()).isEqualTo(200);
    assertThat(json(live).get("invoice_payments").size()).isZero();
  }

  @Test
  void 실패코드와_카드교체와_상한과_7일창은_재시도시각을_보존한다() {
    OffsetDateTime now = OffsetDateTime.now().minusDays(2);
    for (String code :
        List.of(
            "INVALID_CARD_EXPIRATION",
            "HTTP_500",
            "NOT_FOUND_PAYMENT",
            "ALREADY_REMOVED_BILLING_KEY")) {
      UUID customer = UUID.randomUUID();
      UUID invoice = preparePayable("test", customer, false);
      seedAttempt(customer, invoice, "failed", now, code);
      var response = get("/v1/invoice-payments?customer_id=" + customer, "test");
      assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
      assertThat(json(response).at("/invoice_payments/0/next_retry_at").isNull()).isFalse();
      ownerJdbc()
          .update(
              "UPDATE test.billing_key SET authenticated_at=? WHERE organization_id=? AND customer_id=?",
              now.minusDays(1),
              organizationId,
              customer);
      var old = get("/v1/invoice-payments?customer_id=" + customer, "test");
      assertThat(json(old).at("/invoice_payments/0/next_retry_at").isNull())
          .isEqualTo(code.equals("INVALID_CARD_EXPIRATION"));
      seedAttempt(customer, invoice, "failed", now.plusHours(1), code);
      seedAttempt(customer, invoice, "failed", now.plusHours(2), code);
      assertThat(
              json(get("/v1/invoice-payments?customer_id=" + customer, "test"))
                  .at("/invoice_payments/0/next_retry_at")
                  .isNull())
          .isTrue();
    }
  }

  private UUID seedAttempt(
      UUID customer, UUID invoice, String status, OffsetDateTime requested, String failure) {
    UUID id = UUID.randomUUID();
    transactions.write(
        scope("test"),
        () -> {
          jdbc.update(
              "INSERT INTO payment_attempt(id,organization_id,customer_id,invoice_id,mode,amount,order_name,status,requested_at,completed_at,payment_key,failure_code) VALUES (?,?,?,?,'test',13200,'fixture',?,?,?,?,?)",
              id,
              organizationId,
              customer,
              invoice,
              status,
              requested,
              status.equals("pending") ? null : requested.plusSeconds(5),
              status.equals("done") ? "payment-" + id : null,
              status.equals("failed") ? failure : null);
          return null;
        });
    return id;
  }

  private void seedCancellation(UUID attempt, String status, long amount) {
    UUID id = UUID.randomUUID();
    transactions.write(
        scope("test"),
        () -> {
          jdbc.update(
              "INSERT INTO payment_cancellation(id,organization_id,payment_attempt_id,mode,amount,reason,status,requested_at,completed_at,transaction_key,failure_code) VALUES (?,?,?,'test',?,'fixture',?,now(),?,?,?)",
              id,
              organizationId,
              attempt,
              amount,
              status,
              status.equals("pending") ? null : OffsetDateTime.now(),
              status.equals("done") ? "tx-" + id : null,
              status.equals("failed") ? "REJECTED" : null);
          return null;
        });
  }

  @Test
  void 요청메타데이터가_없어도_결제의_이벤트환경은_선택한_DB_scope와_같다() {
    UUID invoice = preparePayable("test", UUID.randomUUID(), false);
    var approved =
        DatabaseScopes.call(
            scope("test"), () -> attempts.create(organizationId, invoice, null, null));
    assertThat(approved.mode()).isEqualTo("test");
    assertThat(approved.status()).isEqualTo("done");
    assertThat(
            ownerJdbc()
                .queryForObject(
                    "SELECT payload->>'environment' FROM test.webhook_event WHERE organization_id=?",
                    String.class,
                    organizationId))
        .isEqualTo("test");
  }

  @Test
  void 대시보드의_건수와_공급자와_미수금은_선택한_스키마에_고정된다() {
    UUID customer = UUID.randomUUID();
    preparePayable("test", customer, false);
    createCustomer("live", customer, "live-dashboard");
    transactions.write(
        scope("live"),
        () -> {
          jdbc.update(
              "INSERT INTO supplier(organization_id,business_registration_number,representative_name,business_address) VALUES (?,'1234567890','live supplier','live address')",
              organizationId);
          return null;
        });
    var test = get("/v1/dashboard?month=" + month(), "test");
    var live = get("/v1/dashboard?month=" + month(), "live");
    assertThat(test.statusCode()).as(test.body()).isEqualTo(200);
    assertThat(live.statusCode()).as(live.body()).isEqualTo(200);
    assertThat(json(test).get("payment_mode").asString()).isEqualTo("test");
    assertThat(json(live).get("payment_mode").asString()).isEqualTo("live");
    assertThat(json(test).get("finalized_total_amount").asLong()).isEqualTo(1100);
    assertThat(json(live).get("finalized_total_amount").asLong()).isZero();
    assertThat(json(test).get("supplier_configured").asBoolean()).isFalse();
    assertThat(json(live).get("supplier_configured").asBoolean()).isTrue();
    assertThat(json(test).get("unresolved_invoice_payments").size()).isEqualTo(1);
    assertThat(json(live).get("unresolved_invoice_payments").size()).isZero();
    assertThat(json(test).get("customer_count").asInt()).isEqualTo(1);
    assertThat(json(live).get("customer_count").asInt()).isEqualTo(1);
  }

  @Test
  void Workspace_HTTP의_검색과_옵션과_월과_CSV는_실제_청구와_결제를_환경별로_조회한다() {
    UUID customer = UUID.randomUUID();
    for (String mode : List.of("test", "live")) {
      createCustomer(mode, customer, "same-workspace");
      transactions.write(
          scope(mode),
          () -> {
            jdbc.update("UPDATE customer SET name=? WHERE id=?", mode + "-customer", customer);
            return null;
          });
      assertThat(createMetric(mode, "workspace", "sum").statusCode()).isEqualTo(201);
      transactions.write(
          scope(mode),
          () -> {
            jdbc.update(
                "UPDATE billable_metric SET name=? WHERE organization_id=? AND code='workspace'",
                mode + "-meter",
                organizationId);
            return null;
          });
      assertThat(
              request(
                      "POST",
                      "/v1/billable-metrics/workspace/price-rates",
                      session,
                      mode,
                      "{\"dimension_values\":{},\"unit_price\":10}")
                  .statusCode())
          .isEqualTo(201);
      assertThat(
              request(
                      "POST",
                      "/v1/events",
                      session,
                      mode,
                      eventJson(
                          "same-workspace",
                          customer,
                          "{\"quantity\":" + (mode.equals("test") ? 10 : 20) + "}"))
                  .statusCode())
          .isEqualTo(200);
    }
    transactions.write(
        scope("live"),
        () -> {
          jdbc.update(
              "INSERT INTO price_rate(id,organization_id,billable_metric_code,dimension_values,unit_price,effective_from) VALUES (?,?,'workspace','{}',5,?)",
              UUID.randomUUID(),
              organizationId,
              month().minusMonths(1).toString());
          return null;
        });
    pastEvent(
        "live",
        "prior-workspace",
        customer,
        "tokens",
        "{\"quantity\":7}",
        month()
            .minusMonths(1)
            .atEndOfMonth()
            .atStartOfDay(com.meterengine.metric.service.BillableMetricUsageService.BILLING_ZONE)
            .toOffsetDateTime(),
        OffsetDateTime.now());
    var finalization = request("POST", "/v1/invoices?month=" + month(), session, "test", "{}");
    assertThat(finalization.statusCode()).as(finalization.body()).isEqualTo(200);
    for (String mode : List.of("test", "live")) {
      for (String resource :
          List.of(
              "customers",
              "billable-metrics",
              "billable-metric-prices",
              "usage",
              "invoices/draft",
              "invoices")) {
        String query =
            resource.equals("customers") || resource.equals("billable-metrics")
                ? ""
                : "?month=" + month();
        var found = get("/v1/" + resource + "/search" + query, mode);
        assertThat(found.statusCode()).as(resource + found.body()).isEqualTo(200);
        int expected =
            resource.equals("invoices/draft") && mode.equals("test")
                    || resource.equals("invoices") && mode.equals("live")
                ? 0
                : 1;
        assertThat(json(found).get("total_unfiltered").asInt())
            .as(resource + mode)
            .isEqualTo(expected);
        var csv = get("/v1/" + resource + "/export" + query, mode);
        assertThat(csv.statusCode()).as(resource + csv.body()).isEqualTo(200);
        assertThat(csv.body())
            .startsWith("\ufeff")
            .contains("\r\n")
            .doesNotContain(mode.equals("test") ? "live-customer" : "test-customer")
            .doesNotContain(mode.equals("test") ? "live-meter" : "test-meter");
      }
      for (String resource : List.of("customers", "billable-metrics")) {
        var options = get("/v1/" + resource + "/options", mode);
        assertThat(options.statusCode()).as(options.body()).isEqualTo(200);
        assertThat(json(options).at("/items/0/label").asString())
            .isEqualTo(mode + (resource.equals("customers") ? "-customer" : "-meter"));
      }
      for (String resource : List.of("usage", "billable-metric-prices")) {
        var months = get("/v1/" + resource + "/months", mode);
        assertThat(months.statusCode()).as(months.body()).isEqualTo(200);
        assertThat(json(months).get("months").size()).isEqualTo(mode.equals("test") ? 1 : 2);
        assertThat(json(months).at("/months/0").asString()).isEqualTo(month().toString());
      }
    }
    assertThat(json(get("/v1/invoices/months", "test")).get("months").size()).isEqualTo(1);
    assertThat(json(get("/v1/invoices/months", "live")).get("months").size()).isZero();
    assertThat(get("/v1/invoices/search?mode=live", "test").statusCode()).isEqualTo(403);
    var filtered =
        get(
            "/v1/invoices/search?month="
                + month()
                + "&customer_id="
                + customer
                + "&billable_metric_code=workspace&min_amount=110&max_amount=110&status=unpaid&sort=total_amount&order=desc&size=1&page=1",
            "test");
    assertThat(filtered.statusCode()).as(filtered.body()).isEqualTo(200);
    assertThat(json(filtered).get("total").asInt()).isEqualTo(1);
    var empty = get("/v1/invoices/search?month=" + month() + "&min_amount=111", "test");
    assertThat(empty.statusCode()).as(empty.body()).isEqualTo(200);
    assertThat(json(empty).get("total").asInt()).isZero();
    var second = get("/v1/invoices/search?month=" + month() + "&page=2&size=1", "test");
    assertThat(second.statusCode()).as(second.body()).isEqualTo(200);
    assertThat(json(second).get("items").size()).isZero();
    assertThat(json(second).get("total").asInt()).isEqualTo(1);
  }

  @Test
  void Workspace_HTTP의_복수행은_조직과_환경과_필터와_정렬과_페이지와_CSV에서_구분된다() {
    UUID currentOrganization = organizationId;
    String currentSession = session;
    UUID foreignOrganization = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO shared.organization(id,name) VALUES (?,'foreign workspace')",
        foreignOrganization);
    jdbc.update(
        "INSERT INTO shared.organization_member(organization_id,user_account_id,created_at,role,scopes) VALUES (?,?,now(),'owner',ARRAY['admin'])",
        foreignOrganization,
        userId);
    try {
      organizationId = foreignOrganization;
      session = issueSession(userId);
      for (String mode : List.of("test", "live")) prepareWorkspaceRows(mode, mode + "-foreign", 9);
    } finally {
      organizationId = currentOrganization;
      session = currentSession;
    }
    Map<String, List<UUID>> customers = new java.util.HashMap<>();
    for (String mode : List.of("test", "live")) {
      customers.put(
          mode, prepareWorkspaceRows(mode, mode + "-current", mode.equals("test") ? 1 : 2));
    }
    for (String mode : List.of("test", "live")) {
      String prefix = mode + "-current";
      int factor = mode.equals("test") ? 1 : 2;
      List<UUID> ids = customers.get(mode);
      List<String> customerOrder =
          List.of(
              ids.get(0).toString(),
              ids.get(1).toString(),
              ids.get(3).toString(),
              ids.get(2).toString());
      List<String> invoiceIds =
          transactions.read(
              scope(mode),
              () ->
                  jdbc.query(
                      "SELECT id FROM invoice WHERE organization_id=? ORDER BY supply_amount",
                      (rs, row) -> rs.getString(1),
                      organizationId));
      assertThat(invoiceIds).hasSize(2);
      for (String resource :
          List.of(
              "customers",
              "billable-metrics",
              "billable-metric-prices",
              "usage",
              "invoices/draft",
              "invoices")) {
        String base = "/v1/" + resource;
        String params = "?month=" + month();
        String identity;
        String csvIdentity;
        String sort;
        String match;
        List<String> expected;
        switch (resource) {
          case "customers" -> {
            params = "?status=all";
            identity = "/id";
            csvIdentity = "id";
            sort = "name";
            expected = customerOrder;
            match = prefix + "-alpha";
          }
          case "billable-metrics", "billable-metric-prices" -> {
            if (resource.equals("billable-metrics")) params = "?status=all";
            identity = resource.equals("billable-metrics") ? "/code" : "/billable_metric_code";
            csvIdentity = identity.substring(1);
            sort = "code";
            expected = List.of("wsa", "wsb");
            match = "wsa";
          }
          case "usage" -> {
            identity = "/customer_id";
            csvIdentity = "customer_id";
            sort = "customer_name";
            expected = customerOrder;
            match = prefix + "-alpha";
          }
          case "invoices/draft" -> {
            identity = "/customer_id";
            csvIdentity = "customer_id";
            sort = "customer_name";
            expected = List.of(ids.get(3).toString(), ids.get(2).toString());
            match = prefix + "-delta";
          }
          default -> {
            identity = "/invoice/id";
            csvIdentity = "invoice.id";
            sort = "total_amount";
            expected = invoiceIds;
            match = prefix + "-alpha";
          }
        }
        String ordered = params + "&sort=" + sort;
        var ascending = workspacePage(base + "/search" + ordered + "&order=asc", mode);
        assertThat(ascending.get("total").asInt()).as(resource).isEqualTo(expected.size());
        assertThat(workspaceIds(ascending, identity))
            .as(resource)
            .containsExactlyElementsOf(expected);
        var descending = workspacePage(base + "/search" + ordered + "&order=desc", mode);
        assertThat(workspaceIds(descending, identity))
            .as(resource)
            .containsExactlyElementsOf(expected.reversed());
        for (int page = 1; page <= expected.size(); page++) {
          var paged =
              workspacePage(base + "/search" + ordered + "&order=asc&size=1&page=" + page, mode);
          assertThat(paged.get("total").asInt()).isEqualTo(expected.size());
          assertThat(workspaceIds(paged, identity)).containsExactly(expected.get(page - 1));
        }
        var beyond =
            workspacePage(
                base + "/search" + ordered + "&size=1&page=" + (expected.size() + 1), mode);
        assertThat(workspaceIds(beyond, identity)).isEmpty();
        var filtered = workspacePage(base + "/search" + ordered + "&q=" + match, mode);
        assertThat(workspaceIds(filtered, identity)).containsExactly(expected.get(0));
        assertThat(filtered.get("total").asInt()).isEqualTo(1);
        var unmatched = workspacePage(base + "/search" + ordered + "&q=absent-workspace", mode);
        assertThat(workspaceIds(unmatched, identity)).isEmpty();
        assertThat(unmatched.get("total").asInt()).isZero();
        var csv = get(base + "/export" + ordered + "&order=asc", mode);
        assertThat(csv.statusCode()).as(csv.body()).isEqualTo(200);
        assertThat(csv.body())
            .startsWith("\ufeff")
            .contains("\r\n")
            .doesNotContain("foreign")
            .doesNotContain(mode.equals("test") ? "live-current" : "test-current");
        List<Map<String, String>> rows = workspaceCsvRows(csv.body());
        assertThat(rows.stream().map(row -> row.get(csvIdentity)).toList())
            .as(resource)
            .containsExactlyElementsOf(expected);
        switch (resource) {
          case "customers" ->
              assertThat(rows.getFirst())
                  .containsEntry("name", prefix + "-alpha")
                  .containsEntry("external_id", prefix + "-alpha");
          case "billable-metrics" ->
              assertThat(rows.getFirst())
                  .containsEntry("name", prefix + "-meter-a")
                  .containsEntry("event_type", "tokens-a");
          case "billable-metric-prices" -> {
            assertThat(rows.getFirst()).containsEntry("billable_metric_name", prefix + "-meter-a");
            assertThat(
                    mapper.readTree(rows.getFirst().get("price_rates")).at("/0/unit_price").asInt())
                .isEqualTo(10 * factor);
            assertThat(ascending.at("/items/1/price_rates/0/unit_price").asInt())
                .isEqualTo(20 * factor);
          }
          case "usage" -> {
            assertThat(rows.getFirst()).containsEntry("customer_name", prefix + "-alpha");
            assertThat(
                    mapper
                        .readTree(rows.getFirst().get("metrics"))
                        .at("/0/customers/0/quantity")
                        .asInt())
                .isEqualTo(10 * factor);
            assertThat(ascending.at("/items/0/metrics/0/name").asString())
                .isEqualTo(prefix + "-meter-a");
          }
          case "invoices/draft" -> {
            assertThat(rows.getFirst())
                .containsEntry("customer_name", prefix + "-delta")
                .containsEntry("amount", String.valueOf(800 * factor * factor));
            assertThat(ascending.at("/items/1/amount").asInt()).isEqualTo(300 * factor * factor);
          }
          default -> {
            assertThat(rows.getFirst())
                .containsEntry("invoice.customer_id", ids.getFirst().toString())
                .containsEntry("invoice.customer_name", prefix + "-alpha")
                .containsEntry("invoice.supply_amount", String.valueOf(100 * factor * factor))
                .containsEntry("invoice.tax_amount", String.valueOf(10 * factor * factor))
                .containsEntry("invoice.total_amount", String.valueOf(110 * factor * factor))
                .containsEntry("payment.status", "unpaid");
            assertThat(ascending.at("/items/1/invoice/total_amount").asInt())
                .isEqualTo(440 * factor * factor);
          }
        }
        List<String> suffixes =
            switch (resource) {
              case "billable-metrics", "billable-metric-prices" -> List.of("meter-a", "meter-b");
              case "invoices/draft" -> List.of("delta", "gamma");
              case "invoices" -> List.of("alpha", "beta");
              default -> List.of("alpha", "beta", "delta", "gamma");
            };
        String nameColumn =
            switch (resource) {
              case "customers", "billable-metrics" -> "name";
              case "billable-metric-prices" -> "billable_metric_name";
              case "invoices" -> "invoice.customer_name";
              default -> "customer_name";
            };
        List<String> expectedNames =
            suffixes.stream().map(suffix -> prefix + "-" + suffix).toList();
        assertThat(rows.stream().map(row -> row.get(nameColumn)).toList())
            .containsExactlyElementsOf(expectedNames);
        List<String> actualNames = new java.util.ArrayList<>();
        ascending
            .get("items")
            .forEach(
                item -> actualNames.add(item.at("/" + nameColumn.replace('.', '/')).asString()));
        assertThat(actualNames).containsExactlyElementsOf(expectedNames);
        for (int row = 0; row < rows.size(); row++) {
          Map<String, String> value = rows.get(row);
          switch (resource) {
            case "billable-metric-prices" ->
                assertThat(mapper.readTree(value.get("price_rates")).at("/0/unit_price").asInt())
                    .isEqualTo((row + 1) * 10 * factor);
            case "usage" -> {
              long quantity = 0;
              for (var metricValue : mapper.readTree(value.get("metrics")))
                quantity += metricValue.at("/customers/0/quantity").asLong();
              assertThat(quantity).isEqualTo(List.of(10, 20, 40, 30).get(row) * factor);
            }
            case "invoices/draft" ->
                assertThat(value)
                    .containsEntry(
                        "amount", String.valueOf(List.of(800, 300).get(row) * factor * factor));
            case "invoices" -> {
              int supply = List.of(100, 400).get(row) * factor * factor;
              assertThat(value)
                  .containsEntry("invoice.supply_amount", String.valueOf(supply))
                  .containsEntry("invoice.tax_amount", String.valueOf(supply / 10))
                  .containsEntry("invoice.total_amount", String.valueOf(supply + supply / 10));
            }
            default -> {}
          }
        }
        var filteredCsv = get(base + "/export" + ordered + "&q=" + match, mode);
        assertThat(filteredCsv.statusCode()).as(filteredCsv.body()).isEqualTo(200);
        assertThat(workspaceCsvRows(filteredCsv.body())).hasSize(1);
        assertThat(workspaceCsvRows(filteredCsv.body()).getFirst().get(csvIdentity))
            .isEqualTo(expected.getFirst());
      }
      String invoiceBase = "/v1/invoices/search?month=" + month();
      String alpha = "&customer_id=" + ids.getFirst();
      String exact =
          "&min_amount=" + 110 * factor * factor + "&max_amount=" + 110 * factor * factor;
      for (String condition :
          List.of(alpha, "&billable_metric_code=wsa", "&status=unpaid", exact)) {
        var selected = workspacePage(invoiceBase + alpha + condition, mode);
        assertThat(workspaceIds(selected, "/invoice/id")).containsExactly(invoiceIds.getFirst());
      }
      for (String condition :
          List.of(
              "&billable_metric_code=wsb",
              "&status=paid",
              "&min_amount=" + (110 * factor * factor + 1),
              "&max_amount=" + (110 * factor * factor - 1))) {
        var excluded = workspacePage(invoiceBase + alpha + condition, mode);
        assertThat(workspaceIds(excluded, "/invoice/id")).isEmpty();
        assertThat(excluded.get("total").asInt()).isZero();
      }
      var beta = workspacePage(invoiceBase + "&customer_id=" + ids.get(1), mode);
      assertThat(workspaceIds(beta, "/invoice/id")).containsExactly(invoiceIds.get(1));
      var metric = workspacePage(invoiceBase + "&billable_metric_code=wsb", mode);
      assertThat(workspaceIds(metric, "/invoice/id")).containsExactly(invoiceIds.get(1));
    }
  }

  private List<UUID> prepareWorkspaceRows(String mode, String prefix, int factor) {
    List<UUID> ids =
        List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    for (String code : List.of("wsa", "wsb")) {
      assertThat(createMetric(mode, code, "sum").statusCode()).isEqualTo(201);
      transactions.write(
          scope(mode),
          () -> {
            jdbc.update(
                "UPDATE billable_metric SET name=?,event_type=? WHERE organization_id=? AND code=?",
                prefix + (code.equals("wsa") ? "-meter-a" : "-meter-b"),
                code.equals("wsa") ? "tokens-a" : "tokens-b",
                organizationId,
                code);
            return null;
          });
      var price =
          request(
              "POST",
              "/v1/billable-metrics/" + code + "/price-rates",
              session,
              mode,
              "{\"dimension_values\":{},\"unit_price\":"
                  + (code.equals("wsa") ? 10 : 20) * factor
                  + "}");
      assertThat(price.statusCode()).as(price.body()).isEqualTo(201);
    }
    for (int index = 0; index < 4; index++) {
      String name = prefix + "-" + List.of("alpha", "beta", "gamma", "delta").get(index);
      UUID id = ids.get(index);
      createCustomer(mode, id, name);
      transactions.write(
          scope(mode),
          () -> {
            jdbc.update(
                "UPDATE customer SET name=? WHERE organization_id=? AND id=?",
                name,
                organizationId,
                id);
            return null;
          });
      String type = index % 2 == 0 ? "tokens-a" : "tokens-b";
      var event =
          request(
              "POST",
              "/v1/events",
              session,
              mode,
              "{\"transaction_id\":\""
                  + name
                  + "\",\"customer_id\":\""
                  + id
                  + "\",\"type\":\""
                  + type
                  + "\",\"properties\":{\"quantity\":"
                  + 10 * (index + 1) * factor
                  + "},\"timestamp\":\""
                  + OffsetDateTime.now().minusSeconds(10)
                  + "\"}");
      assertThat(event.statusCode()).as(event.body()).isEqualTo(200);
      if (index == 1) {
        var finalized = request("POST", "/v1/invoices?month=" + month(), session, mode, "{}");
        assertThat(finalized.statusCode()).as(finalized.body()).isEqualTo(200);
      }
    }
    return ids;
  }

  private tools.jackson.databind.JsonNode workspacePage(String path, String mode) {
    var response = get(path, mode);
    assertThat(response.statusCode()).as(path + response.body()).isEqualTo(200);
    return json(response);
  }

  private static List<String> workspaceIds(tools.jackson.databind.JsonNode page, String pointer) {
    List<String> ids = new java.util.ArrayList<>();
    page.get("items").forEach(item -> ids.add(item.at(pointer).asString()));
    return ids;
  }

  private static List<Map<String, String>> workspaceCsvRows(String csv) {
    List<List<String>> parsed = new java.util.ArrayList<>();
    for (String line : csv.substring(1).split("\r\n")) {
      List<String> cells = new java.util.ArrayList<>();
      StringBuilder cell = new StringBuilder();
      boolean quoted = false;
      for (int index = 0; index < line.length(); index++) {
        char value = line.charAt(index);
        if (value == '"') {
          if (quoted && index + 1 < line.length() && line.charAt(index + 1) == '"') {
            cell.append('"');
            index++;
          } else quoted = !quoted;
        } else if (value == ',' && !quoted) {
          cells.add(cell.toString());
          cell.setLength(0);
        } else cell.append(value);
      }
      assertThat(quoted).isFalse();
      cells.add(cell.toString());
      parsed.add(cells);
    }
    List<String> header = parsed.removeFirst();
    List<Map<String, String>> rows = new java.util.ArrayList<>();
    for (List<String> cells : parsed) {
      assertThat(cells).hasSize(header.size());
      Map<String, String> row = new java.util.LinkedHashMap<>();
      for (int index = 0; index < header.size(); index++)
        row.put(header.get(index), cells.get(index));
      rows.add(row);
    }
    return rows;
  }

  @Test
  void 동시_자격교체와_카드교체는_중간버전을_이력에_보존한다() throws Exception {
    UUID customer = UUID.randomUUID();
    preparePayable("test", customer, false);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var results =
          executor.invokeAll(
              List.<java.util.concurrent.Callable<java.net.http.HttpResponse<String>>>of(
                  () -> saveCredentialResponse("test", "first"),
                  () -> saveCredentialResponse("test", "second")));
      for (var result : results) {
        var r = result.get();
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
      }
    }
    assertThat(
            ownerJdbc()
                .queryForObject(
                    "SELECT count(*) FROM test.toss_payments_credential_history WHERE organization_id=?",
                    Long.class,
                    organizationId))
        .isEqualTo(2);
    saveCredential("test", "fixture");
    String one =
        DatabaseScopes.call(
            scope("test"), () -> registrations.create(organizationId, customer).token());
    String two =
        DatabaseScopes.call(
            scope("test"), () -> registrations.create(organizationId, customer).token());
    var counter = new java.util.concurrent.atomic.AtomicInteger();
    when(toss.issueBillingKey(anyString(), anyString(), anyString()))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              assertThat(invocation.<String>getArgument(0)).isEqualTo("test_sk_fixture");
              return new TossPaymentsResult.Success<>(
                  new com.meterengine.payment.client.TossPaymentsBilling(
                      "card-" + counter.incrementAndGet(),
                      OffsetDateTime.now(),
                      new com.meterengine.payment.client.TossPaymentsBilling.Card(
                          "11", "1234****")));
            });
    when(toss.deleteBillingKey(anyString(), anyString()))
        .thenReturn(new TossPaymentsResult.Success<>(null));
    try (var executor = Executors.newFixedThreadPool(2)) {
      var results =
          executor.invokeAll(
              List
                  .<java.util.concurrent.Callable<
                          com.meterengine.payment.dto.PaymentMethodResponse>>
                      of(() -> registerCard(one, customer), () -> registerCard(two, customer)));
      for (var result : results) assertThat(result.get().mode()).isEqualTo("test");
    }
    assertThat(
            ownerJdbc()
                .queryForObject(
                    "SELECT count(*) FROM test.billing_key_history WHERE organization_id=? AND customer_id=?",
                    Long.class,
                    organizationId,
                    customer))
        .isEqualTo(2);
    var versions =
        ownerJdbc()
            .queryForList(
                "SELECT billing_key FROM test.billing_key_history WHERE organization_id=? AND customer_id=?",
                String.class,
                organizationId,
                customer);
    assertThat(versions).hasSize(2).allMatch(v -> v.startsWith("v1:"));
    verify(toss, times(2)).issueBillingKey(anyString(), anyString(), anyString());
    verify(toss, times(2)).deleteBillingKey(anyString(), anyString());
  }

  @Test
  void 카드교체의_감사실패는_현재카드와_이력과_토큰사용을_함께_롤백한다() {
    UUID customer = UUID.randomUUID();
    preparePayable("test", customer, false);
    String token =
        DatabaseScopes.call(
            scope("test"), () -> registrations.create(organizationId, customer).token());
    when(toss.issueBillingKey(anyString(), anyString(), anyString()))
        .thenReturn(
            new TossPaymentsResult.Success<>(
                new com.meterengine.payment.client.TossPaymentsBilling(
                    "replacement",
                    OffsetDateTime.now(),
                    new com.meterengine.payment.client.TossPaymentsBilling.Card("11", "changed"))));
    String before =
        ownerJdbc()
            .queryForObject(
                "SELECT billing_key FROM test.billing_key WHERE organization_id=? AND customer_id=?",
                String.class,
                organizationId,
                customer);
    rejectAudit(
        "payment_method_created",
        () ->
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> registerCard(token, customer))
                .isInstanceOf(org.springframework.dao.DataAccessException.class));
    assertThat(
            ownerJdbc()
                .queryForObject(
                    "SELECT billing_key FROM test.billing_key WHERE organization_id=? AND customer_id=?",
                    String.class,
                    organizationId,
                    customer))
        .isEqualTo(before);
    assertThat(
            ownerJdbc()
                .queryForObject(
                    "SELECT count(*) FROM test.billing_key_history WHERE organization_id=?",
                    Long.class,
                    organizationId))
        .isZero();
    assertThat(
            ownerJdbc()
                .queryForObject(
                    "SELECT count(*) FROM test.payment_method_registration WHERE organization_id=? AND used_at IS NOT NULL",
                    Long.class,
                    organizationId))
        .isZero();
    verify(toss, never()).deleteBillingKey(anyString(), anyString());
  }

  @Test
  void 자격이력의_NO_KEY_UPDATE_잠금은_같은_조직의_이벤트수집을_막지_않는다() throws Exception {
    UUID customer = UUID.randomUUID();
    createCustomer("test", customer, "lock-customer");
    var locked = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    try (var executor = Executors.newSingleThreadExecutor()) {
      var lock =
          executor.submit(
              () ->
                  transactions.write(
                      scope("test"),
                      () -> {
                        archives.lock(organizationId);
                        locked.countDown();
                        try {
                          assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
                        } catch (InterruptedException e) {
                          throw new IllegalStateException(e);
                        }
                        return null;
                      }));
      assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
      try {
        long started = System.nanoTime();
        var event =
            request(
                "POST", "/v1/events", session, "test", eventJson("nonblocking", customer, "{}"));
        assertThat(event.statusCode()).as(event.body()).isEqualTo(200);
        assertThat(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).isLessThan(2000);
      } finally {
        release.countDown();
      }
      lock.get(5, TimeUnit.SECONDS);
    }
  }

  private com.meterengine.payment.dto.PaymentMethodResponse registerCard(
      String token, UUID customer) {
    return DatabaseScopes.call(
        scope("test"),
        () ->
            billingKeys.create(
                token,
                new com.meterengine.payment.dto.CreateBillingKeyRequest(
                    "fixture-auth", customer.toString(), "v1")));
  }

  @Test
  void 결제_선기록의_감사실패는_PG호출전에_시도와_이벤트를_롤백한다() {
    UUID invoice = preparePayable("test", UUID.randomUUID(), false);
    clearInvocations(toss);
    rejectAudit(
        "payment_attempt_created",
        () -> assertThat(charge("test", invoice).statusCode()).isEqualTo(500));
    assertThat(
            ownerJdbc()
                .queryForObject(
                    "SELECT count(*) FROM test.payment_attempt WHERE organization_id=?",
                    Long.class,
                    organizationId))
        .isZero();
    verify(toss, never()).approveBilling(anyString(), anyString(), any(), anyString(), isNull());
    assertThat(
            ownerJdbc()
                .queryForObject(
                    "SELECT count(*) FROM test.webhook_event WHERE organization_id=?",
                    Long.class,
                    organizationId))
        .isZero();
  }

  @Test
  void 최종_이벤트기록의_실패는_결제완료와_전달큐를_함께_롤백한다() {
    UUID invoice = preparePayable("test", UUID.randomUUID(), false);
    ownerJdbc()
        .execute(
            "CREATE FUNCTION test.s5_fail_event() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 's5-event-failure'; END $$");
    ownerJdbc()
        .execute(
            "CREATE TRIGGER s5_fail_event BEFORE INSERT ON test.webhook_event FOR EACH ROW EXECUTE FUNCTION test.s5_fail_event()");
    try {
      assertThat(charge("test", invoice).statusCode()).isEqualTo(500);
      assertThat(
              ownerJdbc()
                  .queryForObject(
                      "SELECT status FROM test.payment_attempt WHERE invoice_id=?",
                      String.class,
                      invoice))
          .isEqualTo("pending");
      assertThat(
              ownerJdbc()
                  .queryForObject(
                      "SELECT count(*) FROM test.webhook_event WHERE organization_id=?",
                      Long.class,
                      organizationId))
          .isZero();
      assertThat(
              ownerJdbc()
                  .queryForObject(
                      "SELECT count(*) FROM test.webhook_delivery WHERE organization_id=?",
                      Long.class,
                      organizationId))
          .isZero();
      assertThat(charge("test", invoice).statusCode()).isEqualTo(409);
      verify(toss, times(1)).approveBilling(anyString(), anyString(), any(), anyString(), isNull());
    } finally {
      ownerJdbc().execute("DROP TRIGGER s5_fail_event ON test.webhook_event");
      ownerJdbc().execute("DROP FUNCTION test.s5_fail_event()");
    }
  }

  @Test
  void 동시_결제와_취소는_진행중_한건만_남기고_PG를_중복호출하지_않는다() throws Exception {
    UUID invoice = preparePayable("test", UUID.randomUUID(), false);
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              entered.countDown();
              assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
              TossPaymentsApproveBillingRequest a = invocation.getArgument(2);
              return new TossPaymentsResult.Success<>(
                  new TossPaymentsPayment(
                      "payment-" + a.orderId(),
                      a.orderId(),
                      "DONE",
                      a.amount(),
                      a.amount(),
                      List.of()));
            })
        .when(toss)
        .approveBilling(anyString(), anyString(), any(), anyString(), isNull());
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first = executor.submit(() -> charge("test", invoice));
      assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
      try {
        assertThat(charge("test", invoice).statusCode()).isEqualTo(409);
      } finally {
        release.countDown();
      }
      var attempt = first.get(5, TimeUnit.SECONDS);
      assertThat(attempt.statusCode()).as(attempt.body()).isEqualTo(201);
      UUID attemptId = UUID.fromString(json(attempt).get("id").asString());
      var cancelEntered = new CountDownLatch(1);
      var cancelRelease = new CountDownLatch(1);
      doAnswer(
              invocation -> {
                assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
                cancelEntered.countDown();
                assertThat(cancelRelease.await(5, TimeUnit.SECONDS)).isTrue();
                return new TossPaymentsResult.Unknown<>("fixture-pending");
              })
          .when(toss)
          .cancel(anyString(), anyString(), any(), anyString());
      UUID key = UUID.randomUUID();
      String body = "{\"amount\":100,\"reason\":\"concurrent\"}";
      var pending = executor.submit(() -> cancel("test", attemptId, key, body));
      assertThat(cancelEntered.await(5, TimeUnit.SECONDS)).isTrue();
      try {
        var replay = cancel("test", attemptId, key, body);
        assertThat(replay.statusCode()).as(replay.body()).isEqualTo(201);
        assertThat(json(replay).get("status").asString()).isEqualTo("pending");
        assertThat(cancel("test", attemptId, UUID.randomUUID(), body).statusCode()).isEqualTo(409);
        try (var connection = ownerJdbc().getDataSource().getConnection();
            var stmt = connection.createStatement()) {
          connection.setAutoCommit(false);
          stmt.execute("SET LOCAL lock_timeout='500ms'");
          stmt.executeQuery(
              "SELECT id FROM test.payment_attempt WHERE id='" + attemptId + "' FOR UPDATE");
          connection.rollback();
        }
      } finally {
        cancelRelease.countDown();
      }
      assertThat(pending.get(5, TimeUnit.SECONDS).statusCode()).isEqualTo(201);
    }
    verify(toss, times(1)).approveBilling(anyString(), anyString(), any(), anyString(), isNull());
    verify(toss, times(1)).cancel(anyString(), anyString(), any(), anyString());
  }

  @Test
  void 같은_UUID의_청구와_라인과_결제는_각_스키마에서_독립적이고_원장은_불변이다() {
    UUID customer = UUID.randomUUID();
    UUID invoice = UUID.randomUUID();
    UUID line = UUID.randomUUID();
    UUID attempt = UUID.randomUUID();
    for (String mode : List.of("test", "live")) {
      createCustomer(mode, customer, "same-id-customer");
      long supply = mode.equals("test") ? 1000 : 2000;
      transactions.write(
          scope(mode),
          () -> {
            invoices.saveAndFlush(
                new Invoice(
                    invoice,
                    organizationId,
                    customer,
                    mode + "-customer",
                    month().toString(),
                    supply,
                    supply / 10,
                    OffsetDateTime.now()));
            jdbc.update(
                "INSERT INTO invoice_line(id,organization_id,invoice_id,dimension_values,quantity,unit_price,amount,line_type,tier_index) VALUES (?,?,?,'{}',1,?,?, 'base_fee',0)",
                line,
                organizationId,
                invoice,
                supply,
                supply);
            jdbc.update(
                "INSERT INTO payment_attempt(id,organization_id,customer_id,invoice_id,mode,amount,order_name,status,requested_at,completed_at,payment_key) VALUES (?,?,?,?,?,?,?,'done',now(),now(),?)",
                attempt,
                organizationId,
                customer,
                invoice,
                mode,
                supply + supply / 10,
                "fixture",
                mode + "-payment");
            return null;
          });
      assertThat(json(get("/v1/invoices/" + invoice, mode)).get("supply_amount").asLong())
          .isEqualTo(supply);
      assertThat(
              json(get("/v1/invoices/" + invoice + "/payment-attempts", mode))
                  .at("/payment_attempts/0/amount")
                  .asLong())
          .isEqualTo(supply + supply / 10);
      org.assertj.core.api.Assertions.assertThatThrownBy(
              () ->
                  transactions.read(
                      scope(mode),
                      () ->
                          jdbc.queryForObject(
                              "SELECT count(*) FROM "
                                  + (mode.equals("test") ? "live" : "test")
                                  + ".invoice",
                              Long.class)))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
      org.assertj.core.api.Assertions.assertThatThrownBy(
              () ->
                  transactions.read(
                      scope(mode),
                      () ->
                          jdbc.queryForObject(
                              "SELECT count(*) FROM shared.organization", Long.class)))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
      org.assertj.core.api.Assertions.assertThatThrownBy(
              () ->
                  transactions.write(
                      scope(mode),
                      () -> jdbc.update("UPDATE invoice SET supply_amount=1 WHERE id=?", invoice)))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
      org.assertj.core.api.Assertions.assertThatThrownBy(
              () ->
                  ownerJdbc()
                      .update(
                          "UPDATE " + mode + ".payment_attempt SET amount=1 WHERE id=?", attempt))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
      org.assertj.core.api.Assertions.assertThatThrownBy(
              () -> ownerJdbc().update("DELETE FROM " + mode + ".invoice_line WHERE id=?", line))
          .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
  }

  private java.net.http.HttpResponse<String> saveCredentialResponse(String mode, String suffix) {
    return request(
        "PUT",
        "/v1/toss-payments-credentials/" + mode,
        session,
        mode,
        "{\"client_key\":\""
            + mode
            + "_ck_"
            + suffix
            + "\",\"secret_key\":\""
            + mode
            + "_sk_"
            + suffix
            + "\"}");
  }

  private void saveCredential(String mode, String suffix) {
    var r = saveCredentialResponse(mode, suffix);
    assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
  }

  private UUID preparePayable(String mode, UUID customer, boolean priced) {
    saveCredential(mode, "fixture");
    createCustomer(mode, customer, "customer-" + customer);
    UUID id;
    if (priced) {
      assertThat(createMetric(mode, "priced", "sum").statusCode()).isEqualTo(201);
      assertThat(
              request(
                      "POST",
                      "/v1/billable-metrics/priced/price-rates",
                      session,
                      mode,
                      "{\"dimension_values\":{},\"unit_price\":10}")
                  .statusCode())
          .isEqualTo(201);
      assertThat(
              request(
                      "POST",
                      "/v1/events",
                      session,
                      mode,
                      eventJson("same-priced", customer, "{\"quantity\":100}"))
                  .statusCode())
          .isEqualTo(200);
      var finalized = request("POST", "/v1/invoices?month=" + month(), session, mode, "{}");
      assertThat(finalized.statusCode()).as(finalized.body()).isEqualTo(200);
      id =
          UUID.fromString(
              json(get("/v1/invoices?customer_id=" + customer, mode))
                  .at("/invoices/0/id")
                  .asString());
    } else
      id =
          transactions.write(
              scope(mode),
              () ->
                  invoices
                      .saveAndFlush(
                          new Invoice(
                              UUID.randomUUID(),
                              organizationId,
                              customer,
                              "fixture",
                              month().toString(),
                              1000,
                              100,
                              OffsetDateTime.now()))
                      .getId());
    transactions.write(
        scope(mode),
        () -> {
          var credential =
              credentials
                  .findById(
                      new com.meterengine.payment.entity.TossPaymentsCredentialId(
                          organizationId, mode))
                  .orElseThrow();
          keys.saveAndFlush(
              new BillingKey(
                      organizationId,
                      customer,
                      mode,
                      mode + "-billing",
                      "11",
                      "1234****",
                      OffsetDateTime.now(),
                      "v1",
                      OffsetDateTime.now())
                  .pinCredential(credential.getUpdatedAt()));
          return null;
        });
    return id;
  }

  private java.net.http.HttpResponse<String> charge(String mode, UUID invoice) {
    return request("POST", "/v1/invoices/" + invoice + "/payment-attempts", session, mode, null);
  }

  private java.net.http.HttpResponse<String> cancel(
      String mode, UUID attempt, UUID key, String body) {
    return request(
        "POST",
        "/v1/payment-attempts/" + attempt + "/cancellations",
        session,
        mode,
        body,
        "Idempotency-Key",
        key.toString());
  }

  private static YearMonth month() {
    return YearMonth.now(com.meterengine.metric.service.BillableMetricUsageService.BILLING_ZONE);
  }

  private static UUID contractId(UUID org, UUID key, boolean live) {
    var bytes =
        ByteBuffer.wrap(
            digest((org + ":" + (live ? "live:" : "") + key).getBytes(StandardCharsets.UTF_8)));
    return new UUID(bytes.getLong(), bytes.getLong());
  }

  private static String fingerprint(UUID attempt, Long amount, String reason) {
    return HexFormat.of()
        .formatHex(
            digest((attempt + "\n" + amount + "\n" + reason).getBytes(StandardCharsets.UTF_8)));
  }

  private static byte[] digest(byte[] value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value);
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }
}
