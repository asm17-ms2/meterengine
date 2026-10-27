package com.meterengine.invoiceview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
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
import com.meterengine.payment.client.TossPaymentsClient;
import com.meterengine.payment.client.TossPaymentsPayment;
import com.meterengine.payment.client.TossPaymentsResult;
import com.meterengine.payment.entity.BillingKey;
import com.meterengine.payment.entity.TossPaymentsCredential;
import com.meterengine.payment.repository.BillingKeyRepository;
import com.meterengine.payment.repository.TossPaymentsCredentialRepository;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
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
class InvoiceViewLinkEnvironmentIntegrationTest {
  private static final OffsetDateTime KEY_TIME = OffsetDateTime.parse("2026-10-01T00:00:00Z");
  private static final String SECRET = "test_sk_invoice_link_original";
  @Autowired private WebApplicationContext context;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private InvoiceRepository invoices;
  @Autowired private TossPaymentsCredentialRepository credentials;
  @Autowired private BillingKeyRepository cards;
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
  void 링크관리와_공개조회는_원본환경에_고정되고_다른_환경_ID는_404다() {
    Payable test = payable("test");
    Payable live = payable("live");
    Link testLink = createLink("test", test.invoice());
    Link liveLink = createLink("live", live.invoice());
    assertThat(get("test", "/v1/invoices/" + test.invoice() + "/view-links"))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.invoice_view_links")
        .asArray()
        .hasSize(1);
    assertThat(get("live", "/v1/invoices/" + test.invoice() + "/view-links")).hasStatus(404);
    assertThat(post("test", "/v1/invoices/" + live.invoice() + "/view-links", "{}")).hasStatus(404);
    assertThat(
            post(
                "test",
                "/v1/invoices/" + test.invoice() + "/view-links/" + liveLink.id() + "/revoke",
                null))
        .hasStatus(404);
    assertThat(lookup("live", testLink.token()))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.payment.mode")
        .isEqualTo("test");
    assertThat(lookup("test", liveLink.token()))
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.payment.mode")
        .isEqualTo("live");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT environment_id FROM invoice_view_link WHERE id=?",
                UUID.class,
                testLink.id()))
        .isEqualTo(environment("test"));
    assertThat(
            post(
                "test",
                "/v1/invoices/" + test.invoice() + "/view-links/" + testLink.id() + "/revoke",
                null))
        .hasStatusOk();
    assertThat(lookup("live", testLink.token())).hasStatus(404);
    assertThat(lookup("test", liveLink.token())).hasStatusOk();
    assertThat(BusinessEnvironmentContext.current()).isNull();
  }

  @Test
  void 공개재시도는_선택한_화면환경과_교체된_PG키를_따르지_않고_원본실패만_한번_재시도한다() {
    Payable test = payable("test");
    Payable live = payable("live");
    UUID testFailed = failed(test, "test");
    UUID liveFailed = failed(live, "live");
    Link link = createLink("test", test.invoice());
    MvcTestResult before = lookup("live", link.token());
    assertThat(before)
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.payment_retry.allowed")
        .isEqualTo(true);
    String version = JsonPath.read(body(before), "$.payment_retry.version");
    assertThat(
            mvc.put()
                .uri("/v1/toss-payments-credentials/test")
                .header("Authorization", session)
                .header("X-MeterEngine-Environment", "test")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"client_key\":\"test_ck_invoice_link_rotated\",\"secret_key\":\"test_sk_invoice_link_rotated\"}")
                .exchange())
        .hasStatusOk();
    String request = "{\"token\":\"" + link.token() + "\",\"retry_version\":\"" + version + "\"}";
    MvcTestResult retried = post("live", "/v1/public/invoice-links/payment-retries", request);
    assertThat(retried).hasStatusOk().bodyJson().extractingPath("$.payment.mode").isEqualTo("test");
    assertThat(retried).bodyJson().extractingPath("$.payment.status").isEqualTo("paid");
    UUID attempt =
        jdbcTemplate.queryForObject(
            "SELECT id FROM payment_attempt WHERE invoice_id=? AND id<>?",
            UUID.class,
            test.invoice(),
            testFailed);
    verify(toss)
        .approveBilling(eq(SECRET), eq("billing-test"), any(), eq(attempt.toString()), isNull());
    assertThat(post("live", "/v1/public/invoice-links/payment-retries", request)).hasStatus(409);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM payment_attempt WHERE invoice_id=?",
                Integer.class,
                test.invoice()))
        .isEqualTo(2);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT status FROM payment_attempt WHERE id=?", String.class, liveFailed))
        .isEqualTo("failed");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM payment_attempt WHERE invoice_id=?",
                Integer.class,
                live.invoice()))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT environment_id FROM audit_log WHERE organization_id=? AND"
                    + " action='payment_retry_requested'",
                UUID.class,
                organizationId))
        .isEqualTo(environment("test"));
    assertThat(BusinessEnvironmentContext.current()).isNull();
  }

  private Payable payable(String mode) {
    UUID env = environment(mode);
    UUID customer =
        jdbcTemplate.queryForObject(
            "INSERT INTO customer (organization_id, environment_id, name) VALUES (?, ?, '고객')"
                + " RETURNING id",
            UUID.class,
            organizationId,
            env);
    UUID invoice =
        invoices
            .saveAndFlush(
                new Invoice(
                    UUID.randomUUID(),
                    organizationId,
                    customer,
                    "고객",
                    "2026-08",
                    1000,
                    100,
                    OffsetDateTime.now(),
                    env))
            .getId();
    BusinessEnvironmentContext.run(
        new BusinessEnvironment(organizationId, env, mode),
        () -> {
          credentials.saveAndFlush(
              new TossPaymentsCredential(
                  organizationId,
                  mode,
                  mode + "_ck_invoice_link_original",
                  mode.equals("test") ? SECRET : "live_sk_invoice_link_original",
                  KEY_TIME));
          cards.saveAndFlush(
              new BillingKey(
                      organizationId,
                      customer,
                      mode,
                      "billing-" + mode,
                      "61",
                      "4330****1234",
                      KEY_TIME,
                      "v1",
                      KEY_TIME)
                  .pinCredential(KEY_TIME));
        });
    return new Payable(customer, invoice);
  }

  private UUID failed(Payable payable, String mode) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO payment_attempt (id, organization_id, environment_id, customer_id, invoice_id,"
            + " mode, amount, order_name, status, requested_at, completed_at, failure_code,"
            + " failure_message, credential_updated_at) VALUES (?, ?, ?, ?, ?, ?, 1100, '사용료',"
            + " 'failed', ?, ?, 'REJECT_CARD_PAYMENT', '거절', ?)",
        id,
        organizationId,
        environment(mode),
        payable.customer(),
        payable.invoice(),
        mode,
        OffsetDateTime.now().minusHours(26),
        OffsetDateTime.now().minusHours(25),
        KEY_TIME);
    return id;
  }

  private Link createLink(String mode, UUID invoice) {
    MvcTestResult result =
        post(mode, "/v1/invoices/" + invoice + "/view-links", "{\"allow_payment_retry\":true}");
    assertThat(result).hasStatus(201);
    return new Link(
        UUID.fromString(JsonPath.read(body(result), "$.id")),
        JsonPath.read(body(result), "$.token"));
  }

  private MvcTestResult lookup(String mode, String token) {
    return post(mode, "/v1/public/invoice-links/lookup", "{\"token\":\"" + token + "\"}");
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

  private UUID environment(String mode) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM organization_environment WHERE organization_id=? AND mode=?",
        UUID.class,
        organizationId,
        mode);
  }

  private String body(MvcTestResult result) {
    return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
  }

  private record Payable(UUID customer, UUID invoice) {}

  private record Link(UUID id, String token) {}
}
