package com.meterengine.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.jayway.jsonpath.JsonPath;
import com.meterengine.TestcontainersConfiguration;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.context.WebApplicationContext;

// 가짜 Siteverify는 받은 토큰으로 답을 고른다: pass는 통과, fail은 실패, slow는 시간 제한보다 늦게 답한다.
// 요청 수 제한은 꺼서 다른 시험 클래스와 같은 IP 한도를 나눠 쓰지 않는다. 순서는 컨트롤러 단위 시험이 본다.
@Import(TestcontainersConfiguration.class)
@SpringBootTest(
    properties = {
      "meterengine.auth-rate-limit.enabled=false",
      "meterengine.turnstile.enabled=true",
      "meterengine.turnstile.secret-key=1x0000000000000000000000000000000AA",
      "meterengine.turnstile.allowed-hostnames=meterengine.com"
    })
class CaptchaIntegrationTest {

  private static final AtomicInteger SITEVERIFY_CALLS = new AtomicInteger();

  private static final HttpServer SITEVERIFY = startSiteverify();

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;

  private MockMvcTester mvc;

  @DynamicPropertySource
  static void siteverifyUrl(DynamicPropertyRegistry registry) {
    registry.add(
        "meterengine.turnstile.siteverify-url",
        () -> "http://127.0.0.1:" + SITEVERIFY.getAddress().getPort() + "/siteverify");
  }

  @AfterAll
  static void stopSiteverify() {
    SITEVERIFY.stop(0);
  }

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
    SITEVERIFY_CALLS.set(0);
  }

  @Test
  void 캡차를_켜면_turnstile_token_없는_가입은_400_captcha_failed이고_계정을_만들지_않는다() {
    String email = newEmail();
    MvcTestResult result = signup(email, null);

    assertThat(result).hasStatus(400);
    assertThat(errorCode(result)).isEqualTo("captcha_failed");
    assertThat(SITEVERIFY_CALLS).hasValue(0);
    assertThat(accountCount(email)).isZero();
  }

  @Test
  void 검증에_실패한_토큰의_가입은_400_captcha_failed다() {
    String email = newEmail();
    MvcTestResult result = signup(email, "fail");

    assertThat(result).hasStatus(400);
    assertThat(errorCode(result)).isEqualTo("captcha_failed");
    assertThat(accountCount(email)).isZero();
  }

  @Test
  void Siteverify가_답하지_않으면_가입은_503_captcha_unavailable이다() {
    String email = newEmail();
    MvcTestResult result = signup(email, "slow");

    assertThat(result).hasStatus(503);
    assertThat(errorCode(result)).isEqualTo("captcha_unavailable");
    assertThat(accountCount(email)).isZero();
  }

  @Test
  void 통과한_토큰의_가입은_201이다() {
    String email = newEmail();
    assertThat(signup(email, "pass")).hasStatus(201);
    assertThat(accountCount(email)).isEqualTo(1);
  }

  @Test
  void 인증_메일_다시_보내기에도_같은_캡차_검사를_한다() {
    assertThat(errorCode(resend(newEmail(), null))).isEqualTo("captcha_failed");
    assertThat(errorCode(resend(newEmail(), "fail"))).isEqualTo("captcha_failed");
    assertThat(errorCode(resend(newEmail(), "slow"))).isEqualTo("captcha_unavailable");
    assertThat(resend(newEmail(), "pass")).hasStatus(202);
  }

  private MvcTestResult signup(String email, String turnstileToken) {
    return post(
        "/v1/signups",
        """
        {"organization_name":"캡차 도입사","name":"가입자","email":"%s","password":"captcha-password",
         "consents":[{"document":"terms_of_service","document_version":"2026-10-01"},
                     {"document":"privacy_policy","document_version":"2026-09-15"}]%s}
        """
            .formatted(email, tokenField(turnstileToken)));
  }

  private MvcTestResult resend(String email, String turnstileToken) {
    return post(
        "/v1/email-verifications",
        "{\"email\":\"%s\"%s}".formatted(email, tokenField(turnstileToken)));
  }

  private static String tokenField(String turnstileToken) {
    return turnstileToken == null ? "" : ",\"turnstile_token\":\"" + turnstileToken + "\"";
  }

  private MvcTestResult post(String path, String json) {
    return mvc.post().uri(path).contentType(MediaType.APPLICATION_JSON).content(json).exchange();
  }

  private int accountCount(String email) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM user_account WHERE email = ?", Integer.class, email);
  }

  private static String errorCode(MvcTestResult result) {
    return JsonPath.read(
        new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8), "$.code");
  }

  private static String newEmail() {
    return UUID.randomUUID() + "@captcha.example.test";
  }

  private static HttpServer startSiteverify() {
    try {
      HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext(
          "/siteverify",
          exchange -> {
            SITEVERIFY_CALLS.incrementAndGet();
            String form =
                URLDecoder.decode(
                    new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                    StandardCharsets.UTF_8);
            String body;
            if (form.contains("response=pass")) {
              body = "{\"success\":true,\"hostname\":\"meterengine.com\",\"error-codes\":[]}";
            } else if (form.contains("response=slow")) {
              sleepPastReadTimeout();
              body = "{\"success\":true,\"hostname\":\"meterengine.com\",\"error-codes\":[]}";
            } else {
              body = "{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}";
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
          });
      server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
      server.start();
      return server;
    } catch (IOException exception) {
      throw new UncheckedIOException(exception);
    }
  }

  private static void sleepPastReadTimeout() {
    try {
      Thread.sleep(3_500);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    }
  }
}
