package com.meterengine.invoiceview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meterengine.SideEffectTestSupport;
import com.meterengine.global.database.*;
import com.meterengine.global.security.SecureTokens;
import com.meterengine.invoice.entity.Invoice;
import com.meterengine.invoice.repository.InvoiceRepository;
import com.meterengine.invoiceview.entity.InvoiceViewLink;
import com.meterengine.invoiceview.repository.InvoiceViewLinkRepository;
import com.meterengine.payment.client.TossPaymentsBilling;
import com.meterengine.payment.client.TossPaymentsClient;
import com.meterengine.payment.client.TossPaymentsResult;
import com.meterengine.payment.entity.TossPaymentsCredential;
import com.meterengine.payment.repository.TossPaymentsCredentialRepository;
import com.meterengine.payment.service.PaymentMethodRegistrationService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class SchemaPublicLookupIntegrationTest extends SideEffectTestSupport {
  @MockitoBean TossPaymentsClient toss;
  @Autowired PublicResourceLocator locator;
  @Autowired InvoiceRepository invoices;
  @Autowired InvoiceViewLinkRepository links;
  @Autowired TossPaymentsCredentialRepository credentials;
  @Autowired PaymentMethodRegistrationService registrations;

  @AfterEach
  void clearHook() {
    MeteringEnvironmentTestConfig.AFTER_QUERY.set(sql -> {});
  }

  @Test
  void 공개_조회는_정확히_한_토큰의_scope만_선택하고_같은_UUID와_화면헤더를_구분한다() {
    UUID id = UUID.randomUUID();
    String test = seedInvoice("test", id, SecureTokens.generate("me_invoice_"));
    String live = seedInvoice("live", id, SecureTokens.generate("me_invoice_"));
    EnvironmentTestConfig.CONNECTION_TRACES.clear();
    for (var entry : java.util.Map.of("test", test, "live", live).entrySet()) {
      var response =
          request(
              "POST",
              "/v1/public/invoice-links/lookup",
              null,
              entry.getKey().equals("test") ? "live" : "test",
              "{\"token\":\"" + entry.getValue() + "\"}");
      assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
      assertThat(json(response).at("/payment/mode").asString()).isEqualTo(entry.getKey());
      var unheaded =
          request(
              "POST",
              "/v1/public/invoice-links/lookup",
              null,
              null,
              "{\"token\":\"" + entry.getValue() + "\"}");
      assertThat(unheaded.statusCode()).as(unheaded.body()).isEqualTo(200);
      assertThat(json(unheaded).at("/payment/mode").asString()).isEqualTo(entry.getKey());
    }
    assertThat(EnvironmentTestConfig.CONNECTION_TRACES)
        .anyMatch(
            trace -> trace.role().equals("meterengine_shared") && trace.schema().equals("shared"));
    for (String scope : List.of("test", "live"))
      assertThat(EnvironmentTestConfig.CONNECTION_TRACES)
          .anyMatch(
              trace -> trace.role().equals("meterengine_" + scope) && trace.schema().equals(scope));
    assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    assertThat(TransactionSynchronizationManager.getResourceMap()).isEmpty();
    assertThat(DatabaseScopes.current()).isEqualTo(DatabaseScope.SHARED);
  }

  @Test
  void 토큰이_없거나_두_schema에_같으면_공개조회와_PG와_업무감사를_거절한다() {
    String token = SecureTokens.generate("me_invoice_");
    seedInvoice("test", UUID.randomUUID(), token);
    seedInvoice("live", UUID.randomUUID(), token);
    for (String value : List.of(token, SecureTokens.generate("me_invoice_"))) {
      assertThat(locator.invoice(SecureTokens.hash(value))).isEmpty();
      var response =
          request(
              "POST",
              "/v1/public/invoice-links/lookup",
              null,
              "live",
              "{\"token\":\"" + value + "\"}");
      assertThat(response.statusCode()).as(response.body()).isEqualTo(404);
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM shared.audit_log WHERE organization_id=? AND action='invoice_viewed'",
                Long.class,
                organizationId))
        .isZero();
    org.mockito.Mockito.verifyNoInteractions(toss);
  }

  @Test
  void shared_조회뒤_폐기한_링크는_새_업무트랜잭션에서_거절한다() {
    String token = seedInvoice("test", UUID.randomUUID(), SecureTokens.generate("me_invoice_"));
    afterRoute(
        "invoice_view_link_route",
        () ->
            transactions.write(
                DatabaseScope.TEST,
                () -> {
                  jdbc.update(
                      "UPDATE invoice_view_link SET revoked_at=now() WHERE organization_id=?",
                      organizationId);
                  return null;
                }));
    var response =
        request(
            "POST",
            "/v1/public/invoice-links/lookup",
            null,
            "live",
            "{\"token\":\"" + token + "\"}");
    assertThat(response.statusCode()).as(response.body()).isEqualTo(404);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM shared.audit_log WHERE organization_id=? AND action='invoice_viewed'",
                Long.class,
                organizationId))
        .isZero();
  }

  @Test
  void shared_조회뒤_사용된_등록은_PG발급전에_거절한다() {
    var registration = seedRegistration("test");
    afterRoute(
        "payment_registration_route",
        () ->
            transactions.write(
                DatabaseScope.TEST,
                () -> {
                  jdbc.update(
                      "UPDATE payment_method_registration SET used_at=now() WHERE organization_id=?",
                      organizationId);
                  return null;
                }));
    var response =
        request(
            "POST",
            "/v1/payment-method-registrations/" + registration.token() + "/billing-keys",
            null,
            "live",
            cardRequest(registration.customerId()));
    assertThat(response.statusCode()).as(response.body()).isEqualTo(404);
    verify(toss, never()).issueBillingKey(anyString(), anyString(), anyString());
  }

  @Test
  void 같은_주문이_두_schema에_있으면_웹훅은_PG와_업무변경없이_무시한다() {
    UUID order = UUID.randomUUID();
    for (String mode : List.of("test", "live")) seedPending(mode, order);
    var response =
        request("POST", "/v1/toss-payments-webhooks/" + organizationId, null, "test", event(order));
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    org.mockito.Mockito.verifyNoInteractions(toss);
    for (String mode : List.of("test", "live"))
      assertThat(
              ownerJdbc()
                  .queryForObject(
                      "SELECT status FROM " + mode + ".payment_attempt WHERE id=?",
                      String.class,
                      order))
          .isEqualTo("pending");
  }

  @Test
  void 주문_탐색뒤_최종상태로_바뀌면_웹훅은_PG를_조회하지_않는다() {
    UUID order = UUID.randomUUID();
    seedPending("test", order);
    afterRoute(
        "payment_order_route",
        () ->
            transactions.write(
                DatabaseScope.TEST,
                () -> {
                  jdbc.update(
                      "UPDATE payment_attempt SET status='failed',completed_at=now(),failure_code='FIXTURE',failure_message='fixture' WHERE id=?",
                      order);
                  return null;
                }));
    var response =
        request("POST", "/v1/toss-payments-webhooks/" + organizationId, null, "live", event(order));
    assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
    org.mockito.Mockito.verifyNoInteractions(toss);
  }

  @Test
  void 등록해시가_두_schema에_있으면_공개조회와_발급은_PG없이_거절한다() {
    var registration = seedRegistration("test");
    createCustomer("live", registration.customerId(), "duplicate-registration");
    in(
        "live",
        () ->
            jdbc.update(
                "INSERT INTO payment_method_registration(id,organization_id,customer_id,token_hash,mode,created_at,expires_at) VALUES (?,?,?,?,'live',now(),now()+interval '1 day')",
                UUID.randomUUID(),
                organizationId,
                registration.customerId(),
                java.util.HexFormat.of().formatHex(SecureTokens.hash(registration.token()))));
    var get =
        request(
            "GET", "/v1/payment-method-registrations/" + registration.token(), null, "test", null);
    var post =
        request(
            "POST",
            "/v1/payment-method-registrations/" + registration.token() + "/billing-keys",
            null,
            "live",
            cardRequest(registration.customerId()));
    assertThat(get.statusCode()).as(get.body()).isEqualTo(404);
    assertThat(post.statusCode()).as(post.body()).isEqualTo(404);
    org.mockito.Mockito.verifyNoInteractions(toss);
  }

  @Test
  void 만료된_링크와_등록은_locator가_찾아도_업무와_PG를_실행하지_않는다() {
    UUID customer = UUID.randomUUID();
    createCustomer("test", customer, "expired");
    String token = SecureTokens.generate("me_invoice_");
    in(
        "test",
        () -> {
          UUID invoice = UUID.randomUUID();
          invoices.saveAndFlush(
              new Invoice(
                  invoice,
                  organizationId,
                  customer,
                  "고객",
                  "2026-08",
                  1000,
                  100,
                  OffsetDateTime.now()));
          links.saveAndFlush(
              new InvoiceViewLink(
                  UUID.randomUUID(),
                  organizationId,
                  invoice,
                  SecureTokens.hash(token),
                  "test",
                  OffsetDateTime.now().minusDays(2),
                  OffsetDateTime.now().minusDays(1)));
          jdbc.update(
              "INSERT INTO payment_method_registration(id,organization_id,customer_id,token_hash,mode,created_at,expires_at) VALUES (?,?,?,?,'test',now()-interval '2 days',now()-interval '1 day')",
              UUID.randomUUID(),
              organizationId,
              customer,
              java.util.HexFormat.of().formatHex(SecureTokens.hash(token)));
        });
    assertThat(locator.invoice(SecureTokens.hash(token))).isPresent();
    assertThat(locator.registration(java.util.HexFormat.of().formatHex(SecureTokens.hash(token))))
        .isPresent();
    assertThat(
            request(
                    "POST",
                    "/v1/public/invoice-links/lookup",
                    null,
                    "live",
                    "{\"token\":\"" + token + "\"}")
                .statusCode())
        .isEqualTo(404);
    assertThat(
            request("GET", "/v1/payment-method-registrations/" + token, null, "live", null)
                .statusCode())
        .isEqualTo(404);
    org.mockito.Mockito.verifyNoInteractions(toss);
  }

  @Test
  void shared_조회뒤_등록이_만료되면_PG발급전에_거절한다() {
    registrationChangedBeforeIssue("expiry", 404);
  }

  @Test
  void shared_조회뒤_조직이_정지되면_PG발급전에_거절한다() {
    registrationChangedBeforeIssue("organization", 404);
  }

  @Test
  void shared_조회뒤_고객을_보관하면_PG발급전에_거절한다() {
    registrationChangedBeforeIssue("customer", 409);
  }

  @Test
  void shared_조회뒤_자격을_보관하면_PG발급전에_거절한다() {
    registrationChangedBeforeIssue("credential", 409);
  }

  private void registrationChangedBeforeIssue(String change, int expected) {
    var registration = seedRegistration("test");
    afterRoute(
        "payment_registration_route",
        () ->
            transactions.write(
                change.equals("organization") ? DatabaseScope.SHARED : DatabaseScope.TEST,
                () -> {
                  switch (change) {
                    case "expiry" ->
                        jdbc.update(
                            "UPDATE payment_method_registration SET created_at=now()-interval '2 days',expires_at=now()-interval '1 second' WHERE organization_id=?",
                            organizationId);
                    case "organization" ->
                        jdbc.update(
                            "UPDATE shared.organization SET status='suspended' WHERE id=?",
                            organizationId);
                    case "customer" ->
                        jdbc.update(
                            "UPDATE customer SET archived_at=now() WHERE organization_id=? AND id=?",
                            organizationId,
                            registration.customerId());
                    case "credential" ->
                        jdbc.update(
                            "UPDATE toss_payments_credential SET archived_at=now() WHERE organization_id=?",
                            organizationId);
                    default -> throw new IllegalArgumentException(change);
                  }
                  return null;
                }));
    var response =
        request(
            "POST",
            "/v1/payment-method-registrations/" + registration.token() + "/billing-keys",
            null,
            "live",
            cardRequest(registration.customerId()));
    assertThat(response.statusCode()).as(response.body()).isEqualTo(expected);
    org.mockito.Mockito.verifyNoInteractions(toss);
    assertThat(
            ownerJdbc()
                .queryForObject(
                    "SELECT used_at FROM test.payment_method_registration WHERE organization_id=?",
                    OffsetDateTime.class,
                    organizationId))
        .isNull();
    assertThat(
            ownerJdbc()
                .queryForObject(
                    "SELECT count(*) FROM test.billing_key WHERE organization_id=?",
                    Long.class,
                    organizationId))
        .isZero();
  }

  @Test
  void PG수락뒤_도입사가_정지되면_원래_자격으로_보상삭제하고_등록을_사용하지_않는다() {
    var registration = seedRegistration("test");
    when(toss.issueBillingKey(anyString(), anyString(), anyString()))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              transactions.write(
                  DatabaseScope.SHARED,
                  () -> {
                    jdbc.update(
                        "UPDATE shared.organization SET status='suspended' WHERE id=?",
                        organizationId);
                    return null;
                  });
              return new TossPaymentsResult.Success<>(
                  new TossPaymentsBilling(
                      "accepted-suspended",
                      OffsetDateTime.now(),
                      new TossPaymentsBilling.Card("11", "1234****")));
            });
    when(toss.deleteBillingKey(anyString(), anyString()))
        .thenReturn(new TossPaymentsResult.Success<>(null));
    var response =
        request(
            "POST",
            "/v1/payment-method-registrations/" + registration.token() + "/billing-keys",
            null,
            "live",
            cardRequest(registration.customerId()));
    assertThat(response.statusCode()).as(response.body()).isEqualTo(404);
    verify(toss).deleteBillingKey("test_sk_public", "accepted-suspended");
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
                    "SELECT used_at FROM test.payment_method_registration WHERE organization_id=?",
                    OffsetDateTime.class,
                    organizationId))
        .isNull();
  }

  @Test
  void PG수락뒤_등록경합은_원래_자격의_보상삭제로_끝나고_카드를_저장하지_않는다() {
    var registration = seedRegistration("test");
    when(toss.issueBillingKey(anyString(), anyString(), anyString()))
        .thenAnswer(
            invocation -> {
              assertThat(DatabaseScopes.current()).isEqualTo(DatabaseScope.TEST);
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              assertThat(TransactionSynchronizationManager.getResourceMap()).isEmpty();
              assertThat(invocation.<String>getArgument(0)).isEqualTo("test_sk_public");
              transactions.write(
                  DatabaseScope.TEST,
                  () -> {
                    jdbc.update(
                        "UPDATE payment_method_registration SET used_at=now() WHERE organization_id=?",
                        organizationId);
                    return null;
                  });
              return new TossPaymentsResult.Success<>(
                  new TossPaymentsBilling(
                      "accepted-card",
                      OffsetDateTime.now(),
                      new TossPaymentsBilling.Card("11", "1234****")));
            });
    when(toss.deleteBillingKey(anyString(), anyString()))
        .thenReturn(new TossPaymentsResult.Success<>(null));
    var response =
        request(
            "POST",
            "/v1/payment-method-registrations/" + registration.token() + "/billing-keys",
            null,
            "live",
            cardRequest(registration.customerId()));
    assertThat(response.statusCode()).as(response.body()).isEqualTo(404);
    verify(toss).deleteBillingKey("test_sk_public", "accepted-card");
    assertThat(
            ownerJdbc()
                .queryForObject(
                    "SELECT count(*) FROM test.billing_key WHERE organization_id=?",
                    Long.class,
                    organizationId))
        .isZero();
  }

  private String seedInvoice(String mode, UUID invoice, String token) {
    UUID customer = UUID.randomUUID();
    createCustomer(mode, customer, "public-" + customer);
    in(
        mode,
        () -> {
          invoices.saveAndFlush(
              new Invoice(
                  invoice,
                  organizationId,
                  customer,
                  "고객",
                  "2026-08",
                  1000,
                  100,
                  OffsetDateTime.now()));
          links.saveAndFlush(
              new InvoiceViewLink(
                  UUID.randomUUID(),
                  organizationId,
                  invoice,
                  SecureTokens.hash(token),
                  mode,
                  OffsetDateTime.now(),
                  OffsetDateTime.now().plusDays(1)));
        });
    return token;
  }

  private void seedPending(String mode, UUID order) {
    UUID invoice = UUID.randomUUID();
    seedInvoice(mode, invoice, SecureTokens.generate("me_invoice_"));
    in(
        mode,
        () ->
            jdbc.update(
                "INSERT INTO payment_attempt(id,organization_id,customer_id,invoice_id,mode,amount,order_name,status,requested_at) SELECT ?,organization_id,customer_id,id,?,1100,'fixture','pending',now() FROM invoice WHERE id=?",
                order,
                mode,
                invoice));
  }

  private static String event(UUID order) {
    return "{\"eventType\":\"PAYMENT_STATUS_CHANGED\",\"data\":{\"orderId\":\""
        + order
        + "\",\"paymentKey\":\"fixture\"}}";
  }

  private com.meterengine.payment.dto.PaymentMethodRegistrationResponse seedRegistration(
      String mode) {
    UUID customer = UUID.randomUUID();
    createCustomer(mode, customer, "registration-" + customer);
    in(
        mode,
        () ->
            credentials.saveAndFlush(
                new TossPaymentsCredential(
                    organizationId,
                    mode,
                    mode + "_ck_public",
                    mode + "_sk_public",
                    OffsetDateTime.now().minusHours(1))));
    return in(mode, () -> registrations.create(organizationId, customer));
  }

  private static String cardRequest(UUID customer) {
    return "{\"auth_key\":\"fixture\",\"customer_key\":\""
        + customer
        + "\",\"consent_document_version\":\"v1\"}";
  }

  private void afterRoute(String table, Runnable mutation) {
    var once = new AtomicBoolean();
    MeteringEnvironmentTestConfig.AFTER_QUERY.set(
        sql -> {
          if (sql.contains(table) && once.compareAndSet(false, true)) {
            try (var executor = Executors.newSingleThreadExecutor()) {
              executor.submit(mutation).get(10, java.util.concurrent.TimeUnit.SECONDS);
            } catch (Exception exception) {
              throw new IllegalStateException(exception);
            }
          }
        });
  }
}
