package com.meterengine.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.meterengine.TestcontainersConfiguration;
import com.meterengine.account.config.AuthRateLimitProperties;
import com.meterengine.account.repository.AuthRequestRateLimitRepository;
import com.meterengine.account.service.AuthRequestRateLimitService;
import com.meterengine.global.error.TooManyRequestsException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(
    properties = {
      "meterengine.auth-rate-limit.enabled=true",
      "meterengine.auth-rate-limit.login-ip-limit=3",
      "meterengine.auth-rate-limit.login-account-limit=2",
      "meterengine.auth-rate-limit.login-window=5m",
      "meterengine.auth-rate-limit.signup-ip-limit=3",
      "meterengine.auth-rate-limit.signup-account-limit=2",
      "meterengine.auth-rate-limit.signup-window=30m",
      "meterengine.auth-rate-limit.trusted-proxies=10.8.0.0/16"
    })
class AuthRequestRateLimitIntegrationTest {
  @Autowired private WebApplicationContext context;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private AuthRequestRateLimitRepository repository;
  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    jdbcTemplate.update("DELETE FROM auth_request_rate_limit");
    mvc = MockMvcTester.from(context, builder -> builder.apply(springSecurity()).build());
  }

  @Test
  void 실패한_로그인도_다른_IP에서_같은_계정_한도를_공유하고_차단_감사를_남긴다() throws Exception {
    String email = email();
    assertThat(login(email, "203.0.113.1", null)).hasStatus(401);
    assertThat(login(email.toUpperCase(), "203.0.113.2", null)).hasStatus(401);
    MvcTestResult denied = login(email, "203.0.113.3", null);
    assertLimited(denied);
    String requestId = denied.getResponse().getHeader("X-Request-ID");
    String audit =
        jdbcTemplate.queryForObject(
            "SELECT detail::text FROM audit_log WHERE detail->>'request_id'=?",
            String.class,
            requestId);
    assertThat(audit)
        .contains("429", "/v1/sessions")
        .doesNotContain(email, "wrong-password", "203.0.113.3");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT attempts FROM auth_request_rate_limit WHERE scope='login_account'",
                Long.class))
        .isEqualTo(3);
  }

  @Test
  void 신뢰하지_않는_헤더를_바꾸어도_계정이_다른_로그인의_IP_한도는_늘어나지_않는다() {
    for (int index = 0; index < 3; index++) {
      assertThat(login(email(), "203.0.113.4", "198.51.100." + (index + 1))).hasStatus(401);
    }
    assertLimited(login(email(), "203.0.113.4", "198.51.100.200"));
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM auth_request_rate_limit WHERE scope='login_ip'", Long.class))
        .isEqualTo(1);
  }

  @Test
  void 신뢰한_프록시의_단일_헤더만_원래_클라이언트를_구분한다() {
    for (int index = 0; index < 4; index++) {
      assertThat(login(email(), "10.8.1.1", "198.51.100." + (index + 1))).hasStatus(401);
    }
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM auth_request_rate_limit WHERE scope='login_ip'", Long.class))
        .isEqualTo(4);
  }

  @Test
  void 가입_IP_한도를_넘으면_새_사용자나_도입사를_만들지_않는다() {
    for (int index = 0; index < 3; index++) {
      assertThat(signup(email(), "203.0.113.5")).hasStatus(201);
    }
    String rejected = email();
    assertLimited(signup(rejected, "203.0.113.5"));
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM user_account WHERE email=?", Long.class, rejected))
        .isZero();
  }

  @Test
  void 가입_중복과_실패_뒤에도_계정_시도는_유지되고_가입과_로그인_카운터는_분리된다() {
    String email = email();
    assertThat(signup(email, "203.0.113.6")).hasStatus(201);
    assertThat(signup(email, "203.0.113.7")).hasStatus(409);
    assertLimited(signup(email, "203.0.113.8"));
    assertThat(login(email, "203.0.113.9", null)).hasStatus(401);
    assertThat(login(email, "203.0.113.10", null)).hasStatus(401);
    assertLimited(login(email, "203.0.113.11", null));
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM auth_request_rate_limit WHERE scope IN ('login_account','signup_account')",
                Long.class))
        .isEqualTo(2);
  }

  @Test
  void 존재하는_계정과_없는_계정은_같은_실패와_한도_응답을_받는다() throws Exception {
    String existing = email();
    String unknown = email();
    assertThat(signup(existing, "203.0.113.12")).hasStatus(201);
    for (int index = 0; index < 2; index++) {
      MvcTestResult known = login(existing, "203.0.113.13", null);
      MvcTestResult absent = login(unknown, "203.0.113.14", null);
      assertThat(known).hasStatus(401);
      assertThat(absent).hasStatus(401);
      assertThat(known.getResponse().getContentAsString())
          .isEqualTo(absent.getResponse().getContentAsString());
    }
    MvcTestResult known = login(existing, "203.0.113.13", null);
    MvcTestResult absent = login(unknown, "203.0.113.14", null);
    assertLimited(known);
    assertLimited(absent);
    assertThat(known.getResponse().getContentAsString())
        .isEqualTo(absent.getResponse().getContentAsString());
  }

  @Test
  void 만료된_한도는_풀리고_원래_식별자_대신_해시만_남는다() {
    String email = email();
    assertThat(login(email, "203.0.113.15", null)).hasStatus(401);
    assertThat(login(email, "203.0.113.15", null)).hasStatus(401);
    assertLimited(login(email, "203.0.113.15", null));
    jdbcTemplate.update(
        "UPDATE auth_request_rate_limit SET expires_at=statement_timestamp()-interval '1 second'");
    assertThat(login(email, "203.0.113.15", null)).hasStatus(401);
    assertThat(
            jdbcTemplate.queryForList("SELECT attempts FROM auth_request_rate_limit", Long.class))
        .containsOnly(1L);
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT octet_length(identity_hash) FROM auth_request_rate_limit", Integer.class))
        .containsOnly(32);
  }

  @Test
  void 두_서비스_인스턴스의_동시_요청도_같은_DB_한도만큼만_통과한다() throws Exception {
    var properties =
        new AuthRateLimitProperties(
            true, 5, 5, Duration.ofMinutes(5), 3, 2, Duration.ofMinutes(30), List.of());
    var nodes =
        List.of(
            new AuthRequestRateLimitService(repository, properties),
            new AuthRequestRateLimitService(repository, properties));
    String email = email();
    CountDownLatch start = new CountDownLatch(1);
    AtomicInteger allowed = new AtomicInteger();
    AtomicInteger limited = new AtomicInteger();
    try (var executor = Executors.newFixedThreadPool(8)) {
      List<Future<?>> results = new ArrayList<>();
      for (int index = 0; index < 32; index++) {
        var node = nodes.get(index % 2);
        results.add(
            executor.submit(
                () -> {
                  try {
                    start.await();
                    var request = new MockHttpServletRequest();
                    request.setRemoteAddr("203.0.113.16");
                    node.login(email, request);
                    allowed.incrementAndGet();
                  } catch (TooManyRequestsException exception) {
                    assertThat(exception.getRetryAfterSeconds()).isPositive();
                    limited.incrementAndGet();
                  } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                  }
                }));
      }
      start.countDown();
      for (Future<?> result : results) result.get(30, TimeUnit.SECONDS);
    }
    assertThat(allowed).hasValue(5);
    assertThat(limited).hasValue(27);
    assertThat(
            jdbcTemplate.queryForList("SELECT attempts FROM auth_request_rate_limit", Long.class))
        .containsOnly(6L);
  }

  private MvcTestResult login(String email, String peer, String forwarded) {
    var request =
        mvc.post()
            .uri("/v1/sessions")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"wrong-password\"}")
            .with(
                servletRequest -> {
                  servletRequest.setRemoteAddr(peer);
                  return servletRequest;
                });
    if (forwarded != null)
      request
          .header(AuthRequestRateLimitService.CLIENT_IP_HEADER, forwarded)
          .header("X-Forwarded-For", forwarded);
    return request.exchange();
  }

  private MvcTestResult signup(String email, String peer) {
    return mvc.post()
        .uri("/v1/signups")
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            """
            {"organization_name":"시도 제한 도입사","name":"가입자","email":"%s","password":"valid-signup-password","consents":[
              {"document":"terms_of_service","document_version":"2026-10-01"},
              {"document":"privacy_policy","document_version":"2026-10-01"}
            ]}
            """
                .formatted(email))
        .with(
            request -> {
              request.setRemoteAddr(peer);
              return request;
            })
        .exchange();
  }

  private static void assertLimited(MvcTestResult result) {
    assertThat(result)
        .hasStatus(429)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("rate_limit_exceeded");
    assertThat(Long.parseLong(result.getResponse().getHeader("Retry-After"))).isPositive();
  }

  private static String email() {
    return "limit-" + UUID.randomUUID() + "@example.com";
  }
}
