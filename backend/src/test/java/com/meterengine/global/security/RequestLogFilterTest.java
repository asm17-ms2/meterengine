package com.meterengine.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
@ExtendWith(OutputCaptureExtension.class)
class RequestLogFilterTest {

  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private WebApplicationContext webApplicationContext;

  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
  }

  @Test
  void API_키_요청은_주체와_결과를_한_줄로_남기고_Authorization_값과_쿼리_문자열은_남기지_않는다(CapturedOutput output) {
    UUID organizationId = UUID.randomUUID();
    String authorization = TestCredentials.issueApiKey(jdbcTemplate, organizationId, "read");
    UUID apiKeyId = apiKeyIdOf(authorization);

    assertThat(
            mvc.get()
                .uri("/v1/customers?probe=query-secret-value")
                .header(AUTHORIZATION, authorization)
                .exchange())
        .hasStatusOk();

    assertThat(requestLogLines(output))
        .singleElement()
        .satisfies(
            line ->
                assertThat(line)
                    .contains(
                        "method=GET",
                        "path=/v1/customers ",
                        "status=200",
                        "principal_kind=api_key",
                        "organization_id=" + organizationId,
                        "api_key_id=" + apiKeyId,
                        "client_address=127.0.0.1")
                    .containsPattern("duration_ms=\\d+")
                    .doesNotContain("user_account_id"));
    assertThat(output.getOut())
        .doesNotContain(TestCredentials.rawKeyOf(authorization))
        .doesNotContain("query-secret-value")
        .doesNotContain("probe=");
  }

  @Test
  void 세션_요청은_사용자_계정을_주체로_남기고_토큰은_남기지_않는다(CapturedOutput output) {
    UUID organizationId = UUID.randomUUID();
    String authorization = TestCredentials.issueMemberSession(jdbcTemplate, organizationId);
    UUID userAccountId =
        jdbcTemplate.queryForObject(
            "SELECT user_account_id FROM user_session WHERE token_hash = sha256(convert_to(?, 'UTF8'))",
            UUID.class,
            TestCredentials.rawKeyOf(authorization));

    assertThat(mvc.get().uri("/v1/customers").header(AUTHORIZATION, authorization).exchange())
        .hasStatusOk();

    assertThat(requestLogLines(output))
        .singleElement()
        .satisfies(
            line ->
                assertThat(line)
                    .contains(
                        "status=200",
                        "principal_kind=session",
                        "organization_id=" + organizationId,
                        "user_account_id=" + userAccountId)
                    .doesNotContain("api_key_id"));
    assertThat(output.getOut()).doesNotContain(TestCredentials.rawKeyOf(authorization));
  }

  @Test
  void 클라이언트_주소는_X_Forwarded_For의_첫_값이다(CapturedOutput output) {
    String authorization = TestCredentials.issueApiKey(jdbcTemplate, UUID.randomUUID());

    mvc.get()
        .uri("/v1/customers")
        .header(AUTHORIZATION, authorization)
        .header("X-Forwarded-For", "203.0.113.7, 10.0.0.1")
        .exchange();

    assertThat(requestLogLines(output))
        .singleElement()
        .satisfies(
            line ->
                assertThat(line).contains("client_address=203.0.113.7").doesNotContain("10.0.0.1"));
  }

  @Test
  void 방화벽이_거절하는_X_Forwarded_For는_주소를_비워_한_줄로_남긴다(CapturedOutput output) {
    String authorization = TestCredentials.issueApiKey(jdbcTemplate, UUID.randomUUID());

    assertThat(
            mvc.get()
                .uri("/v1/customers")
                .header(AUTHORIZATION, authorization)
                .header("X-Forwarded-For", "203.0.113.7\u0085")
                .exchange())
        .hasStatusOk();

    assertThat(requestLogLines(output))
        .singleElement()
        .satisfies(
            line ->
                assertThat(line)
                    .contains("status=200", "client_address=-")
                    .doesNotContain("203.0.113.7"));
  }

  @Test
  void 인증_필터가_거절한_요청도_한_줄로_남고_자격_증명은_남기지_않는다(CapturedOutput output) {
    String rejectedKey = "me_live_" + "A".repeat(32);

    assertThat(
            mvc.get()
                .uri("/v1/customers")
                .header(AUTHORIZATION, TestCredentials.BEARER_PREFIX + rejectedKey)
                .exchange())
        .hasStatus(401);

    assertThat(requestLogLines(output))
        .singleElement()
        .satisfies(
            line ->
                assertThat(line)
                    .contains("status=401", "principal_kind=anonymous")
                    .doesNotContain("organization_id", "api_key_id"));
    assertThat(output.getOut()).doesNotContain(rejectedKey);
  }

  @Test
  void 레이트리밋에_걸린_요청도_429와_키_주체를_한_줄로_남긴다(CapturedOutput output) {
    UUID organizationId = UUID.randomUUID();
    String authorization = TestCredentials.issueApiKey(jdbcTemplate, organizationId);
    UUID apiKeyId = apiKeyIdOf(authorization);
    jdbcTemplate.update("UPDATE api_key SET rate_limit_per_minute = 1 WHERE id = ?", apiKeyId);

    assertThat(mvc.get().uri("/v1/customers").header(AUTHORIZATION, authorization).exchange())
        .hasStatusOk();
    assertThat(mvc.get().uri("/v1/customers").header(AUTHORIZATION, authorization).exchange())
        .hasStatus(429);

    assertThat(requestLogLines(output))
        .hasSize(2)
        .last()
        .satisfies(
            line ->
                assertThat(line)
                    .contains(
                        "status=429",
                        "principal_kind=api_key",
                        "organization_id=" + organizationId,
                        "api_key_id=" + apiKeyId));
  }

  @Test
  void 카드_등록_경로의_토큰_세그먼트는_가려서_남긴다(CapturedOutput output) {
    String authorization = TestCredentials.issueApiKey(jdbcTemplate, UUID.randomUUID());

    mvc.get()
        .uri("/v1/payment-method-registrations/abc123/x")
        .header(AUTHORIZATION, authorization)
        .exchange();

    assertThat(requestLogLines(output))
        .singleElement()
        .satisfies(
            line ->
                assertThat(line)
                    .contains("path=/v1/payment-method-registrations/{token} ")
                    .doesNotContain("abc123"));
  }

  @Test
  void 하이픈을_퍼센트_인코딩한_카드_등록_경로도_토큰_세그먼트를_가린다(CapturedOutput output) {
    assertTokenSegmentMasked("/v1/payment%2Dmethod-registrations/tok3nSecretValue", output);
  }

  @Test
  void 첫_글자를_퍼센트_인코딩한_카드_등록_경로도_토큰_세그먼트를_가린다(CapturedOutput output) {
    assertTokenSegmentMasked("/v1/%70ayment-method-registrations/tok3nSecretValue", output);
  }

  @Test
  void 도입사_초대_경로의_토큰_세그먼트는_가려서_남긴다(CapturedOutput output) {
    String secret = "InvitationSecret0123456789abcdef";
    String token = "me_invite_" + secret;

    mvc.get().uri("/v1/organization-invitations/" + token).exchange();
    mvc.post().uri("/v1/organization-invitations/" + token + "/accept").exchange();
    mvc.get().uri(URI.create("/v1/%6Frganization-invitations/" + token)).exchange();

    assertThat(requestLogLines(output))
        .hasSize(3)
        .allSatisfy(
            line -> assertThat(line).contains("path=/v1/organization-invitations/{token} "));
    assertThat(output.getOut()).doesNotContain(secret);
  }

  @Test
  void 체인_안에서_방화벽이_거절한_요청은_400으로_한_줄만_남긴다(CapturedOutput output) {
    String rejectedValue = "Bearer me_live_" + "R".repeat(32) + "\u0007";

    assertThat(mvc.get().uri("/v1/customers").header(AUTHORIZATION, rejectedValue).exchange())
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("validation_error");

    assertThat(requestLogLines(output))
        .singleElement()
        .satisfies(
            line ->
                assertThat(line)
                    .contains("path=/v1/customers ", "status=400", "principal_kind=anonymous"));
    assertThat(output.getOut()).doesNotContain("R".repeat(32));
  }

  @Test
  void 체인_앞에서_방화벽이_거절한_요청도_가린_경로와_익명과_400을_한_줄로_남긴다(CapturedOutput output) {
    assertThat(
            mvc.get()
                .uri(URI.create("/v1/payment-method-registrations/tok3nSecretValue;x=1"))
                .exchange())
        .hasStatus(400)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("validation_error");

    assertThat(requestLogLines(output))
        .singleElement()
        .satisfies(
            line ->
                assertThat(line)
                    .contains(
                        "method=GET",
                        "path=/v1/payment-method-registrations/{token} ",
                        "status=400",
                        "principal_kind=anonymous",
                        "client_address=127.0.0.1")
                    .containsPattern("duration_ms=\\d+"));
    assertThat(output.getOut()).doesNotContain("tok3nSecretValue");
  }

  @Test
  void 방화벽이_거절하는_경로_파라미터와_점_세그먼트와_이중_인코딩도_등록_경로_뒤를_가린다(CapturedOutput output) {
    List<String> paths =
        List.of(
            "/v1/payment-method-registrations;x=1/s3cretTokenValue",
            "/v1/payment-method-registrations%3Bx/s3cretTokenValue",
            "/v1/payment-method-registrations/./s3cretTokenValue",
            "/v1/%2570ayment-method-registrations/s3cretTokenValue");

    for (String path : paths) {
      assertThat(mvc.get().uri(URI.create(path)).exchange()).hasStatus(400);
    }

    assertThat(requestLogLines(output))
        .hasSize(paths.size())
        .allSatisfy(
            line ->
                assertThat(line)
                    .contains(
                        "path=/v1/payment-method-registrations/{token} ",
                        "status=400",
                        "principal_kind=anonymous"));
    assertThat(output.getOut()).doesNotContain("s3cretTokenValue");
  }

  private void assertTokenSegmentMasked(String path, CapturedOutput output) {
    String authorization = TestCredentials.issueApiKey(jdbcTemplate, UUID.randomUUID());

    mvc.get().uri(URI.create(path)).header(AUTHORIZATION, authorization).exchange();

    assertThat(requestLogLines(output))
        .singleElement()
        .satisfies(
            line ->
                assertThat(line)
                    .contains("path=/v1/payment-method-registrations/{token} ")
                    .doesNotContain("tok3nSecretValue"));
    assertThat(output.getOut()).doesNotContain("tok3nSecretValue");
  }

  private List<String> requestLogLines(CapturedOutput output) {
    return output.getOut().lines().filter(line -> line.contains("duration_ms=")).toList();
  }

  private UUID apiKeyIdOf(String authorization) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM api_key WHERE key_hash = sha256(convert_to(?, 'UTF8'))",
        UUID.class,
        TestCredentials.rawKeyOf(authorization));
  }
}
