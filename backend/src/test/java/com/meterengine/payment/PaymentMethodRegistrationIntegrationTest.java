package com.meterengine.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.jayway.jsonpath.JsonPath;
import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.payment.client.TossPaymentsBilling;
import com.meterengine.payment.client.TossPaymentsClient;
import com.meterengine.payment.client.TossPaymentsResult;
import com.meterengine.payment.entity.TossPaymentsCredential;
import com.meterengine.payment.repository.TossPaymentsCredentialRepository;
import jakarta.persistence.EntityManager;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
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
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class PaymentMethodRegistrationIntegrationTest {

  private static final String ORGANIZATION_NAME = "결제 도입사";
  private static final String CUSTOMER_NAME = "결제 고객";
  private static final String TEST_CLIENT_KEY = "test_ck_D5GePWvyJnrK0W0k6q8gLzN97Eoq";
  private static final String TEST_SECRET_KEY = "test_sk_zXLkKEypNArWmo50nX3lmeaxYG5R";
  private static final String AUTH_KEY = "bln_authKeyFromTossSdk0001";
  private static final String BILLING_KEY = "billingKeyIssuedByToss0001";
  private static final String NEW_BILLING_KEY = "billingKeyIssuedByToss0002";
  private static final String CONSENT_DOCUMENT_VERSION = "2026-10-01";
  private static final OffsetDateTime AUTHENTICATED_AT =
      OffsetDateTime.parse("2026-10-26T01:02:03Z");

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private TossPaymentsCredentialRepository tossPaymentsCredentialRepository;
  @Autowired private EntityManager entityManager;

  @MockitoBean private TossPaymentsClient tossPaymentsClient;

  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
    when(tossPaymentsClient.deleteBillingKey(anyString(), anyString()))
        .thenReturn(new TossPaymentsResult.Success<>(null));
  }

  @Test
  void 링크를_만들면_token과_24시간_뒤_만료가_오고_DB에는_token_원문_없이_해시만_남는다() {
    UUID organizationId = insertOrganization();
    UUID customerId = insertCustomer(organizationId);
    insertCredential(organizationId, "test");

    MvcTestResult created = createRegistration(organizationId, customerId);

    assertThat(created).hasStatus(201);
    String token = tokenOf(created);
    assertThat(token).matches("[A-Za-z0-9_-]{43}");
    Map<String, Object> row = registrationRow(customerId);
    assertThat(row)
        .containsEntry("organization_id", organizationId)
        .containsEntry("token_hash", sha256(token))
        .containsEntry("mode", "test")
        .containsEntry("used_at", null);
    assertThat(row.values()).doesNotContain(token);
    assertThat(
            Duration.between(
                ((Timestamp) row.get("created_at")).toInstant(),
                ((Timestamp) row.get("expires_at")).toInstant()))
        .isEqualTo(Duration.ofHours(24));
    assertThat(created)
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {
              "token": "%s", "customer_id": "%s", "customer_name": "%s", "organization_name": "%s",
              "client_key": "%s", "customer_key": "%s", "mode": "test", "expires_at": "%s"
            }
            """
                .formatted(
                    token,
                    customerId,
                    CUSTOMER_NAME,
                    ORGANIZATION_NAME,
                    TEST_CLIENT_KEY,
                    customerId,
                    kst(row.get("expires_at"))));
  }

  @Test
  void 고객이_없거나_다른_도입사의_고객이면_링크를_만들지_않고_404다() {
    UUID organizationId = insertOrganization();
    UUID otherOrganizationId = insertOrganization();
    UUID otherCustomerId = insertCustomer(otherOrganizationId);
    insertCredential(organizationId, "test");

    assertError(
        createRegistration(organizationId, UUID.randomUUID()), 404, ErrorCode.CUSTOMER_NOT_FOUND);
    assertError(
        createRegistration(organizationId, otherCustomerId), 404, ErrorCode.CUSTOMER_NOT_FOUND);

    assertThat(countRegistrations(otherCustomerId)).isZero();
  }

  @Test
  void 현재_모드의_키가_없으면_링크를_만들지_않고_409다() {
    UUID organizationId = insertOrganization();
    UUID customerId = insertCustomer(organizationId);

    assertError(
        createRegistration(organizationId, customerId),
        409,
        ErrorCode.TOSS_PAYMENTS_CREDENTIAL_MISSING);

    insertCredential(organizationId, "test");
    jdbcTemplate.update(
        "INSERT INTO payment_setting (organization_id, mode, updated_at) VALUES (?, 'live', now())",
        organizationId);
    assertError(
        createRegistration(organizationId, customerId),
        409,
        ErrorCode.TOSS_PAYMENTS_CREDENTIAL_MISSING);
    assertThat(countRegistrations(customerId)).isZero();
  }

  @Test
  void 공개_조회는_X_Organization_Id_없이_200이고_token은_싣지_않는다() {
    UUID organizationId = insertOrganization();
    UUID customerId = insertCustomer(organizationId);
    insertCredential(organizationId, "test");
    String token = createToken(organizationId, customerId);

    MvcTestResult found = getRegistration(token);

    assertThat(found)
        .hasStatus(200)
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {
              "customer_id": "%s", "customer_name": "%s", "organization_name": "%s",
              "client_key": "%s", "customer_key": "%s", "mode": "test", "expires_at": "%s"
            }
            """
                .formatted(
                    customerId,
                    CUSTOMER_NAME,
                    ORGANIZATION_NAME,
                    TEST_CLIENT_KEY,
                    customerId,
                    kst(registrationRow(customerId).get("expires_at"))));
  }

  @Test
  void 만료된_링크와_없는_링크는_조회도_발급도_404이고_토스를_부르지_않는다() {
    UUID organizationId = insertOrganization();
    UUID customerId = insertCustomer(organizationId);
    insertCredential(organizationId, "test");
    String token = createToken(organizationId, customerId);
    jdbcTemplate.update(
        "UPDATE payment_method_registration SET expires_at = ? WHERE customer_id = ?",
        OffsetDateTime.now().minusSeconds(1),
        customerId);

    assertError(getRegistration(token), 404, ErrorCode.PAYMENT_METHOD_REGISTRATION_NOT_FOUND);
    assertError(
        getRegistration("unknownTokenThatWasNeverIssued0000000000000"),
        404,
        ErrorCode.PAYMENT_METHOD_REGISTRATION_NOT_FOUND);
    assertError(
        createBillingKey(token, AUTH_KEY, customerId.toString(), CONSENT_DOCUMENT_VERSION),
        404,
        ErrorCode.PAYMENT_METHOD_REGISTRATION_NOT_FOUND);

    verifyNoInteractions(tossPaymentsClient);
  }

  @Test
  void 정지된_도입사의_링크는_조회도_발급도_404이고_토스를_부르지_않으며_재개하면_다시_조회된다() {
    UUID organizationId = insertOrganization();
    UUID customerId = insertCustomer(organizationId);
    insertCredential(organizationId, "test");
    String token = createToken(organizationId, customerId);
    tossIssues(issued(BILLING_KEY, "61", "43301234****123*"));

    assertThat(updateOrganizationStatus(organizationId, "suspended")).hasStatus(200);

    assertError(getRegistration(token), 404, ErrorCode.PAYMENT_METHOD_REGISTRATION_NOT_FOUND);
    assertError(
        createBillingKey(token, AUTH_KEY, customerId.toString(), CONSENT_DOCUMENT_VERSION),
        404,
        ErrorCode.PAYMENT_METHOD_REGISTRATION_NOT_FOUND);
    verifyNoInteractions(tossPaymentsClient);
    assertThat(countBillingKeys(customerId)).isZero();

    assertThat(updateOrganizationStatus(organizationId, "active")).hasStatus(200);
    assertThat(getRegistration(token)).hasStatus(200);
  }

  @Test
  void 빌링키를_발급하면_201이고_암호문과_동의_기록이_남고_같은_링크는_다시_쓸_수_없다() {
    UUID organizationId = insertOrganization();
    UUID customerId = insertCustomer(organizationId);
    insertCredential(organizationId, "test");
    String token = createToken(organizationId, customerId);
    tossIssues(issued(BILLING_KEY, "61", "43301234****123*"));
    Instant requestedAfter = Instant.now().truncatedTo(ChronoUnit.MICROS);

    MvcTestResult issued =
        createBillingKey(token, AUTH_KEY, customerId.toString(), CONSENT_DOCUMENT_VERSION);

    Instant respondedBefore = Instant.now();
    assertThat(issued).hasStatus(201);
    Map<String, Object> row = billingKeyRow(customerId);
    assertThat((String) row.get("billing_key")).startsWith("v1:").doesNotContain(BILLING_KEY);
    assertThat(row)
        .containsEntry("organization_id", organizationId)
        .containsEntry("mode", "test")
        .containsEntry("card_issuer_code", "61")
        .containsEntry("card_number", "43301234****123*")
        .containsEntry("consent_document_version", CONSENT_DOCUMENT_VERSION);
    assertThat(((Timestamp) row.get("authenticated_at")).toInstant())
        .isEqualTo(AUTHENTICATED_AT.toInstant());
    assertThat(((Timestamp) row.get("consented_at")).toInstant())
        .isBetween(requestedAfter, respondedBefore);
    assertThat(registrationRow(customerId).get("used_at")).isNotNull();
    assertThat(issued)
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {
              "customer_id": "%s", "card_issuer_code": "61", "card_number": "43301234****123*",
              "mode": "test", "authenticated_at": "2026-10-26T10:02:03+09:00"
            }
            """
                .formatted(customerId));
    assertThat(issued).bodyText().doesNotContain(BILLING_KEY).doesNotContain(AUTH_KEY);

    assertError(
        createBillingKey(token, AUTH_KEY, customerId.toString(), CONSENT_DOCUMENT_VERSION),
        404,
        ErrorCode.PAYMENT_METHOD_REGISTRATION_NOT_FOUND);
    assertError(getRegistration(token), 404, ErrorCode.PAYMENT_METHOD_REGISTRATION_NOT_FOUND);
    verify(tossPaymentsClient, times(1))
        .issueBillingKey(TEST_SECRET_KEY, AUTH_KEY, customerId.toString());
    verify(tossPaymentsClient, never()).deleteBillingKey(any(), any());
  }

  @Test
  void 동의_문서_버전이_없으면_400_validation_error이고_토스를_부르지_않는다() {
    UUID organizationId = insertOrganization();
    UUID customerId = insertCustomer(organizationId);
    insertCredential(organizationId, "test");
    String token = createToken(organizationId, customerId);

    MvcTestResult result =
        mvc.post()
            .uri("/v1/payment-method-registrations/{token}/billing-keys", token)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"auth_key": "%s", "customer_key": "%s"}
                """
                    .formatted(AUTH_KEY, customerId))
            .exchange();

    assertError(result, 400, ErrorCode.VALIDATION_ERROR);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.errors[*].field")
        .asArray()
        .containsExactly("consent_document_version");
    verifyNoInteractions(tossPaymentsClient);
    assertThat(registrationRow(customerId).get("used_at")).isNull();
  }

  @Test
  void customer_key가_링크의_고객과_다르면_400이고_토스를_부르지_않는다() {
    UUID organizationId = insertOrganization();
    UUID customerId = insertCustomer(organizationId);
    insertCredential(organizationId, "test");
    String token = createToken(organizationId, customerId);

    MvcTestResult result =
        createBillingKey(token, AUTH_KEY, UUID.randomUUID().toString(), CONSENT_DOCUMENT_VERSION);

    assertError(result, 400, ErrorCode.VALIDATION_ERROR);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.errors[*].field")
        .asArray()
        .containsExactly("customer_key");
    verifyNoInteractions(tossPaymentsClient);
    assertThat(registrationRow(customerId).get("used_at")).isNull();
  }

  @Test
  void 토스가_거절하면_400_payment_method_rejected이고_토스_문구를_싣지_않으며_같은_링크로_다시_할_수_있다() {
    UUID organizationId = insertOrganization();
    UUID customerId = insertCustomer(organizationId);
    insertCredential(organizationId, "test");
    String token = createToken(organizationId, customerId);
    tossIssues(new TossPaymentsResult.Rejected<>(400, "INVALID_CARD_NUMBER", "카드번호를 다시 확인해주세요."));

    MvcTestResult rejected =
        createBillingKey(token, AUTH_KEY, customerId.toString(), CONSENT_DOCUMENT_VERSION);

    assertThat(rejected)
        .hasStatus(400)
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {"code": "payment_method_rejected", "message": "%s"}
            """
                .formatted(ErrorCode.PAYMENT_METHOD_REJECTED.getMessage()));
    assertThat(rejected)
        .bodyText()
        .doesNotContain("INVALID_CARD_NUMBER")
        .doesNotContain("카드번호를 다시 확인해주세요.");
    assertThat(registrationRow(customerId).get("used_at")).isNull();
    assertThat(countBillingKeys(customerId)).isZero();

    tossIssues(issued(BILLING_KEY, "61", "43301234****123*"));
    assertThat(createBillingKey(token, AUTH_KEY, customerId.toString(), CONSENT_DOCUMENT_VERSION))
        .hasStatus(201);
    assertThat(countBillingKeys(customerId)).isEqualTo(1);
  }

  @Test
  void 토스_결과를_알_수_없으면_503이고_링크는_쓰이지_않은_채_남는다() {
    UUID organizationId = insertOrganization();
    UUID customerId = insertCustomer(organizationId);
    insertCredential(organizationId, "test");
    String token = createToken(organizationId, customerId);
    tossIssues(new TossPaymentsResult.Unknown<>("HttpTimeoutException: request timed out"));

    assertThat(createBillingKey(token, AUTH_KEY, customerId.toString(), CONSENT_DOCUMENT_VERSION))
        .hasStatus(503)
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {"code": "toss_payments_unavailable", "message": "%s"}
            """
                .formatted(ErrorCode.TOSS_PAYMENTS_UNAVAILABLE.getMessage()));

    assertThat(registrationRow(customerId).get("used_at")).isNull();
    assertThat(countBillingKeys(customerId)).isZero();
    assertThat(getRegistration(token)).hasStatus(200);
  }

  @Test
  void 다시_등록하면_행은_하나이고_카드가_바뀌며_옛_빌링키를_지운다() {
    UUID organizationId = insertOrganization();
    UUID customerId = insertCustomer(organizationId);
    insertCredential(organizationId, "test");
    tossIssues(issued(BILLING_KEY, "61", "43301234****123*"));
    assertThat(
            createBillingKey(
                createToken(organizationId, customerId),
                AUTH_KEY,
                customerId.toString(),
                CONSENT_DOCUMENT_VERSION))
        .hasStatus(201);
    String firstCipher = (String) billingKeyRow(customerId).get("billing_key");

    tossIssues(issued(NEW_BILLING_KEY, "41", "53651234****456*"));
    MvcTestResult reissued =
        createBillingKey(
            createToken(organizationId, customerId), AUTH_KEY, customerId.toString(), "2026-11-01");

    assertThat(reissued).hasStatus(201);
    assertThat(countBillingKeys(customerId)).isEqualTo(1);
    Map<String, Object> row = billingKeyRow(customerId);
    assertThat(row)
        .containsEntry("card_issuer_code", "41")
        .containsEntry("card_number", "53651234****456*")
        .containsEntry("consent_document_version", "2026-11-01");
    assertThat((String) row.get("billing_key")).startsWith("v1:").isNotEqualTo(firstCipher);
    verify(tossPaymentsClient).deleteBillingKey(TEST_SECRET_KEY, BILLING_KEY);
    verify(tossPaymentsClient, never()).deleteBillingKey(any(), eq(NEW_BILLING_KEY));
  }

  @Test
  void 카드를_등록하거나_바꾸면_도입사_감사_행을_시스템_행위자로_남기고_교체면_이전_카드를_싣는다() {
    UUID organizationId = insertOrganization();
    UUID customerId = insertCustomer(organizationId);
    insertCredential(organizationId, "test");
    String firstToken = createToken(organizationId, customerId);
    tossIssues(issued(BILLING_KEY, "61", "43301234****123*"));
    assertThat(
            createBillingKey(firstToken, AUTH_KEY, customerId.toString(), CONSENT_DOCUMENT_VERSION))
        .hasStatus(201);
    String secondToken = createToken(organizationId, customerId);
    tossIssues(issued(NEW_BILLING_KEY, "41", "53651234****456*"));
    assertThat(createBillingKey(secondToken, AUTH_KEY, customerId.toString(), "2026-11-01"))
        .hasStatus(201);
    entityManager.flush();

    List<Map<String, Object>> rows =
        jdbcTemplate.queryForList(
            """
            SELECT actor_type, actor_id, target_type, target_id, detail::text AS detail
            FROM audit_log WHERE organization_id = ? AND action = 'payment_method_created'
            ORDER BY occurred_at
            """,
            organizationId);

    assertThat(rows)
        .hasSize(2)
        .allSatisfy(
            row ->
                assertThat(row)
                    .containsEntry("actor_type", "system")
                    .containsEntry("actor_id", null)
                    .containsEntry("target_type", "payment_method")
                    .containsEntry("target_id", customerId.toString()));
    assertThat(detailOf(rows.get(0)))
        .isEqualTo(
            Map.of(
                "mode", "test",
                "payment_method_registration_id", registrationId(firstToken),
                "card_number", "43301234****123*"));
    assertThat(detailOf(rows.get(1)))
        .isEqualTo(
            Map.of(
                "mode", "test",
                "payment_method_registration_id", registrationId(secondToken),
                "card_number", "53651234****456*",
                "replaced_card_number", "43301234****123*"));
  }

  @Test
  void 발급하는_사이에_링크가_쓰이면_404이고_막_발급한_빌링키를_지우며_기존_카드는_그대로다() {
    UUID organizationId = insertOrganization();
    UUID customerId = insertCustomer(organizationId);
    insertCredential(organizationId, "test");
    tossIssues(issued(BILLING_KEY, "61", "43301234****123*"));
    assertThat(
            createBillingKey(
                createToken(organizationId, customerId),
                AUTH_KEY,
                customerId.toString(),
                CONSENT_DOCUMENT_VERSION))
        .hasStatus(201);
    String token = createToken(organizationId, customerId);
    String tokenHash = sha256(token);
    when(tossPaymentsClient.issueBillingKey(anyString(), anyString(), anyString()))
        .thenAnswer(
            invocation -> {
              jdbcTemplate.update(
                  "UPDATE payment_method_registration SET used_at = ? WHERE token_hash = ?",
                  OffsetDateTime.now(),
                  tokenHash);
              return issued(NEW_BILLING_KEY, "41", "53651234****456*");
            });

    assertError(
        createBillingKey(token, AUTH_KEY, customerId.toString(), "2026-11-01"),
        404,
        ErrorCode.PAYMENT_METHOD_REGISTRATION_NOT_FOUND);

    verify(tossPaymentsClient).deleteBillingKey(TEST_SECRET_KEY, NEW_BILLING_KEY);
    verify(tossPaymentsClient, never()).deleteBillingKey(any(), eq(BILLING_KEY));
    assertThat(countBillingKeys(customerId)).isEqualTo(1);
    assertThat(billingKeyRow(customerId))
        .containsEntry("card_issuer_code", "61")
        .containsEntry("card_number", "43301234****123*")
        .containsEntry("consent_document_version", CONSENT_DOCUMENT_VERSION);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void 토스_발급과_옛_빌링키_삭제는_트랜잭션_밖에서_부른다() {
    UUID organizationId = insertOrganization();
    UUID customerId = insertCustomer(organizationId);
    insertCredential(organizationId, "test");
    List<Boolean> transactionActiveDuringCalls = new ArrayList<>();
    AtomicInteger issueCount = new AtomicInteger();
    when(tossPaymentsClient.issueBillingKey(anyString(), anyString(), anyString()))
        .thenAnswer(
            invocation -> {
              transactionActiveDuringCalls.add(
                  TransactionSynchronizationManager.isActualTransactionActive());
              return issueCount.incrementAndGet() == 1
                  ? issued(BILLING_KEY, "61", "43301234****123*")
                  : issued(NEW_BILLING_KEY, "41", "53651234****456*");
            });
    when(tossPaymentsClient.deleteBillingKey(anyString(), anyString()))
        .thenAnswer(
            invocation -> {
              transactionActiveDuringCalls.add(
                  TransactionSynchronizationManager.isActualTransactionActive());
              return new TossPaymentsResult.Success<>(null);
            });
    try {
      assertThat(
              createBillingKey(
                  createToken(organizationId, customerId),
                  AUTH_KEY,
                  customerId.toString(),
                  CONSENT_DOCUMENT_VERSION))
          .hasStatus(201);
      assertThat(
              createBillingKey(
                  createToken(organizationId, customerId),
                  AUTH_KEY,
                  customerId.toString(),
                  CONSENT_DOCUMENT_VERSION))
          .hasStatus(201);

      assertThat(transactionActiveDuringCalls).containsExactly(false, false, false);
      verify(tossPaymentsClient).deleteBillingKey(TEST_SECRET_KEY, BILLING_KEY);
      assertThat(countBillingKeys(customerId)).isEqualTo(1);
      assertThat(billingKeyRow(customerId)).containsEntry("card_issuer_code", "41");
    } finally {
      jdbcTemplate.update("DELETE FROM billing_key WHERE organization_id = ?", organizationId);
      jdbcTemplate.update(
          "DELETE FROM payment_method_registration WHERE organization_id = ?", organizationId);
      jdbcTemplate.update(
          "DELETE FROM toss_payments_credential WHERE organization_id = ?", organizationId);
      jdbcTemplate.update("DELETE FROM customer WHERE organization_id = ?", organizationId);
      jdbcTemplate.update("DELETE FROM user_session WHERE organization_id = ?", organizationId);
      jdbcTemplate.update(
          "DELETE FROM organization_member WHERE organization_id = ?", organizationId);
      jdbcTemplate.update("DELETE FROM api_key WHERE organization_id = ?", organizationId);
    }
  }

  private void tossIssues(TossPaymentsResult<TossPaymentsBilling> result) {
    when(tossPaymentsClient.issueBillingKey(anyString(), anyString(), anyString()))
        .thenReturn(result);
  }

  private static TossPaymentsResult<TossPaymentsBilling> issued(
      String billingKey, String issuerCode, String number) {
    return new TossPaymentsResult.Success<>(
        new TossPaymentsBilling(
            billingKey, AUTHENTICATED_AT, new TossPaymentsBilling.Card(issuerCode, number)));
  }

  private MvcTestResult createRegistration(UUID organizationId, UUID customerId) {
    return withOrganization(
            mvc.post().uri("/v1/customers/{id}/payment-method-registrations", customerId),
            organizationId)
        .exchange();
  }

  private String createToken(UUID organizationId, UUID customerId) {
    MvcTestResult created = createRegistration(organizationId, customerId);
    assertThat(created).hasStatus(201);
    return tokenOf(created);
  }

  private static String tokenOf(MvcTestResult created) {
    return JsonPath.read(
        new String(created.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8),
        "$.token");
  }

  private MvcTestResult getRegistration(String token) {
    return mvc.get().uri("/v1/payment-method-registrations/{token}", token).exchange();
  }

  private MvcTestResult createBillingKey(
      String token, String authKey, String customerKey, String consentDocumentVersion) {
    return mvc.post()
        .uri("/v1/payment-method-registrations/{token}/billing-keys", token)
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            """
            {"auth_key": "%s", "customer_key": "%s", "consent_document_version": "%s"}
            """
                .formatted(authKey, customerKey, consentDocumentVersion))
        .exchange();
  }

  private MvcTestResult updateOrganizationStatus(UUID organizationId, String status) {
    return mvc.put()
        .uri("/v1/organizations/{id}/status", organizationId)
        .header("Authorization", TestCredentials.issueOperatorSession(jdbcTemplate))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\": \"%s\"}".formatted(status))
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

  private void insertCredential(UUID organizationId, String mode) {
    tossPaymentsCredentialRepository.saveAndFlush(
        new TossPaymentsCredential(
            organizationId,
            mode,
            TEST_CLIENT_KEY,
            TEST_SECRET_KEY,
            OffsetDateTime.parse("2026-10-01T00:00:00Z")));
  }

  private Map<String, Object> registrationRow(UUID customerId) {
    return jdbcTemplate.queryForMap(
        "SELECT * FROM payment_method_registration WHERE customer_id = ?", customerId);
  }

  private String registrationId(String token) {
    return jdbcTemplate
        .queryForObject(
            "SELECT id FROM payment_method_registration WHERE token_hash = ?",
            UUID.class,
            sha256(token))
        .toString();
  }

  private static Map<String, Object> detailOf(Map<String, Object> auditRow) {
    return JsonPath.read((String) auditRow.get("detail"), "$");
  }

  private int countRegistrations(UUID customerId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM payment_method_registration WHERE customer_id = ?",
        Integer.class,
        customerId);
  }

  private Map<String, Object> billingKeyRow(UUID customerId) {
    return jdbcTemplate.queryForMap("SELECT * FROM billing_key WHERE customer_id = ?", customerId);
  }

  private int countBillingKeys(UUID customerId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM billing_key WHERE customer_id = ?", Integer.class, customerId);
  }

  private static String sha256(String token) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException(exception);
    }
  }

  private static String kst(Object timestamp) {
    return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
        ((Timestamp) timestamp).toInstant().atOffset(ZoneOffset.ofHours(9)));
  }

  private UUID insertOrganization() {
    return jdbcTemplate.queryForObject(
        "INSERT INTO organization (name) VALUES (?) RETURNING id", UUID.class, ORGANIZATION_NAME);
  }

  private UUID insertCustomer(UUID organizationId) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO customer (organization_id, name) VALUES (?, ?) RETURNING id",
        UUID.class,
        organizationId,
        CUSTOMER_NAME);
  }
}
