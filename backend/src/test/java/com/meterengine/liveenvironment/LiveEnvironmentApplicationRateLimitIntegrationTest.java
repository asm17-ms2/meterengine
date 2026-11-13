package com.meterengine.liveenvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(
    properties = {
      "meterengine.auth-rate-limit.enabled=true",
      "meterengine.auth-rate-limit.signup-ip-limit=1000",
      "meterengine.auth-rate-limit.login-ip-limit=1000"
    })
@Transactional
class LiveEnvironmentApplicationRateLimitIntegrationTest {

  private static final String ORGANIZATION_APPLICATIONS =
      "/v1/organization/live-environment-applications";
  private static final String PASSWORD = "signup-password";
  private static final String NUMBER = "1234567891";
  private static final String WRONG_CHECK_DIGIT = "1234567890";
  private static final int LIMIT = 10;
  private static final long WINDOW_SECONDS = 30 * 60;

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PlatformTransactionManager transactionManager;

  @MockitoBean private TossPaymentsClient tossPaymentsClient;
  @MockitoBean private BusinessRegistrationLookup businessRegistrationLookup;

  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    // 한도는 시험의 트랜잭션 밖에서 커밋되므로 앞 시험이 남긴 카운터를 따로 커밋해 지운다.
    TransactionTemplate separate = new TransactionTemplate(transactionManager);
    separate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    separate.executeWithoutResult(
        status -> jdbcTemplate.update("DELETE FROM auth_request_rate_limit"));
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
    when(tossPaymentsClient.findPaymentByOrderId(anyString(), any()))
        .thenReturn(
            new TossPaymentsResult.Rejected<>(404, "NOT_FOUND_PAYMENT", "존재하지 않는 결제 정보 입니다."));
  }

  @Test
  void 거절된_신청도_세어_조직마다_30분에_열_번을_넘으면_국세청을_부르지_않고_429다() {
    String applicant = signup("limit-a@example.com");
    String other = signup("limit-b@example.com");
    ntsAnswers(State.UNREGISTERED);

    for (int attempt = 1; attempt <= LIMIT; attempt++) {
      assertThat(apply(applicant, NUMBER)).hasStatus(400);
    }
    MvcTestResult limited = apply(applicant, NUMBER);

    assertThat(limited).hasStatus(429);
    assertThat(limited)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED.getCode());
    assertThat(Long.parseLong(limited.getResponse().getHeader("Retry-After")))
        .isBetween(1L, WINDOW_SECONDS);
    verify(businessRegistrationLookup, times(LIMIT)).find(anyString());
    verifyNoInteractions(tossPaymentsClient);

    // 다른 조직은 따로 센다.
    assertThat(apply(other, NUMBER)).hasStatus(400);
  }

  @Test
  void 검증_숫자가_틀린_번호와_검토_중인_신청이_있는_조직의_요청은_한도에_세지_않는다() {
    String applicant = signup("limit-c@example.com");
    ntsAnswers(State.OPERATING);

    for (int attempt = 1; attempt <= LIMIT + 1; attempt++) {
      assertThat(apply(applicant, WRONG_CHECK_DIGIT)).hasStatus(400);
    }
    assertThat(apply(applicant, NUMBER)).hasStatus(201);
    for (int attempt = 1; attempt <= LIMIT + 1; attempt++) {
      assertThat(apply(applicant, NUMBER)).hasStatus(409);
    }

    verify(businessRegistrationLookup, times(1)).find(anyString());
  }

  private String signup(String email) {
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
    return TestCredentials.BEARER_PREFIX + JsonPath.read(body(login), "$.token");
  }

  private MvcTestResult apply(String authorization, String businessRegistrationNumber) {
    return mvc.post()
        .uri(ORGANIZATION_APPLICATIONS)
        .header(AUTHORIZATION, authorization)
        .header("X-MeterEngine-Environment", "test")
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            """
            {"business_registration_number":"%s","toss_payments_client_key":"live_ck_application0000",
             "toss_payments_secret_key":"live_sk_application00000WXYZ","toss_payments_merchant_id":"meterengine01"}
            """
                .formatted(businessRegistrationNumber))
        .exchange();
  }

  private void ntsAnswers(State state) {
    when(businessRegistrationLookup.find(anyString()))
        .thenReturn(new BusinessRegistrationStatus(state, "부가가치세 일반과세자"));
  }

  private static String body(MvcTestResult result) {
    try {
      return result.getResponse().getContentAsString(StandardCharsets.UTF_8);
    } catch (UnsupportedEncodingException exception) {
      throw new IllegalStateException(exception);
    }
  }
}
