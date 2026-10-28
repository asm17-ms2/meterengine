package com.meterengine.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.payment.client.TossPaymentsClient;
import com.meterengine.payment.client.TossPaymentsPayment;
import com.meterengine.payment.client.TossPaymentsResult;
import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
class TossPaymentsCredentialIntegrationTest {

  private static final String TEST_CLIENT_KEY = "test_ck_D5GePWvyJnrK0W0k6q8gLzN97Eoq";
  private static final String TEST_SECRET_KEY = "test_sk_zXLkKEypNArWmo50nX3lmeaxYG5R";

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;

  @MockitoBean private TossPaymentsClient tossPaymentsClient;

  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
  }

  @Test
  void test_키를_저장하면_목록에_마지막_네_자와_확인_시각이_나오고_DB에는_암호문만_남는다() {
    UUID organizationId = insertOrganization();
    tossAnswers(new TossPaymentsResult.Rejected<>(404, "NOT_FOUND_PAYMENT", "존재하지 않는 결제 정보 입니다."));

    MvcTestResult saved =
        updateCredential(organizationId, "test", TEST_CLIENT_KEY, TEST_SECRET_KEY);

    assertThat(saved).hasStatus(200);
    ArgumentCaptor<String> orderId = ArgumentCaptor.forClass(String.class);
    verify(tossPaymentsClient).findPaymentByOrderId(eq(TEST_SECRET_KEY), orderId.capture());
    assertThat(orderId.getValue()).startsWith("credential-check-").matches("[A-Za-z0-9_-]{6,64}");
    Map<String, Object> row = credentialRow(organizationId, "test");
    assertThat((String) row.get("secret_key")).startsWith("v1:").doesNotContain(TEST_SECRET_KEY);
    assertThat(row).containsEntry("secret_key_last_four", "YG5R");
    String expected =
        """
        {
          "mode": "test", "client_key": "%s", "secret_key_last_four": "YG5R",
          "verified_at": "%s", "updated_at": "%s"
        }
        """
            .formatted(TEST_CLIENT_KEY, kst(row.get("verified_at")), kst(row.get("updated_at")));
    assertThat(saved).bodyJson().isStrictlyEqualTo(expected);
    MvcTestResult listed = listCredentials(organizationId);
    assertThat(listed)
        .hasStatus(200)
        .bodyJson()
        .isStrictlyEqualTo("{\"toss_payments_credentials\": [" + expected + "]}");
    assertThat(listed).bodyText().doesNotContain(TEST_SECRET_KEY);
    assertThat(saved).bodyText().doesNotContain(TEST_SECRET_KEY);
  }

  @Test
  void 모드와_맞지_않는_접두사의_키는_400이고_토스를_부르지_않는다() {
    UUID organizationId = insertOrganization();
    List<List<String>> rejectedKeys =
        List.of(
            List.of(TEST_CLIENT_KEY, "live_sk_zXLkKEypNArWmo50nX3lmeaxYG5R"),
            List.of(TEST_CLIENT_KEY, "test_gsk_zXLkKEypNArWmo50nX3lmeaxYG5R"),
            List.of(TEST_CLIENT_KEY, "test_sk_"),
            List.of("live_ck_D5GePWvyJnrK0W0k6q8gLzN97Eoq", TEST_SECRET_KEY),
            List.of("test_gck_D5GePWvyJnrK0W0k6q8gLzN97Eoq", TEST_SECRET_KEY),
            List.of("ab", "cd"));

    for (List<String> keys : rejectedKeys) {
      assertError(
          updateCredential(organizationId, "test", keys.get(0), keys.get(1)),
          400,
          ErrorCode.INVALID_TOSS_PAYMENTS_CREDENTIAL);
    }
    assertError(
        updateCredential(organizationId, "live", TEST_CLIENT_KEY, TEST_SECRET_KEY),
        400,
        ErrorCode.INVALID_TOSS_PAYMENTS_CREDENTIAL);

    verifyNoInteractions(tossPaymentsClient);
    assertThat(countCredentials(organizationId)).isZero();
  }

  @Test
  void test_모드에_live_시크릿_키를_보내면_어느_필드인지_errors에_싣고_키를_되비추지_않는다() {
    UUID organizationId = insertOrganization();
    String liveSecretKey = "live_sk_zXLkKEypNArWmo50nX3lmeaxYG5R";

    MvcTestResult result = updateCredential(organizationId, "test", TEST_CLIENT_KEY, liveSecretKey);

    assertThat(result)
        .hasStatus(400)
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {
              "code": "invalid_toss_payments_credential", "message": "%s",
              "errors": [{"field": "secret_key", "message": "test 모드의 키는 test_sk_ 뒤에 값이 이어져야 합니다"}]
            }
            """
                .formatted(ErrorCode.INVALID_TOSS_PAYMENTS_CREDENTIAL.getMessage()));
    assertThat(result).bodyText().doesNotContain(liveSecretKey).doesNotContain(TEST_CLIENT_KEY);
    verifyNoInteractions(tossPaymentsClient);
  }

  @Test
  void 토스가_401로_거절하면_secret_key를_errors에_싣고_토스_문구와_키를_되비추지_않는다() {
    UUID organizationId = insertOrganization();
    tossAnswers(new TossPaymentsResult.Rejected<>(401, "UNAUTHORIZED_KEY", "인증되지 않은 시크릿 키"));

    MvcTestResult result =
        updateCredential(organizationId, "test", TEST_CLIENT_KEY, TEST_SECRET_KEY);

    assertThat(result)
        .hasStatus(400)
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {
              "code": "invalid_toss_payments_credential", "message": "%s",
              "errors": [{"field": "secret_key", "message": "토스페이먼츠가 받지 않는 시크릿 키입니다"}]
            }
            """
                .formatted(ErrorCode.INVALID_TOSS_PAYMENTS_CREDENTIAL.getMessage()));
    assertThat(result)
        .bodyText()
        .doesNotContain(TEST_SECRET_KEY)
        .doesNotContain("UNAUTHORIZED_KEY")
        .doesNotContain("인증되지 않은 시크릿 키");
    assertThat(countCredentials(organizationId)).isZero();
  }

  @Test
  void 확인용_주문에_결제가_있다고_토스가_답해도_유효한_키로_저장한다() {
    UUID organizationId = insertOrganization();
    tossAnswers(
        new TossPaymentsResult.Success<>(
            new TossPaymentsPayment(
                "tgen_20261026000000abcd", "credential-check-order", "DONE", 1000, 1000, null)));

    assertThat(updateCredential(organizationId, "test", TEST_CLIENT_KEY, TEST_SECRET_KEY))
        .hasStatus(200);

    assertThat(countCredentials(organizationId)).isEqualTo(1);
  }

  @Test
  void 토스가_키를_거절하면_400이고_행을_남기지_않는다() {
    UUID organizationId = insertOrganization();

    tossAnswers(new TossPaymentsResult.Rejected<>(401, "UNAUTHORIZED_KEY", "인증되지 않은 시크릿 키"));
    assertError(
        updateCredential(organizationId, "test", TEST_CLIENT_KEY, TEST_SECRET_KEY),
        400,
        ErrorCode.INVALID_TOSS_PAYMENTS_CREDENTIAL);
    tossAnswers(new TossPaymentsResult.Rejected<>(400, "INVALID_REQUEST", "잘못된 요청입니다."));
    assertError(
        updateCredential(organizationId, "test", TEST_CLIENT_KEY, TEST_SECRET_KEY),
        400,
        ErrorCode.INVALID_TOSS_PAYMENTS_CREDENTIAL);

    assertThat(countCredentials(organizationId)).isZero();
  }

  @Test
  void 토스_결과를_알_수_없거나_다른_거절이면_503이고_토스_문구를_싣지_않는다() {
    UUID organizationId = insertOrganization();

    tossAnswers(new TossPaymentsResult.Unknown<>("HttpTimeoutException: request timed out"));
    MvcTestResult unknown =
        updateCredential(organizationId, "test", TEST_CLIENT_KEY, TEST_SECRET_KEY);
    tossAnswers(new TossPaymentsResult.Rejected<>(403, "FORBIDDEN_REQUEST", "허용되지 않은 요청입니다."));
    MvcTestResult forbidden =
        updateCredential(organizationId, "test", TEST_CLIENT_KEY, TEST_SECRET_KEY);

    for (MvcTestResult result : List.of(unknown, forbidden)) {
      assertThat(result)
          .hasStatus(503)
          .bodyJson()
          .isStrictlyEqualTo(
              """
              {"code": "toss_payments_unavailable", "message": "%s"}
              """
                  .formatted(ErrorCode.TOSS_PAYMENTS_UNAVAILABLE.getMessage()));
    }
    assertThat(countCredentials(organizationId)).isZero();
  }

  @Test
  void 같은_모드를_다시_저장하면_행_하나가_바뀐다() {
    UUID organizationId = insertOrganization();
    tossAnswers(new TossPaymentsResult.Rejected<>(404, "NOT_FOUND_PAYMENT", "존재하지 않는 결제 정보 입니다."));
    assertThat(updateCredential(organizationId, "test", TEST_CLIENT_KEY, TEST_SECRET_KEY))
        .hasStatus(200);
    String firstCipher = (String) credentialRow(organizationId, "test").get("secret_key");

    MvcTestResult updated =
        updateCredential(
            organizationId, "test", "test_ck_other0000", "test_sk_another000000000ABCD");

    assertThat(updated).hasStatus(200);
    assertThat(countCredentials(organizationId)).isEqualTo(1);
    Map<String, Object> row = credentialRow(organizationId, "test");
    assertThat(row)
        .containsEntry("client_key", "test_ck_other0000")
        .containsEntry("secret_key_last_four", "ABCD");
    assertThat((String) row.get("secret_key")).startsWith("v1:").isNotEqualTo(firstCipher);
    assertThat(updated).bodyJson().extractingPath("$.secret_key_last_four").isEqualTo("ABCD");
  }

  @Test
  void 같은_키를_다시_저장하면_확인_시각만_바뀌고_updated_at은_그대로다() {
    UUID organizationId = insertOrganization();
    tossAnswers(new TossPaymentsResult.Rejected<>(404, "NOT_FOUND_PAYMENT", "존재하지 않는 결제 정보 입니다."));
    updateCredential(organizationId, "test", TEST_CLIENT_KEY, TEST_SECRET_KEY);
    Map<String, Object> first = credentialRow(organizationId, "test");

    assertThat(updateCredential(organizationId, "test", TEST_CLIENT_KEY, TEST_SECRET_KEY))
        .hasStatus(200);

    Map<String, Object> second = credentialRow(organizationId, "test");
    assertThat(second.get("updated_at")).isEqualTo(first.get("updated_at"));
    assertThat(second.get("verified_at")).isNotEqualTo(first.get("verified_at"));
  }

  @Test
  void 시크릿_키나_클라이언트_키를_바꿔_저장하면_updated_at이_바뀐다() {
    UUID organizationId = insertOrganization();
    tossAnswers(new TossPaymentsResult.Rejected<>(404, "NOT_FOUND_PAYMENT", "존재하지 않는 결제 정보 입니다."));
    updateCredential(organizationId, "test", TEST_CLIENT_KEY, TEST_SECRET_KEY);
    Object firstUpdatedAt = credentialRow(organizationId, "test").get("updated_at");

    updateCredential(organizationId, "test", TEST_CLIENT_KEY, "test_sk_another000000000ABCD");
    Object secretChangedAt = credentialRow(organizationId, "test").get("updated_at");
    updateCredential(organizationId, "test", "test_ck_other0000", "test_sk_another000000000ABCD");
    Object clientChangedAt = credentialRow(organizationId, "test").get("updated_at");

    assertThat(secretChangedAt).isNotEqualTo(firstUpdatedAt);
    assertThat(clientChangedAt).isNotEqualTo(secretChangedAt);
  }

  @Test
  void 두_모드를_저장하면_목록은_mode_순이고_다른_도입사의_키는_나오지_않는다() {
    UUID organizationId = insertOrganization();
    UUID otherOrganizationId = insertOrganization();
    tossAnswers(new TossPaymentsResult.Rejected<>(404, "NOT_FOUND_PAYMENT", "존재하지 않는 결제 정보 입니다."));
    updateCredential(organizationId, "test", TEST_CLIENT_KEY, TEST_SECRET_KEY);
    updateCredential(organizationId, "live", "live_ck_live0000", "live_sk_live00000000LIVE");
    updateCredential(otherOrganizationId, "test", "test_ck_other0000", "test_sk_other0000OTHR");

    assertThat(listCredentials(organizationId))
        .hasStatus(200)
        .bodyJson()
        .extractingPath("$.toss_payments_credentials[*].mode")
        .asArray()
        .containsExactly("live", "test");
    assertThat(listCredentials(UUID.randomUUID()))
        .bodyJson()
        .isStrictlyEqualTo("{\"toss_payments_credentials\": []}");
  }

  @Test
  void admin_API_키로는_키와_같은_모드의_키도_저장할_수_없고_403이며_토스를_부르지_않고_저장된_키는_그대로다() {
    UUID organizationId = insertOrganization();
    tossAnswers(new TossPaymentsResult.Rejected<>(404, "NOT_FOUND_PAYMENT", "존재하지 않는 결제 정보 입니다."));
    assertThat(updateCredential(organizationId, "test", TEST_CLIENT_KEY, TEST_SECRET_KEY))
        .hasStatus(200);
    assertThat(
            updateCredential(
                organizationId, "live", "live_ck_live0000", "live_sk_live00000000LIVE"))
        .hasStatus(200);
    Map<String, Map<String, Object>> saved =
        Map.of(
            "test", credentialRow(organizationId, "test"),
            "live", credentialRow(organizationId, "live"));

    for (String keyMode : List.of("test", "live")) {
      assertError(
          updateCredential(
              apiKey(organizationId, keyMode),
              keyMode,
              keyMode + "_ck_other0000",
              keyMode + "_sk_other0000OTHR"),
          403,
          ErrorCode.PERMISSION_DENIED);
      assertThat(credentialRow(organizationId, keyMode)).isEqualTo(saved.get(keyMode));
    }

    verify(tossPaymentsClient).findPaymentByOrderId(eq(TEST_SECRET_KEY), any());
    verify(tossPaymentsClient).findPaymentByOrderId(eq("live_sk_live00000000LIVE"), any());
    verifyNoMoreInteractions(tossPaymentsClient);
  }

  @Test
  void 모드_경로가_test나_live가_아니면_400_validation_error이고_토스를_부르지_않는다() {
    UUID organizationId = insertOrganization();

    assertError(
        updateCredential(organizationId, "prod", TEST_CLIENT_KEY, TEST_SECRET_KEY),
        400,
        ErrorCode.VALIDATION_ERROR);

    verifyNoInteractions(tossPaymentsClient);
  }

  @Test
  void 키가_비어_있으면_400_validation_error다() {
    UUID organizationId = insertOrganization();

    MvcTestResult result =
        withOrganization(mvc.put().uri("/v1/toss-payments-credentials/test"), organizationId)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"client_key\": \"\"}")
            .exchange();

    assertError(result, 400, ErrorCode.VALIDATION_ERROR);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.errors[*].field")
        .asArray()
        .containsExactlyInAnyOrder("client_key", "secret_key");
    verifyNoInteractions(tossPaymentsClient);
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void 토스는_트랜잭션_밖에서_부르고_커밋된_키를_다시_저장하면_행_하나가_바뀐다() {
    UUID organizationId = insertOrganization();
    List<Boolean> transactionActiveDuringCalls = new ArrayList<>();
    when(tossPaymentsClient.findPaymentByOrderId(anyString(), any()))
        .thenAnswer(
            invocation -> {
              transactionActiveDuringCalls.add(
                  TransactionSynchronizationManager.isActualTransactionActive());
              return new TossPaymentsResult.Rejected<>(
                  404, "NOT_FOUND_PAYMENT", "존재하지 않는 결제 정보 입니다.");
            });
    try {
      assertThat(updateCredential(organizationId, "test", TEST_CLIENT_KEY, TEST_SECRET_KEY))
          .hasStatus(200);
      MvcTestResult updated =
          updateCredential(
              organizationId, "test", "test_ck_other0000", "test_sk_another000000000ABCD");

      assertThat(updated).hasStatus(200);
      assertThat(transactionActiveDuringCalls).containsExactly(false, false);
      assertThat(countCredentials(organizationId)).isEqualTo(1);
      assertThat(credentialRow(organizationId, "test"))
          .containsEntry("client_key", "test_ck_other0000")
          .containsEntry("secret_key_last_four", "ABCD");
    } finally {
      jdbcTemplate.update(
          "DELETE FROM toss_payments_credential WHERE organization_id = ?", organizationId);
      jdbcTemplate.update("DELETE FROM user_session WHERE organization_id = ?", organizationId);
      jdbcTemplate.update(
          "DELETE FROM organization_member WHERE organization_id = ?", organizationId);
      jdbcTemplate.update("DELETE FROM api_key WHERE organization_id = ?", organizationId);
    }
  }

  private void tossAnswers(TossPaymentsResult<TossPaymentsPayment> result) {
    when(tossPaymentsClient.findPaymentByOrderId(anyString(), any())).thenReturn(result);
  }

  private MvcTestResult updateCredential(
      UUID organizationId, String mode, String clientKey, String secretKey) {
    return updateCredential(
        TestCredentials.issueMemberSession(jdbcTemplate, organizationId),
        mode,
        clientKey,
        secretKey);
  }

  private MvcTestResult updateCredential(
      String authorization, String mode, String clientKey, String secretKey) {
    return mvc.put()
        .uri("/v1/toss-payments-credentials/" + mode)
        .header("Authorization", authorization)
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            """
            {"client_key": "%s", "secret_key": "%s"}
            """
                .formatted(clientKey, secretKey))
        .exchange();
  }

  private String apiKey(UUID organizationId, String mode) {
    return TestCredentials.issueApiKey(jdbcTemplate, organizationId, mode);
  }

  private MvcTestResult listCredentials(UUID organizationId) {
    return withOrganization(mvc.get().uri("/v1/toss-payments-credentials"), organizationId)
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

  private Map<String, Object> credentialRow(UUID organizationId, String mode) {
    return jdbcTemplate.queryForMap(
        "SELECT * FROM toss_payments_credential WHERE organization_id = ? AND mode = ?",
        organizationId,
        mode);
  }

  private int countCredentials(UUID organizationId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM toss_payments_credential WHERE organization_id = ?",
        Integer.class,
        organizationId);
  }

  private static String kst(Object timestamp) {
    return DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(
        ((Timestamp) timestamp).toInstant().atOffset(ZoneOffset.ofHours(9)));
  }

  private UUID insertOrganization() {
    return jdbcTemplate.queryForObject(
        "INSERT INTO organization (name) VALUES ('도입사') RETURNING id", UUID.class);
  }
}
