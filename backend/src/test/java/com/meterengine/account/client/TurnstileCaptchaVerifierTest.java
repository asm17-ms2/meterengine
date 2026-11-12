package com.meterengine.account.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meterengine.global.error.BusinessException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.InvalidRequestException;
import com.meterengine.global.error.ServiceUnavailableException;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.web.client.RestClient;

class TurnstileCaptchaVerifierTest {

  private static final String SECRET = "1x0000000000000000000000000000000AA";

  private HttpServer server;
  private final AtomicInteger calls = new AtomicInteger();
  private final AtomicReference<String> lastForm = new AtomicReference<>();
  private volatile int status = 200;
  private volatile String body =
      "{\"success\":true,\"hostname\":\"meterengine.com\",\"error-codes\":[]}";
  private volatile Duration delay = Duration.ZERO;

  @BeforeEach
  void startServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/siteverify",
        exchange -> {
          calls.incrementAndGet();
          lastForm.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          try {
            Thread.sleep(delay.toMillis());
          } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
          }
          byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
          exchange.getResponseHeaders().add("Content-Type", "application/json");
          exchange.sendResponseHeaders(status, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
  }

  @AfterEach
  void stopServer() {
    server.stop(0);
  }

  @Test
  void 통과한_토큰은_예외_없이_지나가고_비밀_키와_토큰과_IP를_보낸다() {
    assertThatCode(() -> verifier().verify("token-1", "signup", "203.0.113.7"))
        .doesNotThrowAnyException();
    assertThat(lastForm.get())
        .contains("secret=" + SECRET)
        .contains("response=token-1")
        .contains("remoteip=203.0.113.7");
  }

  @Test
  void 토큰이_없으면_Siteverify를_부르지_않고_captcha_failed로_거부한다() {
    assertCode(() -> verifier().verify(null, "signup", null), ErrorCode.CAPTCHA_FAILED);
    assertCode(() -> verifier().verify(" ", "signup", null), ErrorCode.CAPTCHA_FAILED);
    assertThat(calls).hasValue(0);
  }

  @Test
  void 검증에_실패한_토큰은_captcha_failed로_거부한다() {
    body = "{\"success\":false,\"error-codes\":[\"invalid-input-response\"]}";
    assertCode(() -> verifier().verify("token-1", "signup", null), ErrorCode.CAPTCHA_FAILED);
    body = "{\"success\":false,\"error-codes\":[\"timeout-or-duplicate\"]}";
    assertCode(() -> verifier().verify("token-1", "signup", null), ErrorCode.CAPTCHA_FAILED);
  }

  @Test
  void 다른_hostname에서_풀린_토큰은_captcha_failed로_거부한다() {
    body = "{\"success\":true,\"hostname\":\"example.com\",\"error-codes\":[]}";
    assertCode(() -> verifier().verify("token-1", "signup", null), ErrorCode.CAPTCHA_FAILED);
  }

  @Test
  void 성공_응답에_hostname이_없으면_captcha_failed로_거부한다() {
    body = "{\"success\":true,\"error-codes\":[]}";
    assertCode(() -> verifier().verify("token-1", "signup", null), ErrorCode.CAPTCHA_FAILED);
  }

  @Test
  void 다른_action의_위젯에서_풀린_토큰은_captcha_failed로_거부한다() {
    body =
        "{\"success\":true,\"hostname\":\"meterengine.com\",\"action\":\"email_verification\",\"error-codes\":[]}";
    assertCode(() -> verifier().verify("token-1", "signup", null), ErrorCode.CAPTCHA_FAILED);
    body =
        "{\"success\":true,\"hostname\":\"meterengine.com\",\"action\":\"signup\",\"error-codes\":[]}";
    assertThatCode(() -> verifier().verify("token-1", "signup", null)).doesNotThrowAnyException();
  }

  @Test
  void 시험용_키처럼_action이_없는_성공_응답은_hostname만_보고_통과시킨다() {
    assertThatCode(() -> verifier().verify("token-1", "signup", null)).doesNotThrowAnyException();
  }

  @Test
  void Siteverify가_시간_안에_답하지_않으면_captcha_unavailable로_막는다() {
    delay = Duration.ofSeconds(2);
    assertCode(() -> verifier().verify("token-1", "signup", null), ErrorCode.CAPTCHA_UNAVAILABLE);
  }

  @Test
  void Siteverify가_5xx나_내부_오류를_주면_captcha_unavailable로_막는다() {
    status = 500;
    body = "{}";
    assertCode(() -> verifier().verify("token-1", "signup", null), ErrorCode.CAPTCHA_UNAVAILABLE);
    status = 200;
    body = "{\"success\":false,\"error-codes\":[\"internal-error\"]}";
    assertCode(() -> verifier().verify("token-1", "signup", null), ErrorCode.CAPTCHA_UNAVAILABLE);
    body = "{\"success\":false,\"error-codes\":[\"invalid-input-secret\"]}";
    assertCode(() -> verifier().verify("token-1", "signup", null), ErrorCode.CAPTCHA_UNAVAILABLE);
  }

  private TurnstileCaptchaVerifier verifier() {
    RestClient restClient =
        RestClient.builder()
            .requestFactory(
                ClientHttpRequestFactoryBuilder.jdk()
                    .build(
                        HttpClientSettings.defaults()
                            .withTimeouts(Duration.ofSeconds(1), Duration.ofMillis(500))))
            .build();
    return new TurnstileCaptchaVerifier(
        restClient,
        URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/siteverify"),
        SECRET,
        List.of("meterengine.com"));
  }

  private static void assertCode(
      org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode code) {
    Class<? extends BusinessException> kind =
        code.getStatus().value() == 400
            ? InvalidRequestException.class
            : ServiceUnavailableException.class;
    assertThatThrownBy(call)
        .isInstanceOf(kind)
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(code);
  }
}
