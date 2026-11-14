package com.meterengine.liveenvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.jayway.jsonpath.JsonPath;
import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.liveenvironment.service.BusinessRegistrationLookup;
import com.meterengine.liveenvironment.service.BusinessRegistrationStatus;
import com.meterengine.liveenvironment.service.BusinessRegistrationStatus.State;
import com.meterengine.payment.client.TossPaymentsClient;
import com.meterengine.payment.client.TossPaymentsResult;
import jakarta.persistence.EntityManager;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class LiveEnvironmentApplicationIntegrationTest {

  private static final String ORGANIZATION_APPLICATIONS =
      "/v1/organization/live-environment-applications";
  private static final String APPLICATIONS = "/v1/live-environment-applications";
  private static final String ENVIRONMENT_HEADER = "X-MeterEngine-Environment";

  private static final String PASSWORD = "signup-password";
  private static final String NUMBER = "1234567891";
  private static final String CLIENT_KEY = "live_ck_application0000";
  private static final String SECRET_KEY = "live_sk_application00000WXYZ";
  private static final String MERCHANT_ID = "meterengine01";
  private static final String TAX_TYPE = "부가가치세 일반과세자";

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EntityManager entityManager;

  @MockitoBean private TossPaymentsClient tossPaymentsClient;
  @MockitoBean private BusinessRegistrationLookup businessRegistrationLookup;

  private MockMvcTester mvc;
  private String operator;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
    operator = TestCredentials.issueOperatorSession(jdbcTemplate);
    when(tossPaymentsClient.findPaymentByOrderId(anyString(), any()))
        .thenReturn(
            new TossPaymentsResult.Rejected<>(404, "NOT_FOUND_PAYMENT", "존재하지 않는 결제 정보 입니다."));
    ntsAnswers(State.OPERATING, TAX_TYPE);
  }

  // --- 신청 ---

  @Test
  void 신청하면_201_pending이고_실결제_키는_신청에만_암호화해_두며_실제_환경은_잠긴_채다() {
    Applicant applicant = signup("apply@example.com");

    MvcTestResult result = apply(applicant.authorization(), NUMBER);

    assertThat(result).hasStatus(201);
    Map<String, Object> application = JsonPath.read(body(result), "$");
    assertThat(application)
        .containsOnlyKeys(
            "id",
            "organization_id",
            "organization_name",
            "business_registration_number",
            "toss_payments_merchant_id",
            "status",
            "business_status",
            "tax_type",
            "rejection_reason",
            "created_at",
            "reviewed_at",
            "observed_toss_payments_merchant_id",
            "merchant_id_checked_at")
        .containsEntry("organization_id", applicant.organizationId().toString())
        .containsEntry("organization_name", "신청 조직")
        .containsEntry("business_registration_number", NUMBER)
        .containsEntry("toss_payments_merchant_id", MERCHANT_ID)
        .containsEntry("status", "pending")
        .containsEntry("business_status", "operating")
        .containsEntry("tax_type", TAX_TYPE)
        .containsEntry("rejection_reason", null)
        .containsEntry("reviewed_at", null);
    assertThat(body(result)).doesNotContain(SECRET_KEY).doesNotContain(CLIENT_KEY);
    verify(businessRegistrationLookup).find(NUMBER);
    verify(tossPaymentsClient).findPaymentByOrderId(eq(SECRET_KEY), any());
    entityManager.flush();
    Map<String, Object> keys =
        jdbcTemplate.queryForMap(
            "SELECT toss_payments_client_key, toss_payments_secret_key FROM"
                + " live_environment_application WHERE organization_id = ?",
            applicant.organizationId());
    assertThat(keys).containsEntry("toss_payments_client_key", CLIENT_KEY);
    assertThat((String) keys.get("toss_payments_secret_key"))
        .startsWith("v1:")
        .doesNotContain(SECRET_KEY);
    assertThat(countCredentials(applicant.organizationId())).isZero();
    assertThat(isLiveEnvironmentOpen(applicant.organizationId())).isFalse();
    assertThat(customers(applicant.authorization(), "live"))
        .hasStatus(403)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.LIVE_ENVIRONMENT_LOCKED.getCode());
  }

  @Test
  void 신청은_감사_행을_환경_없이_신청한_사용자_행위자로_남기고_키를_남기지_않는다() {
    Applicant applicant = signup("apply-audit@example.com");

    String applicationId = JsonPath.read(body(apply(applicant.authorization(), NUMBER)), "$.id");

    entityManager.flush();
    Map<String, Object> row =
        jdbcTemplate.queryForMap(
            """
            SELECT actor_type, organization_id, target_type, target_id, environment_id,
                   detail::text AS detail
            FROM audit_log WHERE action = 'live_environment_application_created'
              AND organization_id = ?
            """,
            applicant.organizationId());
    assertThat(row)
        .containsEntry("actor_type", "user_account")
        .containsEntry("target_type", "live_environment_application")
        .containsEntry("target_id", applicationId)
        .containsEntry("environment_id", null);
    assertThat((String) row.get("detail"))
        .contains("operating")
        .doesNotContain(SECRET_KEY)
        .doesNotContain(NUMBER);
  }

  @Test
  void 하이픈과_공백이_섞인_사업자등록번호는_숫자만_남겨_받는다() {
    Applicant applicant = signup("hyphen@example.com");

    MvcTestResult result = apply(applicant.authorization(), " 123-45-67891 ");

    assertThat(result).hasStatus(201);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.business_registration_number")
        .isEqualTo(NUMBER);
    verify(businessRegistrationLookup).find(NUMBER);
  }

  @Test
  void 검증_숫자가_틀린_번호는_국세청과_토스를_부르지_않고_400_invalid_business_registration_number다() {
    Applicant applicant = signup("checksum@example.com");

    for (String number : List.of("1234567890", "123456789", "abcdefghij")) {
      MvcTestResult result = apply(applicant.authorization(), number);

      assertThat(result).as(number).hasStatus(400);
      assertThat(result)
          .bodyJson()
          .extractingPath("$.code")
          .isEqualTo(ErrorCode.INVALID_BUSINESS_REGISTRATION_NUMBER.getCode());
      assertThat(result)
          .bodyJson()
          .extractingPath("$.errors[0].field")
          .isEqualTo("business_registration_number");
    }
    verifyNoInteractions(businessRegistrationLookup, tossPaymentsClient);
    assertThat(countApplications(applicant.organizationId())).isZero();
  }

  @Test
  void 국세청에_등록되지_않았거나_폐업한_번호는_이유를_필드_오류로_알리고_거절한다() {
    Applicant applicant = signup("nts-reject@example.com");

    ntsAnswers(State.UNREGISTERED, null);
    MvcTestResult unregistered = apply(applicant.authorization(), NUMBER);
    ntsAnswers(State.CLOSED, TAX_TYPE);
    MvcTestResult closed = apply(applicant.authorization(), NUMBER);

    for (MvcTestResult result : List.of(unregistered, closed)) {
      assertThat(result).hasStatus(400);
      assertThat(result)
          .bodyJson()
          .extractingPath("$.code")
          .isEqualTo(ErrorCode.INVALID_BUSINESS_REGISTRATION_NUMBER.getCode());
      assertThat(result)
          .bodyJson()
          .extractingPath("$.errors[0].field")
          .isEqualTo("business_registration_number");
    }
    assertThat(unregistered)
        .bodyJson()
        .extractingPath("$.errors[0].message")
        .isEqualTo("국세청에 등록되지 않은 사업자등록번호입니다");
    assertThat(closed)
        .bodyJson()
        .extractingPath("$.errors[0].message")
        .isEqualTo("폐업한 사업자의 등록번호로는 신청할 수 없습니다");
    verifyNoInteractions(tossPaymentsClient);
    assertThat(countApplications(applicant.organizationId())).isZero();
    assertThat(countCredentials(applicant.organizationId())).isZero();
  }

  @Test
  void 휴업_사업자는_받고_business_status를_suspended로_남긴다() {
    Applicant applicant = signup("nts-suspended@example.com");
    ntsAnswers(State.SUSPENDED, TAX_TYPE);

    MvcTestResult result = apply(applicant.authorization(), NUMBER);

    assertThat(result).hasStatus(201);
    assertThat(result).bodyJson().extractingPath("$.business_status").isEqualTo("suspended");
    assertThat(result).bodyJson().extractingPath("$.tax_type").isEqualTo(TAX_TYPE);
  }

  @Test
  void 국세청_조회_결과를_받지_못하면_신청은_받고_business_status를_unchecked로_남긴다() {
    Applicant applicant = signup("nts-unavailable@example.com");
    ntsAnswers(State.UNAVAILABLE, null);

    MvcTestResult result = apply(applicant.authorization(), NUMBER);

    assertThat(result).hasStatus(201);
    assertThat(result).bodyJson().extractingPath("$.business_status").isEqualTo("unchecked");
    assertThat(result).bodyJson().extractingPath("$.tax_type").isNull();
  }

  @Test
  void 토스가_시크릿_키를_거절하면_신청서의_필드_이름으로_400_invalid_toss_payments_credential이다() {
    Applicant applicant = signup("toss-reject@example.com");
    when(tossPaymentsClient.findPaymentByOrderId(anyString(), any()))
        .thenReturn(new TossPaymentsResult.Rejected<>(401, "UNAUTHORIZED_KEY", "인증되지 않은 시크릿 키"));

    MvcTestResult result = apply(applicant.authorization(), NUMBER);

    assertThat(result).hasStatus(400);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.INVALID_TOSS_PAYMENTS_CREDENTIAL.getCode());
    assertThat(result)
        .bodyJson()
        .extractingPath("$.errors[0].field")
        .isEqualTo("toss_payments_secret_key");
    assertThat(countApplications(applicant.organizationId())).isZero();
    assertThat(countCredentials(applicant.organizationId())).isZero();
  }

  @Test
  void 테스트_키로_신청하면_토스를_부르지_않고_두_키_필드를_신청서의_이름으로_알린다() {
    Applicant applicant = signup("toss-test-key@example.com");

    MvcTestResult result =
        apply(
            applicant.authorization(),
            NUMBER,
            "test_ck_application0000",
            "test_sk_application00000WXYZ",
            MERCHANT_ID);

    assertThat(result).hasStatus(400);
    assertThat((List<String>) JsonPath.read(body(result), "$.errors[*].field"))
        .containsExactly("toss_payments_client_key", "toss_payments_secret_key");
    assertThat((List<String>) JsonPath.read(body(result), "$.errors[*].message"))
        .containsExactly("실제 환경 키는 live_ck_ 뒤에 값이 이어져야 합니다", "실제 환경 키는 live_sk_ 뒤에 값이 이어져야 합니다");
    verifyNoInteractions(tossPaymentsClient);
  }

  @Test
  void 토스_응답을_확인하지_못하면_503_toss_payments_unavailable이고_신청을_받지_않는다() {
    Applicant applicant = signup("toss-unknown@example.com");
    when(tossPaymentsClient.findPaymentByOrderId(anyString(), any()))
        .thenReturn(new TossPaymentsResult.Unknown<>("HttpTimeoutException: request timed out"));

    assertThat(apply(applicant.authorization(), NUMBER))
        .hasStatus(503)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.TOSS_PAYMENTS_UNAVAILABLE.getCode());
    assertThat(countApplications(applicant.organizationId())).isZero();
  }

  @Test
  void 필수_값이_비거나_상점아이디가_길면_400_validation_error다() {
    Applicant applicant = signup("blank@example.com");

    for (List<String> values :
        List.of(
            List.of("", CLIENT_KEY, SECRET_KEY, MERCHANT_ID),
            List.of(NUMBER, "", SECRET_KEY, MERCHANT_ID),
            List.of(NUMBER, CLIENT_KEY, "", MERCHANT_ID),
            List.of(NUMBER, CLIENT_KEY, SECRET_KEY, ""),
            List.of(NUMBER, CLIENT_KEY, SECRET_KEY, "m".repeat(65)))) {
      assertThat(
              apply(
                  applicant.authorization(),
                  values.get(0),
                  values.get(1),
                  values.get(2),
                  values.get(3)))
          .as(values.toString())
          .hasStatus(400)
          .bodyJson()
          .extractingPath("$.code")
          .isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
    }
    verifyNoInteractions(businessRegistrationLookup, tossPaymentsClient);
  }

  @Test
  void 검토_중인_신청이_있으면_다시_신청할_때_409_live_environment_application_in_progress다() {
    Applicant applicant = signup("twice@example.com");
    assertThat(apply(applicant.authorization(), NUMBER)).hasStatus(201);

    assertThat(apply(applicant.authorization(), NUMBER))
        .hasStatus(409)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.LIVE_ENVIRONMENT_APPLICATION_IN_PROGRESS.getCode());
    assertThat(countApplications(applicant.organizationId())).isOne();
  }

  @Test
  void 실제_환경이_이미_열린_조직이_신청하면_409_live_environment_already_open이다() {
    UUID organizationId = UUID.randomUUID();
    String member = TestCredentials.issueMemberSession(jdbcTemplate, organizationId);

    assertThat(apply(member, NUMBER))
        .hasStatus(409)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.LIVE_ENVIRONMENT_ALREADY_OPEN.getCode());
    verifyNoInteractions(businessRegistrationLookup, tossPaymentsClient);
  }

  @Test
  void 잠긴_조직이_환경_헤더_없이_신청하면_403_live_environment_locked다() {
    Applicant applicant = signup("no-header@example.com");

    assertThat(
            mvc.post()
                .uri(ORGANIZATION_APPLICATIONS)
                .header(AUTHORIZATION, applicant.authorization())
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(NUMBER, CLIENT_KEY, SECRET_KEY, MERCHANT_ID))
                .exchange())
        .hasStatus(403)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.LIVE_ENVIRONMENT_LOCKED.getCode());
  }

  @Test
  void 운영자와_인증_없는_요청은_신청하지_못한다() {
    assertThat(apply(operator, NUMBER)).hasStatus(403);
    assertThat(
            mvc.post()
                .uri(ORGANIZATION_APPLICATIONS)
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody(NUMBER, CLIENT_KEY, SECRET_KEY, MERCHANT_ID))
                .exchange())
        .hasStatus(401);
  }

  // --- 검토 ---

  @Test
  void 운영자가_승인하면_조직의_실제_환경이_열려_실제_환경_요청과_실제_키_발급이_통과한다() {
    Applicant applicant = signup("approve@example.com");
    String applicationId = JsonPath.read(body(apply(applicant.authorization(), NUMBER)), "$.id");

    MvcTestResult result = review(operator, applicationId, "approved", null);

    assertThat(result).hasStatusOk();
    assertThat(result).bodyJson().extractingPath("$.status").isEqualTo("approved");
    assertThat(result).bodyJson().extractingPath("$.reviewed_at").isNotNull();
    assertThat(result).bodyJson().extractingPath("$.rejection_reason").isNull();
    entityManager.flush();
    assertThat(isLiveEnvironmentOpen(applicant.organizationId())).isTrue();
    assertThat(
            jdbcTemplate.queryForMap(
                """
                SELECT c.mode, e.mode AS environment_mode, c.client_key, c.secret_key_last_four
                FROM toss_payments_credential c
                JOIN organization_environment e ON e.id = c.environment_id
                WHERE c.organization_id = ?
                """,
                applicant.organizationId()))
        .containsEntry("mode", "live")
        .containsEntry("environment_mode", "live")
        .containsEntry("client_key", CLIENT_KEY)
        .containsEntry("secret_key_last_four", SECRET_KEY.substring(SECRET_KEY.length() - 4));
    assertThat(applicationKeys(applicationId)).containsOnly((Object) null);
    verify(tossPaymentsClient, times(1)).findPaymentByOrderId(anyString(), any());
    assertThat(customers(applicant.authorization(), "live")).hasStatusOk();
    assertThat(
            mvc.post()
                .uri("/v1/api-keys")
                .header(AUTHORIZATION, applicant.authorization())
                .header(ENVIRONMENT_HEADER, "live")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"label\":\"실제 키\",\"mode\":\"live\",\"scopes\":[\"read\"]}")
                .exchange())
        .hasStatus(201);
    assertThat(
            jdbcTemplate.queryForMap(
                """
                SELECT actor_type, organization_id, target_id, environment_id
                FROM audit_log WHERE action = 'live_environment_application_approved'
                  AND organization_id = ?
                """,
                applicant.organizationId()))
        .containsEntry("actor_type", "user_account")
        .containsEntry("target_id", applicationId)
        .containsEntry("environment_id", null);
  }

  // 예열과 측정처럼 운영 쪽이 쓰는 조직은 신청 없이 DB에서 연다(deploy/README.md "수집 경로 예열").
  @Test
  void DB에서_live_environment_opened_at을_채우면_신청_없이_실제_키_발급이_통과한다() {
    Applicant applicant = signup("opened-by-sql@example.com");
    MvcTestResult locked = issueLiveKey(applicant.authorization());
    assertThat(locked).hasStatus(403);
    assertThat(locked)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.LIVE_ENVIRONMENT_LOCKED.getCode());

    entityManager.flush();
    int updated =
        jdbcTemplate.update(
            "UPDATE organization SET live_environment_opened_at = now()"
                + " WHERE id = ? AND live_environment_opened_at IS NULL",
            applicant.organizationId());
    entityManager.clear();

    assertThat(updated).isEqualTo(1);
    assertThat(issueLiveKey(applicant.authorization())).hasStatus(201);
  }

  @Test
  void 거절은_사유가_있어야_하고_거절된_조직은_사유를_읽고_다시_신청할_수_있다() {
    Applicant applicant = signup("reject@example.com");
    String applicationId = JsonPath.read(body(apply(applicant.authorization(), NUMBER)), "$.id");

    for (String reason : new String[] {null, "", "   "}) {
      MvcTestResult blank = review(operator, applicationId, "rejected", reason);
      assertThat(blank).as(String.valueOf(reason)).hasStatus(400);
      assertThat(blank)
          .bodyJson()
          .extractingPath("$.code")
          .isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
      assertThat(blank)
          .bodyJson()
          .extractingPath("$.errors[0].field")
          .isEqualTo("rejection_reason");
    }
    MvcTestResult rejected = review(operator, applicationId, "rejected", " 상점아이디를 확인할 수 없습니다 ");

    assertThat(rejected).hasStatusOk();
    assertThat(rejected).bodyJson().extractingPath("$.status").isEqualTo("rejected");
    assertThat(rejected)
        .bodyJson()
        .extractingPath("$.rejection_reason")
        .isEqualTo("상점아이디를 확인할 수 없습니다");
    entityManager.flush();
    assertThat(isLiveEnvironmentOpen(applicant.organizationId())).isFalse();
    assertThat(applicationKeys(applicationId)).containsOnly((Object) null);
    assertThat(countCredentials(applicant.organizationId())).isZero();
    MvcTestResult list =
        mvc.get()
            .uri(ORGANIZATION_APPLICATIONS)
            .header(AUTHORIZATION, applicant.authorization())
            .header(ENVIRONMENT_HEADER, "test")
            .exchange();
    assertThat(list).hasStatusOk();
    assertThat(list)
        .bodyJson()
        .extractingPath("$.live_environment_applications[0].rejection_reason")
        .isEqualTo("상점아이디를 확인할 수 없습니다");

    assertThat(apply(applicant.authorization(), NUMBER)).hasStatus(201);
    MvcTestResult both =
        mvc.get()
            .uri(ORGANIZATION_APPLICATIONS)
            .header(AUTHORIZATION, applicant.authorization())
            .header(ENVIRONMENT_HEADER, "test")
            .exchange();
    assertThat(
            (List<String>) JsonPath.read(body(both), "$.live_environment_applications[*].status"))
        .containsExactly("pending", "rejected");
  }

  @Test
  void 승인할_때_사유를_보내면_400_validation_error다() {
    Applicant applicant = signup("approve-reason@example.com");
    String applicationId = JsonPath.read(body(apply(applicant.authorization(), NUMBER)), "$.id");

    assertThat(review(operator, applicationId, "approved", "사유"))
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
    entityManager.flush();
    assertThat(isLiveEnvironmentOpen(applicant.organizationId())).isFalse();
  }

  @Test
  void 이미_검토한_신청을_다시_검토하면_409_live_environment_application_already_reviewed다() {
    Applicant applicant = signup("reviewed@example.com");
    String applicationId = JsonPath.read(body(apply(applicant.authorization(), NUMBER)), "$.id");
    assertThat(review(operator, applicationId, "approved", null)).hasStatusOk();

    for (String status : List.of("approved", "rejected")) {
      assertThat(review(operator, applicationId, status, "rejected".equals(status) ? "사유" : null))
          .as(status)
          .hasStatus(409)
          .bodyJson()
          .extractingPath("$.code")
          .isEqualTo(ErrorCode.LIVE_ENVIRONMENT_APPLICATION_ALREADY_REVIEWED.getCode());
    }
    entityManager.flush();
    assertThat(isLiveEnvironmentOpen(applicant.organizationId())).isTrue();
  }

  @Test
  void 없는_신청이나_모르는_status로_검토하면_404와_400이다() {
    assertThat(review(operator, UUID.randomUUID().toString(), "approved", null))
        .hasStatus(404)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.LIVE_ENVIRONMENT_APPLICATION_NOT_FOUND.getCode());
    assertThat(review(operator, UUID.randomUUID().toString(), "pending", null))
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.VALIDATION_ERROR.getCode());
  }

  @Test
  void 운영자는_모든_조직의_신청을_최근_것부터_보고_status로_좁힌다() {
    Applicant first = signup("list-first@example.com");
    Applicant second = signup("list-second@example.com");
    String firstId = JsonPath.read(body(apply(first.authorization(), NUMBER)), "$.id");
    String secondId = JsonPath.read(body(apply(second.authorization(), "1248100998")), "$.id");
    assertThat(review(operator, firstId, "approved", null)).hasStatusOk();

    MvcTestResult all = mvc.get().uri(APPLICATIONS).header(AUTHORIZATION, operator).exchange();
    MvcTestResult pending =
        mvc.get()
            .uri(APPLICATIONS)
            .param("status", "pending")
            .header(AUTHORIZATION, operator)
            .exchange();

    assertThat(all).hasStatusOk();
    List<String> ids = JsonPath.read(body(all), "$.live_environment_applications[*].id");
    assertThat(ids).contains(firstId, secondId);
    assertThat(ids.indexOf(secondId)).isLessThan(ids.indexOf(firstId));
    assertThat(
            (List<String>)
                JsonPath.read(
                    body(all),
                    "$.live_environment_applications[?(@.id == '%s')].organization_name"
                        .formatted(secondId)))
        .containsExactly("신청 조직");
    List<String> pendingIds = JsonPath.read(body(pending), "$.live_environment_applications[*].id");
    assertThat(pendingIds).contains(secondId).doesNotContain(firstId);
    assertThat(
            mvc.get()
                .uri(APPLICATIONS)
                .param("status", "deleted")
                .header(AUTHORIZATION, operator)
                .exchange())
        .hasStatus(400);
  }

  @Test
  void 조직의_구성원은_운영자의_목록과_검토_API를_부르지_못하고_다른_조직의_신청을_보지_못한다() {
    Applicant applicant = signup("member-only@example.com");
    Applicant other = signup("other-org@example.com");
    String applicationId = JsonPath.read(body(apply(applicant.authorization(), NUMBER)), "$.id");

    assertThat(
            mvc.get()
                .uri(APPLICATIONS)
                .header(AUTHORIZATION, applicant.authorization())
                .header(ENVIRONMENT_HEADER, "test")
                .exchange())
        .hasStatus(403);
    assertThat(review(applicant.authorization(), applicationId, "approved", null)).hasStatus(403);
    MvcTestResult otherList =
        mvc.get()
            .uri(ORGANIZATION_APPLICATIONS)
            .header(AUTHORIZATION, other.authorization())
            .header(ENVIRONMENT_HEADER, "test")
            .exchange();
    assertThat(otherList).hasStatusOk();
    assertThat((List<Object>) JsonPath.read(body(otherList), "$.live_environment_applications"))
        .isEmpty();
    entityManager.flush();
    assertThat(isLiveEnvironmentOpen(applicant.organizationId())).isFalse();
  }

  private record Applicant(UUID organizationId, String authorization) {}

  private Applicant signup(String email) {
    MvcTestResult signup =
        mvc.post()
            .uri("/v1/signups")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"organization_name":"신청 조직","name":"신청자","email":"%s","password":"%s",
                 "consents":[{"document":"terms_of_service","document_version":"2026-10-01"},
                             {"document":"privacy_policy","document_version":"2026-09-15"}]}
                """
                    .formatted(email, PASSWORD))
            .exchange();
    assertThat(signup).hasStatus(201);
    MvcTestResult login =
        mvc.post()
            .uri("/v1/sessions")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD))
            .exchange();
    assertThat(login).hasStatus(201);
    return new Applicant(
        UUID.fromString(JsonPath.read(body(signup), "$.organization.id")),
        TestCredentials.BEARER_PREFIX + JsonPath.read(body(login), "$.token"));
  }

  private MvcTestResult apply(String authorization, String businessRegistrationNumber) {
    return apply(authorization, businessRegistrationNumber, CLIENT_KEY, SECRET_KEY, MERCHANT_ID);
  }

  private MvcTestResult apply(
      String authorization,
      String businessRegistrationNumber,
      String clientKey,
      String secretKey,
      String merchantId) {
    return mvc.post()
        .uri(ORGANIZATION_APPLICATIONS)
        .header(AUTHORIZATION, authorization)
        .header(ENVIRONMENT_HEADER, "test")
        .contentType(MediaType.APPLICATION_JSON)
        .content(requestBody(businessRegistrationNumber, clientKey, secretKey, merchantId))
        .exchange();
  }

  private MvcTestResult review(
      String authorization, String applicationId, String status, String rejectionReason) {
    String reason = rejectionReason == null ? "null" : "\"" + rejectionReason + "\"";
    return mvc.put()
        .uri(APPLICATIONS + "/" + applicationId + "/status")
        .header(AUTHORIZATION, authorization)
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"%s\",\"rejection_reason\":%s}".formatted(status, reason))
        .exchange();
  }

  private MvcTestResult issueLiveKey(String authorization) {
    return mvc.post()
        .uri("/v1/api-keys")
        .header(AUTHORIZATION, authorization)
        .header(ENVIRONMENT_HEADER, "live")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"label\":\"실제 키\",\"mode\":\"live\",\"scopes\":[\"ingest\"]}")
        .exchange();
  }

  private MvcTestResult customers(String authorization, String environment) {
    return mvc.get()
        .uri("/v1/customers")
        .header(AUTHORIZATION, authorization)
        .header(ENVIRONMENT_HEADER, environment)
        .exchange();
  }

  private void ntsAnswers(State state, String taxType) {
    when(businessRegistrationLookup.find(anyString()))
        .thenReturn(new BusinessRegistrationStatus(state, taxType));
  }

  private boolean isLiveEnvironmentOpen(UUID organizationId) {
    return Boolean.TRUE.equals(
        jdbcTemplate.queryForObject(
            "SELECT live_environment_opened_at IS NOT NULL FROM organization WHERE id = ?",
            Boolean.class,
            organizationId));
  }

  private int countApplications(UUID organizationId) {
    entityManager.flush();
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM live_environment_application WHERE organization_id = ?",
        Integer.class,
        organizationId);
  }

  private Collection<Object> applicationKeys(String applicationId) {
    entityManager.flush();
    return jdbcTemplate
        .queryForMap(
            "SELECT toss_payments_client_key, toss_payments_secret_key FROM"
                + " live_environment_application WHERE id = ?",
            UUID.fromString(applicationId))
        .values();
  }

  private int countCredentials(UUID organizationId) {
    entityManager.flush();
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM toss_payments_credential WHERE organization_id = ?",
        Integer.class,
        organizationId);
  }

  private static String requestBody(
      String businessRegistrationNumber, String clientKey, String secretKey, String merchantId) {
    return """
        {"business_registration_number":"%s","toss_payments_client_key":"%s",
         "toss_payments_secret_key":"%s","toss_payments_merchant_id":"%s"}
        """
        .formatted(businessRegistrationNumber, clientKey, secretKey, merchantId);
  }

  private static String body(MvcTestResult result) {
    try {
      return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    } catch (UnsupportedEncodingException e) {
      throw new IllegalStateException("응답 본문을 읽지 못했다", e);
    }
  }
}
