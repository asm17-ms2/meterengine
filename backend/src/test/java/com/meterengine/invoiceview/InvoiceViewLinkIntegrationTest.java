package com.meterengine.invoiceview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.jayway.jsonpath.JsonPath;
import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.security.SecureTokens;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class InvoiceViewLinkIntegrationTest {
  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EntityManager entityManager;
  private MockMvcTester mvc;
  private UUID organizationId;
  private UUID customerId;
  private UUID invoiceId;
  private String authorization;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
    organizationId = UUID.randomUUID();
    authorization = TestCredentials.issueApiKey(jdbcTemplate, organizationId, "test", "admin");
    jdbcTemplate.update("UPDATE organization SET name='공급 서비스' WHERE id=?", organizationId);
    customerId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO customer (id,organization_id,name,billing_email) VALUES (?,?,'현재 고객','private@example.com')",
        customerId,
        organizationId);
    invoiceId = insertInvoice(customerId, "2026-10");
    jdbcTemplate.update(
        """
        INSERT INTO invoice_line (id,organization_id,invoice_id,billable_metric_code,
          dimension_values,quantity,unit_price,amount,quantity_numerator,quantity_denominator)
        VALUES (?,?,?,'requests','{"model":"fast"}',100,10,1000,100,1)
        """,
        UUID.randomUUID(),
        organizationId,
        invoiceId);
  }

  @Test
  void 발급은_30일_기본이고_원문은_발급에만_나오며_DB에는_해시만_있다() {
    MvcTestResult created = create(null);
    assertThat(created).hasStatus(201);
    String token = value(created, "$.token");
    assertThat(token).matches("me_invoice_[A-Za-z0-9]{32}");
    assertThat(created.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
    Map<String, Object> row =
        jdbcTemplate.queryForMap("SELECT * FROM invoice_view_link WHERE invoice_id=?", invoiceId);
    assertThat(row).containsEntry("organization_id", organizationId).containsEntry("mode", "test");
    assertThat((byte[]) row.get("token_hash")).hasSize(32);
    assertThat(row.values()).doesNotContain(token);
    assertThat(
            Duration.between(
                ((Timestamp) row.get("created_at")).toInstant(),
                ((Timestamp) row.get("expires_at")).toInstant()))
        .isEqualTo(Duration.ofDays(30));
    MvcTestResult listed = list(authorization, invoiceId);
    assertThat(listed).hasStatusOk();
    assertThat(listed).bodyText().doesNotContain(token).doesNotContain("token_hash");
    assertThat(listed)
        .bodyJson()
        .extractingPath("$.invoice_view_links[0].id")
        .isEqualTo(row.get("id").toString());
  }

  @Test
  void 유효기간은_1일부터_90일까지이며_범위_밖은_저장하지_않는다() {
    for (int days : new int[] {0, -1, 91}) {
      assertError(create("{\"expires_in_days\":" + days + "}"), 400, ErrorCode.VALIDATION_ERROR);
    }
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM invoice_view_link WHERE invoice_id=?",
                Integer.class,
                invoiceId))
        .isZero();
    assertThat(create("{\"expires_in_days\":1}")).hasStatus(201);
    assertThat(create("{\"expires_in_days\":90}")).hasStatus(201);
  }

  @Test
  void read_키는_목록만_조회하고_발급과_폐기는_거부된다() {
    MvcTestResult created = create(null);
    String read = TestCredentials.issueApiKey(jdbcTemplate, organizationId, "test", "read");
    assertThat(list(read, invoiceId)).hasStatusOk();
    assertThat(
            mvc.post()
                .uri("/v1/invoices/{id}/view-links", invoiceId)
                .header("Authorization", read)
                .exchange())
        .hasStatus(403);
    assertThat(revoke(read, invoiceId, UUID.fromString(value(created, "$.id")))).hasStatus(403);
  }

  @Test
  void 다른_도입사와_다른_인보이스의_링크는_목록과_폐기에_쓸_수_없다() {
    MvcTestResult created = create(null);
    UUID linkId = UUID.fromString(value(created, "$.id"));
    String other = TestCredentials.issueApiKey(jdbcTemplate, UUID.randomUUID(), "test", "admin");
    assertError(list(other, invoiceId), 404, ErrorCode.INVOICE_NOT_FOUND);
    assertError(revoke(other, invoiceId, linkId), 404, ErrorCode.INVOICE_NOT_FOUND);
    assertError(
        mvc.post()
            .uri("/v1/invoices/{id}/view-links", invoiceId)
            .header("Authorization", other)
            .exchange(),
        404,
        ErrorCode.INVOICE_NOT_FOUND);
    UUID anotherInvoice = insertInvoice(customerId, "2026-09");
    assertError(
        revoke(authorization, anotherInvoice, linkId), 404, ErrorCode.INVOICE_VIEW_LINK_NOT_FOUND);
    assertThat(lookup(value(created, "$.token"))).hasStatusOk();
  }

  @Test
  void 공개_조회는_저장한_금액과_고객명만_싣고_반복_가능하며_비밀을_싣지_않는다() {
    MvcTestResult created = create(null);
    String token = value(created, "$.token");
    MvcTestResult found = lookup(token);
    assertThat(found).hasStatusOk();
    assertThat(found).bodyJson().extractingPath("$.organization_name").isEqualTo("공급 서비스");
    assertThat(found).bodyJson().extractingPath("$.customer_name").isEqualTo("확정 고객");
    assertThat(found).bodyJson().extractingPath("$.total_amount").isEqualTo(1100);
    String number = value(found, "$.invoice_number");
    assertThat(number).matches("ME-202610-[0-9]{8,}");
    assertThat(
            mvc.get()
                .uri("/v1/invoices/{id}", invoiceId)
                .header("Authorization", authorization)
                .exchange())
        .bodyJson()
        .extractingPath("$.invoice_number")
        .isEqualTo(number);
    assertThat(found).bodyJson().extractingPath("$.lines[0].amount").isEqualTo(1000);
    assertThat(found).bodyJson().extractingPath("$.payment.status").isEqualTo("unpaid");
    assertThat(found).bodyJson().extractingPath("$.payment.outstanding_amount").isEqualTo(1100);
    assertThat(found)
        .bodyText()
        .doesNotContain(
            token,
            "token_hash",
            "private@example.com",
            organizationId.toString(),
            customerId.toString(),
            invoiceId.toString(),
            "payment_key",
            "failure_code");
    assertThat(found.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
    assertThat(found.getResponse().getHeader("Referrer-Policy")).isEqualTo("no-referrer");
    assertThat(found.getResponse().getHeader("X-Robots-Tag")).isEqualTo("noindex, nofollow");
    assertThat(lookup(token)).hasStatusOk();
  }

  @Test
  void 표시_번호는_인보이스마다_고유하고_재조회와_링크_재발급에도_유지된다() {
    String token = value(create(null), "$.token");
    String original = value(lookup(token), "$.invoice_number");
    assertThat(value(lookup(value(create(null), "$.token")), "$.invoice_number"))
        .isEqualTo(original);
    UUID nextInvoice = insertInvoice(customerId, "2026-09");
    MvcTestResult next =
        mvc.post()
            .uri("/v1/invoices/{id}/view-links", nextInvoice)
            .header("Authorization", authorization)
            .exchange();
    assertThat(value(lookup(value(next, "$.token")), "$.invoice_number"))
        .matches("ME-202609-[0-9]{8,}")
        .isNotEqualTo(original);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(DISTINCT display_number) FROM invoice WHERE organization_id=?",
                Integer.class,
                organizationId))
        .isEqualTo(2);
  }

  @Test
  void 없는_토큰과_만료와_폐기는_같은_404이고_폐기는_반복해도_같다() {
    MvcTestResult created = create(null);
    String token = value(created, "$.token");
    UUID linkId = UUID.fromString(value(created, "$.id"));
    MvcTestResult revoked = revoke(authorization, invoiceId, linkId);
    assertThat(revoked).hasStatusOk();
    assertThat(revoke(authorization, invoiceId, linkId))
        .bodyJson()
        .isStrictlyEqualTo(json(revoked));
    MvcTestResult unknown = lookup("me_invoice_" + "z".repeat(32));
    assertError(unknown, 404, ErrorCode.INVOICE_VIEW_LINK_NOT_FOUND);
    assertThat(lookup(token)).bodyJson().isStrictlyEqualTo(json(unknown));
    MvcTestResult fresh = create(null);
    jdbcTemplate.update(
        "UPDATE invoice_view_link SET created_at=now()-interval '2 days',expires_at=now()-interval '1 day' WHERE id=?",
        UUID.fromString(value(fresh, "$.id")));
    entityManager.clear();
    assertThat(lookup(value(fresh, "$.token")))
        .hasStatus(404)
        .bodyJson()
        .isStrictlyEqualTo(json(unknown));
    assertThat(lookup("not-a-valid-token"))
        .hasStatus(404)
        .bodyJson()
        .isStrictlyEqualTo(json(unknown));
  }

  @Test
  void pending은_납부와_대기금액을_추정하지_않는다() {
    String token = value(create(null), "$.token");
    insertAttempt("pending", "test");
    MvcTestResult found = lookup(token);
    assertThat(found)
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.payment.status")
        .isEqualTo("pending");
    assertThat(found).bodyJson().extractingPath("$.payment.paid_amount").isNull();
    assertThat(found).bodyJson().extractingPath("$.payment.outstanding_amount").isNull();
  }

  @Test
  void 승인과_부분취소와_전액취소를_현재_조회하고_취소는_다시_미수가_되지_않는다() {
    String token = value(create(null), "$.token");
    UUID attemptId = insertAttempt("done", "test");
    assertThat(lookup(token)).bodyJson().extractingPath("$.payment.paid_amount").isEqualTo(1100);
    insertCancellation(attemptId, 400);
    MvcTestResult partial = lookup(token);
    assertThat(partial)
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.payment.status")
        .isEqualTo("partially_canceled");
    assertThat(partial).bodyJson().extractingPath("$.payment.paid_amount").isEqualTo(700);
    assertThat(partial).bodyJson().extractingPath("$.payment.canceled_amount").isEqualTo(400);
    assertThat(partial).bodyJson().extractingPath("$.payment.outstanding_amount").isEqualTo(0);
    insertCancellation(attemptId, 700);
    MvcTestResult canceled = lookup(token);
    assertThat(canceled)
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.payment.status")
        .isEqualTo("canceled");
    assertThat(canceled).bodyJson().extractingPath("$.payment.paid_amount").isEqualTo(0);
    assertThat(canceled).bodyJson().extractingPath("$.payment.outstanding_amount").isEqualTo(0);
    assertThat(canceled).bodyJson().extractingPath("$.total_amount").isEqualTo(1100);
    assertThat(canceled).bodyText().doesNotContain("pg-payment-secret", "pg-transaction-secret");
  }

  @Test
  void 링크_발급_뒤_결제모드를_바꿔도_원래_모드의_상태를_본다() {
    String token = value(create(null), "$.token");
    insertAttempt("done", "test");
    jdbcTemplate.update(
        "INSERT INTO payment_setting (organization_id,mode,updated_at) VALUES (?,'live',now())",
        organizationId);
    MvcTestResult found = lookup(token);
    assertThat(found).hasStatusOk().bodyJson().extractingPath("$.payment.mode").isEqualTo("test");
    assertThat(found).bodyJson().extractingPath("$.payment.status").isEqualTo("paid");
    MvcTestResult live = create(null);
    assertThat(live).hasStatus(201).bodyJson().extractingPath("$.mode").isEqualTo("live");
    assertThat(lookup(value(live, "$.token")))
        .bodyJson()
        .extractingPath("$.payment.status")
        .isEqualTo("unpaid");
  }

  @Test
  void 고객을_보관해도_과거_인보이스는_보이고_도입사_정지는_404다() {
    String token = value(create(null), "$.token");
    jdbcTemplate.update("UPDATE customer SET archived_at=now() WHERE id=?", customerId);
    assertThat(lookup(token)).hasStatusOk();
    for (String status : new String[] {"suspended", "rejected", "pending"}) {
      jdbcTemplate.update("UPDATE organization SET status=? WHERE id=?", status, organizationId);
      entityManager.clear();
      assertError(lookup(token), 404, ErrorCode.INVOICE_VIEW_LINK_NOT_FOUND);
    }
  }

  @Test
  void 조회와_발급과_폐기의_감사에는_토큰이나_해시가_없고_링크_ID로_주체를_기록한다() {
    MvcTestResult created = create(null);
    String token = value(created, "$.token");
    UUID id = UUID.fromString(value(created, "$.id"));
    assertThat(lookup(token)).hasStatusOk();
    assertThat(revoke(authorization, invoiceId, id)).hasStatusOk();
    assertError(lookup(token), 404, ErrorCode.INVOICE_VIEW_LINK_NOT_FOUND);
    Map<String, Object> viewed =
        jdbcTemplate.queryForMap(
            "SELECT * FROM audit_log WHERE organization_id=? AND action='invoice_viewed'",
            organizationId);
    assertThat(viewed)
        .containsEntry("actor_type", "invoice_view_link")
        .containsEntry("actor_id", id);
    String audit =
        jdbcTemplate
            .queryForList(
                "SELECT detail::text FROM audit_log WHERE organization_id=?",
                String.class,
                organizationId)
            .toString();
    assertThat(audit)
        .doesNotContain(token, "token_hash", HexFormat.of().formatHex(SecureTokens.hash(token)));
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE organization_id=? AND action IN ('invoice_view_link_created','invoice_view_link_revoked')",
                Integer.class,
                organizationId))
        .isEqualTo(2);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action='api_request_completed' AND detail->>'route'='/v1/public/invoice-links/lookup' AND detail->>'status'='404'",
                Integer.class))
        .isPositive();
  }

  @Test
  void 없거나_빈_토큰과_너무_긴_토큰도_같은_404이고_입력한_비밀을_되비추지_않는다() {
    String secret = "secret-never-return-".repeat(20);
    MvcTestResult invalid = lookup(secret);
    assertError(invalid, 404, ErrorCode.INVOICE_VIEW_LINK_NOT_FOUND);
    assertThat(invalid).bodyText().doesNotContain(secret);
    for (String body :
        java.util.List.of(
            "{}",
            "{\"token\":null}",
            "{\"token\":\"\"}",
            "{\"token\":\" \"}",
            "{\"token\":\"" + "x".repeat(257) + "\"}")) {
      MvcTestResult result =
          mvc.post()
              .uri("/v1/public/invoice-links/lookup")
              .contentType(MediaType.APPLICATION_JSON)
              .content(body)
              .exchange();
      assertThat(result).hasStatus(404).bodyJson().isStrictlyEqualTo(json(invalid));
    }
    String audit =
        jdbcTemplate
            .queryForList(
                "SELECT detail::text FROM audit_log WHERE action='api_request_completed' AND detail->>'route'='/v1/public/invoice-links/lookup'",
                String.class)
            .toString();
    assertThat(audit).doesNotContain(secret, "token_hash");
  }

  private MvcTestResult create(String body) {
    var request =
        mvc.post()
            .uri("/v1/invoices/{id}/view-links", invoiceId)
            .header("Authorization", authorization);
    if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(body);
    return request.exchange();
  }

  private MvcTestResult list(String credential, UUID id) {
    return mvc.get()
        .uri("/v1/invoices/{id}/view-links", id)
        .header("Authorization", credential)
        .exchange();
  }

  private MvcTestResult revoke(String credential, UUID invoice, UUID id) {
    return mvc.post()
        .uri("/v1/invoices/{id}/view-links/{view_link_id}/revoke", invoice, id)
        .header("Authorization", credential)
        .exchange();
  }

  private MvcTestResult lookup(String token) {
    return mvc.post()
        .uri("/v1/public/invoice-links/lookup")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"token\":\"" + token + "\"}")
        .exchange();
  }

  private UUID insertInvoice(UUID customer, String period) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO invoice (id,organization_id,customer_id,customer_name,period,supply_amount,tax_amount,total_amount,finalized_at) VALUES (?,?,?,'확정 고객',?,1000,100,1100,now())",
        id,
        organizationId,
        customer,
        period);
    return id;
  }

  private UUID insertAttempt(String status, String mode) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO payment_attempt (id,organization_id,customer_id,invoice_id,mode,amount,order_name,status,payment_key,requested_at,completed_at)
        VALUES (?,?,?,?,?,1100,'인보이스',?,?,now(),?)
        """,
        id,
        organizationId,
        customerId,
        invoiceId,
        mode,
        status,
        "done".equals(status) ? "pg-payment-secret" : null,
        "pending".equals(status) ? null : OffsetDateTime.now());
    return id;
  }

  private void insertCancellation(UUID attemptId, long amount) {
    jdbcTemplate.update(
        """
        INSERT INTO payment_cancellation (id,organization_id,payment_attempt_id,mode,amount,reason,status,transaction_key,requested_at,completed_at)
        VALUES (?,?,?,'test',?,'고객 요청','done','pg-transaction-secret',now(),now())
        """,
        UUID.randomUUID(),
        organizationId,
        attemptId,
        amount);
  }

  private static String value(MvcTestResult result, String path) {
    return JsonPath.read(json(result), path);
  }

  private static String json(MvcTestResult result) {
    return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
  }

  private static void assertError(MvcTestResult result, int status, ErrorCode code) {
    assertThat(result)
        .hasStatus(status)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(code.getCode());
  }
}
