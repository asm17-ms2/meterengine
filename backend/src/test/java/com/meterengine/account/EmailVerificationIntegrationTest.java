package com.meterengine.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.jayway.jsonpath.JsonPath;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.mail.service.EmailMessage;
import com.meterengine.mail.service.EmailSender;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(
    properties = {
      "meterengine.email-verification.enabled=true",
      "meterengine.email-verification.link-base-url=https://console.example.test",
      "meterengine.auth-rate-limit.enabled=true",
      "meterengine.auth-rate-limit.signup-ip-limit=1000",
      "meterengine.auth-rate-limit.signup-account-limit=2",
      "meterengine.auth-rate-limit.login-ip-limit=1000"
    })
class EmailVerificationIntegrationTest {

  private static final String PASSWORD = "verify-password";

  private static final Pattern LINK =
      Pattern.compile(
          "https://console\\.example\\.test/email-verifications/(me_verify_[A-Za-z0-9]{32})");

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private MeterRegistry meterRegistry;
  @Autowired private PasswordEncoder passwordEncoder;

  @TestBean(methodName = "recordingEmailSender")
  private EmailSender emailSender;

  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
    jdbcTemplate.update("DELETE FROM auth_request_rate_limit");
    sender().reset();
    // 앞 시험의 늦은 메일이 이 시험의 hold나 failWith에 걸리지 않게 발송이 빌 때까지 기다린다.
    awaitUntil(() -> sender().inFlight() == 0);
  }

  static EmailSender recordingEmailSender() {
    return new RecordingEmailSender();
  }

  private RecordingEmailSender sender() {
    return (RecordingEmailSender) emailSender;
  }

  @Test
  void 가입하면_인증_메일을_보내고_인증하지_않은_계정의_로그인은_403_email_not_verified다() {
    String email = newEmail();
    MvcTestResult signup = signup(email);

    assertThat(signup).hasStatus(201);
    assertThat((Boolean) JsonPath.read(body(signup), "$.user_account.email_verified")).isFalse();
    EmailMessage message = sender().next(email);
    assertThat(message.text()).contains("(한국 시간)까지 한 번만 쓸 수 있어요");
    assertThat(message.html()).contains("이메일 인증하기");
    assertThat(errorCode(login(email, PASSWORD))).isEqualTo("email_not_verified");
  }

  @Test
  void 비밀번호가_틀리면_인증하지_않은_계정이어도_401_invalid_credentials다() {
    String email = newEmail();
    signup(email);

    MvcTestResult result = login(email, "wrong-password");
    assertThat(result).hasStatus(401);
    assertThat(errorCode(result)).isEqualTo("invalid_credentials");
  }

  @Test
  void 메일의_토큰으로_인증하면_로그인이_인증_단계를_지나고_승인되면_로그인된다() {
    String email = newEmail();
    String organizationId = JsonPath.read(body(signup(email)), "$.organization.id");

    assertThat(confirm(tokenIn(sender().next(email)))).hasStatus(204);

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT email_verified_at IS NOT NULL FROM user_account WHERE email = ?",
                Boolean.class,
                email))
        .isTrue();
    assertThat(errorCode(login(email, PASSWORD))).isEqualTo("organization_not_active");
    jdbcTemplate.update(
        "UPDATE organization SET status = 'active' WHERE id = ?::uuid", organizationId);
    assertThat(login(email, PASSWORD)).hasStatus(201);
  }

  @Test
  void 인증은_user_account_email_verified_감사_행을_남긴다() {
    String email = newEmail();
    String userAccountId = JsonPath.read(body(signup(email)), "$.user_account.id");

    confirm(tokenIn(sender().next(email)));

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM audit_log WHERE action = 'user_account_email_verified' AND target_id = ?",
                Integer.class,
                userAccountId))
        .isEqualTo(1);
  }

  @Test
  void 이미_쓴_토큰을_다시_쓰면_404_email_verification_not_found다() {
    String email = newEmail();
    signup(email);
    String token = tokenIn(sender().next(email));

    assertThat(confirm(token)).hasStatus(204);
    MvcTestResult again = confirm(token);
    assertThat(again).hasStatus(404);
    assertThat(errorCode(again)).isEqualTo("email_verification_not_found");
  }

  @Test
  void 만료된_토큰은_404_email_verification_not_found이고_계정은_인증되지_않는다() {
    String email = newEmail();
    signup(email);
    String token = tokenIn(sender().next(email));
    jdbcTemplate.update(
        """
        UPDATE email_verification
        SET created_at = now() - interval '2 days', expires_at = now() - interval '1 second'
        WHERE user_account_id = (SELECT id FROM user_account WHERE email = ?)
        """,
        email);

    MvcTestResult result = confirm(token);
    assertThat(result).hasStatus(404);
    assertThat(errorCode(result)).isEqualTo("email_verification_not_found");
    assertThat(errorCode(login(email, PASSWORD))).isEqualTo("email_not_verified");
  }

  @Test
  void 형식이_다르거나_없는_토큰은_404_email_verification_not_found다() {
    assertThat(errorCode(confirm("me_verify_" + "A".repeat(32))))
        .isEqualTo("email_verification_not_found");
    assertThat(errorCode(confirm("not-a-token"))).isEqualTo("email_verification_not_found");
  }

  @Test
  void 발송이_실패해도_가입은_201이고_실패를_세며_다시_보내기로_새_메일을_받는다() {
    double failuresBefore = sendCount("failure");
    sender().failWith(new IllegalStateException("ses down"));
    String email = newEmail();

    assertThat(signup(email)).hasStatus(201);
    awaitUntil(() -> sendCount("failure") >= failuresBefore + 1);

    sender().reset();
    assertThat(resend(email)).hasStatus(202);
    assertThat(confirm(tokenIn(sender().next(email)))).hasStatus(204);
  }

  @Test
  void 다시_보내기는_없는_이메일과_인증한_계정에도_202이고_메일을_보내지_않는다() {
    String email = newEmail();
    signup(email);
    confirm(tokenIn(sender().next(email)));
    String unknown = newEmail();

    assertThat(resend(email)).hasStatus(202);
    assertThat(resend(unknown)).hasStatus(202);
    assertThat(issuedCount(email)).isEqualTo(1);
    assertThat(issuedCount(unknown)).isZero();
  }

  @Test
  void 다시_보내기는_같은_이메일에_한도를_넘으면_429다() {
    String email = newEmail();
    signup(email);

    assertThat(resend(email)).hasStatus(202);
    assertThat(resend(email)).hasStatus(202);
    MvcTestResult limited = resend(email);
    assertThat(limited).hasStatus(429);
    assertThat(limited.getResponse().getHeader("Retry-After")).isNotBlank();
    assertThat(issuedCount(email)).isEqualTo(3);
    for (int sent = 0; sent < 3; sent++) sender().next(email);
  }

  @Test
  void 발송이_끝나지_않아도_가입_응답은_기다리지_않고_메일은_뒤이어_나간다() {
    sender().hold();
    String email = newEmail();

    assertThat(signup(email)).hasStatus(201);
    // 요청 스레드에서 보냈다면 응답 전에 발송이 끝났으므로, 응답 뒤에도 발송이 걸려 있어야 한다.
    awaitUntil(() -> sender().inFlight() == 1);

    sender().release();
    assertThat(confirm(tokenIn(sender().next(email)))).hasStatus(204);
  }

  @Test
  void 앞서_있던_계정과_SQL로_넣은_계정은_인증된_것으로_보고_로그인한다() {
    UUID userAccountId = UUID.randomUUID();
    String email = newEmail();
    jdbcTemplate.update(
        """
        INSERT INTO user_account (id, email, password_hash, name, role, created_at)
        VALUES (?, ?, ?, '앞선 운영자', 'operator', now())
        """,
        userAccountId,
        email,
        passwordEncoder.encode(PASSWORD));

    assertThat(login(email, PASSWORD)).hasStatus(201);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM user_account WHERE email_verified_at IS NULL AND email NOT LIKE '%@verify.example.test'",
                Integer.class))
        .isZero();
  }

  private MvcTestResult signup(String email) {
    return post(
        "/v1/signups",
        """
        {"organization_name":"인증 도입사","name":"가입자","email":"%s","password":"%s",
         "consents":[{"document":"terms_of_service","document_version":"2026-10-01"},
                     {"document":"privacy_policy","document_version":"2026-09-15"}]}
        """
            .formatted(email, PASSWORD));
  }

  private MvcTestResult login(String email, String password) {
    return post(
        "/v1/sessions", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password));
  }

  private MvcTestResult confirm(String token) {
    return post("/v1/email-verification-confirmations", "{\"token\":\"%s\"}".formatted(token));
  }

  private MvcTestResult resend(String email) {
    return post("/v1/email-verifications", "{\"email\":\"%s\"}".formatted(email));
  }

  private MvcTestResult post(String path, String json) {
    return mvc.post().uri(path).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
  }

  private int issuedCount(String email) {
    return jdbcTemplate.queryForObject(
        """
        SELECT count(*) FROM email_verification
        WHERE user_account_id = (SELECT id FROM user_account WHERE email = ?)
        """,
        Integer.class,
        email);
  }

  // 발송은 다른 스레드에서 끝나므로 조건이 맞을 때까지 짧게 다시 본다.
  private static void awaitUntil(BooleanSupplier condition) {
    long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
    while (!condition.getAsBoolean()) {
      assertThat(System.nanoTime()).as("condition not met in time").isLessThan(deadline);
      LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
    }
  }

  private static String tokenIn(EmailMessage message) {
    Matcher matcher = LINK.matcher(message.text());
    assertThat(matcher.find()).as("link in %s", message.text()).isTrue();
    assertThat(message.html()).contains(matcher.group());
    return matcher.group(1);
  }

  private double sendCount(String outcome) {
    return meterRegistry.counter("meterengine.email.verification.send", "outcome", outcome).count();
  }

  private static String errorCode(MvcTestResult result) {
    return JsonPath.read(body(result), "$.code");
  }

  private static String body(MvcTestResult result) {
    return new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
  }

  private static String newEmail() {
    return UUID.randomUUID() + "@verify.example.test";
  }
}
