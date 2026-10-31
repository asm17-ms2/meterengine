package com.meterengine.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.invoice.entity.Invoice;
import com.meterengine.invoice.repository.InvoiceRepository;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class InvoicePaymentIntegrationTest {

  private static final OffsetDateTime FIRST_REQUESTED_AT =
      OffsetDateTime.parse("2026-10-01T00:00:00Z");
  private static final OffsetDateTime FIRST_COMPLETED_AT =
      OffsetDateTime.parse("2026-10-01T00:00:05Z");
  private static final OffsetDateTime SECOND_REQUESTED_AT =
      OffsetDateTime.parse("2026-10-02T00:00:00Z");
  private static final OffsetDateTime SECOND_COMPLETED_AT =
      OffsetDateTime.parse("2026-10-02T00:00:05Z");
  private static final OffsetDateTime THIRD_REQUESTED_AT =
      OffsetDateTime.parse("2026-10-03T00:00:00Z");
  private static final OffsetDateTime THIRD_COMPLETED_AT =
      OffsetDateTime.parse("2026-10-03T00:00:05Z");

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private InvoiceRepository invoiceRepository;

  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
  }

  @Test
  void 일곱_상태를_그_모드의_최신_시도와_취소_합으로_정하고_금액_필드를_채운다() {
    UUID organizationId = insertOrganization();
    UUID notRequired = insertInvoice(organizationId, insertCustomer(organizationId, "가"), 0);
    UUID unpaid = insertInvoice(organizationId, insertCustomer(organizationId, "나"), 12000);
    UUID pending = insertInvoice(organizationId, insertCustomer(organizationId, "다"), 12000);
    insertPendingAttempt(organizationId, pending, "test", FIRST_REQUESTED_AT);
    UUID paid = insertInvoice(organizationId, insertCustomer(organizationId, "라"), 12000);
    insertFailedAttempt(
        organizationId,
        paid,
        "test",
        "REJECT_CARD_PAYMENT",
        FIRST_REQUESTED_AT,
        FIRST_COMPLETED_AT);
    insertDoneAttempt(organizationId, paid, "test", SECOND_REQUESTED_AT, SECOND_COMPLETED_AT);
    UUID partiallyCanceled =
        insertInvoice(organizationId, insertCustomer(organizationId, "마"), 12000);
    UUID partiallyCanceledAttempt =
        insertDoneAttempt(
            organizationId, partiallyCanceled, "test", FIRST_REQUESTED_AT, FIRST_COMPLETED_AT);
    insertCancellation(organizationId, partiallyCanceledAttempt, "done", 3000);
    insertCancellation(organizationId, partiallyCanceledAttempt, "done", 1000);
    insertCancellation(organizationId, partiallyCanceledAttempt, "failed", 5000);
    insertCancellation(organizationId, partiallyCanceledAttempt, "pending", 2000);
    UUID canceled = insertInvoice(organizationId, insertCustomer(organizationId, "바"), 12000);
    UUID canceledAttempt =
        insertDoneAttempt(organizationId, canceled, "test", FIRST_REQUESTED_AT, FIRST_COMPLETED_AT);
    insertCancellation(organizationId, canceledAttempt, "done", 10000);
    insertCancellation(organizationId, canceledAttempt, "done", 3200);
    UUID failedCustomer = insertCustomer(organizationId, "사");
    UUID failed = insertInvoice(organizationId, failedCustomer, 12000);
    insertFailedAttempt(
        organizationId,
        failed,
        "test",
        "REJECT_CARD_PAYMENT",
        FIRST_REQUESTED_AT,
        FIRST_COMPLETED_AT);
    insertBillingKey(organizationId, failedCustomer, FIRST_REQUESTED_AT);

    assertThat(list(organizationId, ""))
        .hasStatusOk()
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {"invoice_payments": [
              {"invoice_id": "%s", "status": "not_required", "mode": "test", "amount": 0,
               "canceled_amount": 0, "attempt_count": 0, "last_failure_code": null,
               "next_retry_at": null, "completed_at": null},
              {"invoice_id": "%s", "status": "unpaid", "mode": "test", "amount": 13200,
               "canceled_amount": 0, "attempt_count": 0, "last_failure_code": null,
               "next_retry_at": null, "completed_at": null},
              {"invoice_id": "%s", "status": "pending", "mode": "test", "amount": 13200,
               "canceled_amount": 0, "attempt_count": 1, "last_failure_code": null,
               "next_retry_at": null, "completed_at": null},
              {"invoice_id": "%s", "status": "paid", "mode": "test", "amount": 13200,
               "canceled_amount": 0, "attempt_count": 2, "last_failure_code": null,
               "next_retry_at": null, "completed_at": "2026-10-02T09:00:05+09:00"},
              {"invoice_id": "%s", "status": "partially_canceled", "mode": "test", "amount": 13200,
               "canceled_amount": 4000, "attempt_count": 1, "last_failure_code": null,
               "next_retry_at": null, "completed_at": "2026-10-01T09:00:05+09:00"},
              {"invoice_id": "%s", "status": "canceled", "mode": "test", "amount": 13200,
               "canceled_amount": 13200, "attempt_count": 1, "last_failure_code": null,
               "next_retry_at": null, "completed_at": "2026-10-01T09:00:05+09:00"},
              {"invoice_id": "%s", "status": "failed", "mode": "test", "amount": 13200,
               "canceled_amount": 0, "attempt_count": 1, "last_failure_code": "REJECT_CARD_PAYMENT",
               "next_retry_at": "2026-10-02T09:00:05+09:00",
               "completed_at": "2026-10-01T09:00:05+09:00"}
            ]}
            """
                .formatted(
                    notRequired, unpaid, pending, paid, partiallyCanceled, canceled, failed));
  }

  @Test
  void live_모드_시도는_test_설정에서_보이지_않고_live로_바꾸면_보인다() {
    UUID organizationId = insertOrganization();
    UUID invoiceId = insertInvoice(organizationId, insertCustomer(organizationId, "아크메"), 12000);
    insertDoneAttempt(organizationId, invoiceId, "live", FIRST_REQUESTED_AT, FIRST_COMPLETED_AT);

    assertThat(list(organizationId, ""))
        .hasStatusOk()
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {"invoice_payments": [
              {"invoice_id": "%s", "status": "unpaid", "mode": "test", "amount": 13200,
               "canceled_amount": 0, "attempt_count": 0, "last_failure_code": null,
               "next_retry_at": null, "completed_at": null}
            ]}
            """
                .formatted(invoiceId));

    switchToLive(organizationId);

    assertThat(list(organizationId, ""))
        .hasStatusOk()
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {"invoice_payments": [
              {"invoice_id": "%s", "status": "paid", "mode": "live", "amount": 13200,
               "canceled_amount": 0, "attempt_count": 1, "last_failure_code": null,
               "next_retry_at": null, "completed_at": "2026-10-01T09:00:05+09:00"}
            ]}
            """
                .formatted(invoiceId));
  }

  @Test
  void next_retry_at은_재시도_대상_코드면_채워지고_INVALID_CARD_EXPIRATION이거나_시도_수가_상한이면_null이다() {
    UUID organizationId = insertOrganization();
    UUID expired = insertInvoice(organizationId, insertCardCustomer(organizationId, "가"), 12000);
    insertFailedAttempt(
        organizationId,
        expired,
        "test",
        "INVALID_CARD_EXPIRATION",
        FIRST_REQUESTED_AT,
        FIRST_COMPLETED_AT);
    UUID credentialMissing =
        insertInvoice(organizationId, insertCardCustomer(organizationId, "나"), 12000);
    insertFailedAttempt(
        organizationId,
        credentialMissing,
        "test",
        "toss_payments_credential_missing",
        FIRST_REQUESTED_AT,
        FIRST_COMPLETED_AT);
    UUID serverError =
        insertInvoice(organizationId, insertCardCustomer(organizationId, "다"), 12000);
    insertFailedAttempt(
        organizationId, serverError, "test", "HTTP_500", FIRST_REQUESTED_AT, FIRST_COMPLETED_AT);
    insertFailedAttempt(
        organizationId, serverError, "test", "HTTP_500", SECOND_REQUESTED_AT, SECOND_COMPLETED_AT);
    UUID exhausted = insertInvoice(organizationId, insertCardCustomer(organizationId, "라"), 12000);
    insertFailedAttempt(
        organizationId,
        exhausted,
        "test",
        "REJECT_CARD_PAYMENT",
        FIRST_REQUESTED_AT,
        FIRST_COMPLETED_AT);
    insertFailedAttempt(
        organizationId,
        exhausted,
        "test",
        "REJECT_CARD_PAYMENT",
        SECOND_REQUESTED_AT,
        SECOND_COMPLETED_AT);
    insertFailedAttempt(
        organizationId,
        exhausted,
        "test",
        "REJECT_CARD_PAYMENT",
        THIRD_REQUESTED_AT,
        THIRD_COMPLETED_AT);

    MvcTestResult listed = list(organizationId, "");

    assertThat(listed)
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.invoice_payments[*].invoice_id")
        .asArray()
        .containsExactly(
            expired.toString(),
            credentialMissing.toString(),
            serverError.toString(),
            exhausted.toString());
    assertThat(listed)
        .bodyJson()
        .extractingPath("$.invoice_payments[*].last_failure_code")
        .asArray()
        .containsExactly(
            "INVALID_CARD_EXPIRATION",
            "toss_payments_credential_missing",
            "HTTP_500",
            "REJECT_CARD_PAYMENT");
    assertThat(listed)
        .bodyJson()
        .extractingPath("$.invoice_payments[*].attempt_count")
        .asArray()
        .containsExactly(1, 1, 2, 3);
    assertThat(listed)
        .bodyJson()
        .extractingPath("$.invoice_payments[*].next_retry_at")
        .asArray()
        .containsExactly(null, "2026-10-02T09:00:05+09:00", "2026-10-03T09:00:05+09:00", null);
  }

  @Test
  void 카드_오류로_실패한_뒤_카드를_다시_등록했으면_next_retry_at이_채워지고_실패_전_카드뿐이면_null이다() {
    UUID organizationId = insertOrganization();
    UUID reregisteredCustomer = insertCustomer(organizationId, "가");
    UUID reregistered = insertInvoice(organizationId, reregisteredCustomer, 12000);
    insertFailedAttempt(
        organizationId,
        reregistered,
        "test",
        "INVALID_CARD_EXPIRATION",
        FIRST_REQUESTED_AT,
        FIRST_COMPLETED_AT);
    insertBillingKey(organizationId, reregisteredCustomer, SECOND_COMPLETED_AT);
    UUID oldCardCustomer = insertCustomer(organizationId, "나");
    UUID oldCard = insertInvoice(organizationId, oldCardCustomer, 12000);
    insertFailedAttempt(
        organizationId,
        oldCard,
        "test",
        "INVALID_CARD_EXPIRATION",
        FIRST_REQUESTED_AT,
        FIRST_COMPLETED_AT);
    insertBillingKey(organizationId, oldCardCustomer, FIRST_REQUESTED_AT);

    MvcTestResult listed = list(organizationId, "");

    assertInvoiceIds(listed, reregistered, oldCard);
    assertThat(listed)
        .bodyJson()
        .extractingPath("$.invoice_payments[*].next_retry_at")
        .asArray()
        .containsExactly("2026-10-02T09:00:05+09:00", null);
  }

  @Test
  void next_retry_at은_지워진_빌링키_실패면_지금_카드가_있을_때만이고_NOT_FOUND_PAYMENT면_끝난_지_7일_안일_때만_채워진다() {
    UUID organizationId = insertOrganization();
    UUID removedWithCardCustomer = insertCustomer(organizationId, "가");
    UUID removedWithCard = insertInvoice(organizationId, removedWithCardCustomer, 12000);
    insertFailedAttempt(
        organizationId,
        removedWithCard,
        "test",
        "ALREADY_REMOVED_BILLING_KEY",
        FIRST_REQUESTED_AT,
        FIRST_COMPLETED_AT);
    insertBillingKey(organizationId, removedWithCardCustomer, FIRST_REQUESTED_AT);
    UUID removedWithoutCard =
        insertInvoice(organizationId, insertCustomer(organizationId, "나"), 12000);
    insertFailedAttempt(
        organizationId,
        removedWithoutCard,
        "test",
        "ALREADY_REMOVED_BILLING_KEY",
        FIRST_REQUESTED_AT,
        FIRST_COMPLETED_AT);
    UUID oldNotFound =
        insertInvoice(organizationId, insertCardCustomer(organizationId, "다"), 12000);
    OffsetDateTime oldFailedAt = OffsetDateTime.now().minusDays(7).minusMinutes(1);
    insertFailedAttempt(
        organizationId,
        oldNotFound,
        "test",
        "NOT_FOUND_PAYMENT",
        oldFailedAt.minusSeconds(5),
        oldFailedAt);
    UUID recentNotFound =
        insertInvoice(organizationId, insertCardCustomer(organizationId, "라"), 12000);
    OffsetDateTime recentFailedAt = OffsetDateTime.now().minusDays(1);
    insertFailedAttempt(
        organizationId,
        recentNotFound,
        "test",
        "NOT_FOUND_PAYMENT",
        recentFailedAt.minusSeconds(5),
        recentFailedAt);

    MvcTestResult listed = list(organizationId, "");

    assertInvoiceIds(listed, removedWithCard, removedWithoutCard, oldNotFound, recentNotFound);
    assertThat(listed)
        .bodyJson()
        .extractingPath("$.invoice_payments[*].next_retry_at")
        .asArray()
        .satisfiesExactly(
            nextRetryAt -> assertThat(nextRetryAt).isEqualTo("2026-10-02T09:00:05+09:00"),
            nextRetryAt -> assertThat(nextRetryAt).isNull(),
            nextRetryAt -> assertThat(nextRetryAt).isNull(),
            nextRetryAt -> assertThat(nextRetryAt).isNotNull());
  }

  @Test
  void next_retry_at은_재시도_대상_코드여도_카드를_보관했거나_카드가_없으면_null이다() {
    UUID organizationId = insertOrganization();
    UUID active = insertInvoice(organizationId, insertCardCustomer(organizationId, "가"), 12000);
    insertFailedAttempt(
        organizationId,
        active,
        "test",
        "REJECT_CARD_PAYMENT",
        FIRST_REQUESTED_AT,
        FIRST_COMPLETED_AT);
    UUID archivedCustomer = insertCardCustomer(organizationId, "나");
    UUID archived = insertInvoice(organizationId, archivedCustomer, 12000);
    insertFailedAttempt(
        organizationId,
        archived,
        "test",
        "REJECT_CARD_PAYMENT",
        FIRST_REQUESTED_AT,
        FIRST_COMPLETED_AT);
    jdbcTemplate.update(
        "UPDATE billing_key SET archived_at = now() WHERE organization_id = ? AND customer_id = ?",
        organizationId,
        archivedCustomer);
    UUID withoutCard = insertInvoice(organizationId, insertCustomer(organizationId, "다"), 12000);
    insertFailedAttempt(
        organizationId,
        withoutCard,
        "test",
        "REJECT_CARD_PAYMENT",
        FIRST_REQUESTED_AT,
        FIRST_COMPLETED_AT);

    MvcTestResult listed = list(organizationId, "");

    assertInvoiceIds(listed, active, archived, withoutCard);
    assertThat(listed)
        .bodyJson()
        .extractingPath("$.invoice_payments[*].next_retry_at")
        .asArray()
        .containsExactly("2026-10-02T09:00:05+09:00", null, null);
  }

  @Test
  void 인보이스_목록과_같은_순서이고_month와_customer_id로_거르며_다른_도입사_인보이스는_나오지_않는다() {
    UUID organizationId = insertOrganization();
    UUID zeta = insertCustomer(organizationId, "제타상사");
    UUID acme = insertCustomer(organizationId, "아크메");
    UUID acmeJuly = insertInvoice(organizationId, acme, "2026-07", 10000);
    UUID zetaAugust = insertInvoice(organizationId, zeta, "2026-08", 5000);
    UUID acmeAugust = insertInvoice(organizationId, acme, "2026-08", 12000);
    UUID otherOrganizationId = insertOrganization();
    insertInvoice(otherOrganizationId, insertCustomer(otherOrganizationId, "베타"), "2026-08", 9000);

    assertInvoiceIds(list(organizationId, ""), acmeAugust, zetaAugust, acmeJuly);
    assertInvoiceIds(list(organizationId, "?month=2026-08"), acmeAugust, zetaAugust);
    assertInvoiceIds(list(organizationId, "?customer_id=" + acme), acmeAugust, acmeJuly);
    assertInvoiceIds(
        list(organizationId, "?customer_id=%s&month=2026-07".formatted(acme)), acmeJuly);
    assertInvoiceIds(list(organizationId, "?month=2026-06"));
  }

  @Test
  void customer_id가_없는_고객이거나_다른_도입사_고객이면_404다() {
    UUID organizationId = insertOrganization();
    UUID otherOrganizationId = insertOrganization();
    UUID otherCustomerId = insertCustomer(otherOrganizationId, "베타");

    assertError(
        list(organizationId, "?customer_id=" + UUID.randomUUID()),
        404,
        ErrorCode.CUSTOMER_NOT_FOUND);
    assertError(
        list(organizationId, "?customer_id=" + otherCustomerId), 404, ErrorCode.CUSTOMER_NOT_FOUND);
  }

  @Test
  void 인증이_없으면_401이고_customer_id나_month의_형식이_틀리면_400이다() {
    assertError(
        mvc.get().uri("/v1/invoice-payments").exchange(), 401, ErrorCode.AUTHENTICATION_REQUIRED);
    assertError(
        list(UUID.randomUUID(), "?customer_id=not-a-uuid"), 400, ErrorCode.VALIDATION_ERROR);
    assertError(list(UUID.randomUUID(), "?month=2026-8"), 400, ErrorCode.VALIDATION_ERROR);
    assertError(list(UUID.randomUUID(), "?month=10000-01"), 400, ErrorCode.VALIDATION_ERROR);
  }

  private MvcTestResult list(UUID organizationId, String query) {
    return withOrganization(mvc.get().uri("/v1/invoice-payments" + query), organizationId)
        .exchange();
  }

  private MockMvcTester.MockMvcRequestBuilder withOrganization(
      MockMvcTester.MockMvcRequestBuilder request, UUID organizationId) {
    return request.header(
        "Authorization", TestCredentials.issueMemberSession(jdbcTemplate, organizationId));
  }

  private void assertInvoiceIds(MvcTestResult result, UUID... invoiceIds) {
    assertThat(result)
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.invoice_payments[*].invoice_id")
        .asArray()
        .containsExactly(Arrays.stream(invoiceIds).map(UUID::toString).toArray());
  }

  private void assertError(MvcTestResult result, int status, ErrorCode errorCode) {
    assertThat(result)
        .hasStatus(status)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(errorCode.getCode());
  }

  private void switchToLive(UUID organizationId) {
    jdbcTemplate.update(
        "INSERT INTO payment_setting (organization_id, mode, updated_at) VALUES (?, 'live', now())",
        organizationId);
  }

  private void insertPendingAttempt(
      UUID organizationId, UUID invoiceId, String mode, OffsetDateTime requestedAt) {
    insertAttempt(organizationId, invoiceId, mode, "pending", null, null, requestedAt, null);
  }

  private UUID insertDoneAttempt(
      UUID organizationId,
      UUID invoiceId,
      String mode,
      OffsetDateTime requestedAt,
      OffsetDateTime completedAt) {
    return insertAttempt(
        organizationId,
        invoiceId,
        mode,
        "done",
        "payment-key-" + UUID.randomUUID(),
        null,
        requestedAt,
        completedAt);
  }

  private void insertFailedAttempt(
      UUID organizationId,
      UUID invoiceId,
      String mode,
      String failureCode,
      OffsetDateTime requestedAt,
      OffsetDateTime completedAt) {
    insertAttempt(
        organizationId, invoiceId, mode, "failed", null, failureCode, requestedAt, completedAt);
  }

  private UUID insertAttempt(
      UUID organizationId,
      UUID invoiceId,
      String mode,
      String status,
      String paymentKey,
      String failureCode,
      OffsetDateTime requestedAt,
      OffsetDateTime completedAt) {
    UUID attemptId = UUID.randomUUID();
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
        failureCode,
        failureCode == null ? null : "거절",
        requestedAt,
        completedAt,
        organizationId,
        invoiceId);
    return attemptId;
  }

  private void insertBillingKey(
      UUID organizationId, UUID customerId, OffsetDateTime authenticatedAt) {
    jdbcTemplate.update(
        """
        INSERT INTO billing_key
          (organization_id, customer_id, mode, billing_key, card_issuer_code, card_number,
           authenticated_at, consent_document_version, consented_at)
        VALUES (?, ?, 'test', 'v1:bk', '61', '43301234****123*', ?, '2026-10-01', ?)
        """,
        organizationId,
        customerId,
        authenticatedAt,
        authenticatedAt);
  }

  private void insertCancellation(
      UUID organizationId, UUID paymentAttemptId, String status, long amount) {
    boolean done = "done".equals(status);
    boolean failed = "failed".equals(status);
    jdbcTemplate.update(
        """
        INSERT INTO payment_cancellation
          (id, organization_id, payment_attempt_id, mode, amount, reason, status,
           transaction_key, failure_code, failure_message, requested_at, completed_at)
        VALUES (?, ?, ?, 'test', ?, '고객 요청', ?, ?, ?, ?, ?, ?)
        """,
        UUID.randomUUID(),
        organizationId,
        paymentAttemptId,
        amount,
        status,
        done ? "transaction-key-" + UUID.randomUUID() : null,
        failed ? "NOT_CANCELABLE_AMOUNT" : null,
        failed ? "취소 불가" : null,
        SECOND_REQUESTED_AT,
        done || failed ? SECOND_COMPLETED_AT : null);
  }

  private UUID insertOrganization() {
    return jdbcTemplate.queryForObject(
        "INSERT INTO organization (name) VALUES ('도입사') RETURNING id", UUID.class);
  }

  private UUID insertCardCustomer(UUID organizationId, String name) {
    UUID customerId = insertCustomer(organizationId, name);
    insertBillingKey(organizationId, customerId, FIRST_REQUESTED_AT);
    return customerId;
  }

  private UUID insertCustomer(UUID organizationId, String name) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO customer (organization_id, name) VALUES (?, ?) RETURNING id",
        UUID.class,
        organizationId,
        name);
  }

  private UUID insertInvoice(UUID organizationId, UUID customerId, long supplyAmount) {
    return insertInvoice(organizationId, customerId, "2026-08", supplyAmount);
  }

  private UUID insertInvoice(
      UUID organizationId, UUID customerId, String period, long supplyAmount) {
    Invoice invoice =
        new Invoice(
            UUID.randomUUID(),
            organizationId,
            customerId,
            jdbcTemplate.queryForObject(
                "SELECT name FROM customer WHERE id = ?", String.class, customerId),
            period,
            supplyAmount,
            supplyAmount / 10,
            OffsetDateTime.parse("2026-09-01T04:00:00Z"),
            OffsetDateTime.parse("2026-09-01T05:00:00Z"));
    return invoiceRepository.saveAndFlush(invoice).getId();
  }
}
