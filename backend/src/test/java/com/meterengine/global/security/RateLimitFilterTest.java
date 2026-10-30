package com.meterengine.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.global.error.ErrorCode;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

class RateLimitFilterTest {

  private final MutableClock clock = new MutableClock(Instant.parse("2026-10-26T00:00:00Z"));

  private final AtomicInteger passedCount = new AtomicInteger();

  private final FilterChain countingChain = (request, response) -> passedCount.incrementAndGet();

  private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

  private final RateLimitFilter rateLimitFilter =
      new RateLimitFilter(
          clock, new ErrorResponseWriter(JsonMapper.builder().build()), meterRegistry);

  @Test
  void 분당_한도_5인_키의_6번째_요청은_429_rate_limit_exceeded와_Retry_After_12초다() throws Exception {
    AuthenticatedPrincipal apiKey = apiKeyPrincipal();

    for (int count = 0; count < 5; count++) {
      assertThat(send(apiKey, 5).getStatus()).isEqualTo(200);
    }
    MockHttpServletResponse rejected = send(apiKey, 5);

    assertThat(rejected.getStatus()).isEqualTo(429);
    assertThat(rejected.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("12");
    assertThat(rejected.getContentAsString())
        .contains("\"code\":\"" + ErrorCode.RATE_LIMIT_EXCEEDED.getCode() + "\"");
    assertThat(passedCount).hasValue(5);
  }

  @Test
  void 한도를_넘겨_거절한_요청만_거절_수로_센다() throws Exception {
    exhaust(apiKeyPrincipal(), 5);

    assertThat(meterRegistry.get(RateLimitFilter.REJECTION_COUNTER_NAME).counter().count())
        .isEqualTo(1.0);
  }

  @Test
  void 한도가_NULL인_키는_50번_연속_통과한다() throws Exception {
    AuthenticatedPrincipal apiKey = apiKeyPrincipal();

    for (int count = 0; count < 50; count++) {
      assertThat(send(apiKey, null).getStatus()).isEqualTo(200);
    }

    assertThat(passedCount).hasValue(50);
  }

  @Test
  void 세션_주체는_한도_속성이_있어도_제한하지_않는다() throws Exception {
    AuthenticatedPrincipal session =
        new AuthenticatedPrincipal(
            PrincipalKind.SESSION,
            UUID.randomUUID(),
            null,
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            "member",
            Set.of());

    for (int count = 0; count < 50; count++) {
      assertThat(send(session, 1).getStatus()).isEqualTo(200);
    }

    assertThat(passedCount).hasValue(50);
  }

  @Test
  void 인증되지_않은_요청은_그대로_다음_필터로_넘긴다() throws Exception {
    for (int count = 0; count < 50; count++) {
      MockHttpServletResponse response = new MockHttpServletResponse();
      rateLimitFilter.doFilter(new MockHttpServletRequest(), response, countingChain);
      assertThat(response.getStatus()).isEqualTo(200);
    }

    assertThat(passedCount).hasValue(50);
  }

  @Test
  void 시간이_지나면_채워진_만큼_다시_통과한다() throws Exception {
    AuthenticatedPrincipal apiKey = apiKeyPrincipal();
    exhaust(apiKey, 5);

    clock.advance(Duration.ofMillis(11_999));
    assertThat(send(apiKey, 5).getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
    clock.advance(Duration.ofMillis(1));
    assertThat(send(apiKey, 5).getStatus()).isEqualTo(200);
    assertThat(send(apiKey, 5).getStatus()).isEqualTo(429);

    clock.advance(Duration.ofMinutes(10));
    for (int count = 0; count < 5; count++) {
      assertThat(send(apiKey, 5).getStatus()).isEqualTo(200);
    }
    assertThat(send(apiKey, 5).getStatus()).isEqualTo(429);
  }

  @Test
  void Retry_After는_다음_토큰까지_남은_초를_올림한다() throws Exception {
    AuthenticatedPrincipal apiKey = apiKeyPrincipal();
    exhaust(apiKey, 5);

    clock.advance(Duration.ofMillis(1_500));

    assertThat(send(apiKey, 5).getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("11");
  }

  @Test
  void 한도가_바뀌면_다음_요청부터_새_한도로_자른다() throws Exception {
    AuthenticatedPrincipal apiKey = apiKeyPrincipal();
    assertThat(send(apiKey, 10).getStatus()).isEqualTo(200);

    assertThat(send(apiKey, 2).getStatus()).isEqualTo(200);
    assertThat(send(apiKey, 2).getStatus()).isEqualTo(200);
    MockHttpServletResponse rejected = send(apiKey, 2);

    assertThat(rejected.getStatus()).isEqualTo(429);
    assertThat(rejected.getHeader(HttpHeaders.RETRY_AFTER)).isEqualTo("30");
  }

  @Test
  void 키마다_따로_센다() throws Exception {
    AuthenticatedPrincipal exhausted = apiKeyPrincipal();
    exhaust(exhausted, 1);

    assertThat(send(apiKeyPrincipal(), 1).getStatus()).isEqualTo(200);
  }

  @Test
  void 동시에_보낸_요청도_한도만큼만_통과한다() throws Exception {
    AuthenticatedPrincipal apiKey = apiKeyPrincipal();
    CountDownLatch start = new CountDownLatch(1);
    List<Future<Integer>> statuses = new ArrayList<>();

    try (ExecutorService executor = Executors.newFixedThreadPool(16)) {
      for (int count = 0; count < 200; count++) {
        statuses.add(
            executor.submit(
                () -> {
                  start.await();
                  return send(apiKey, 50).getStatus();
                }));
      }
      start.countDown();
      int rejectedCount = 0;
      for (Future<Integer> status : statuses) {
        if (status.get() == 429) {
          rejectedCount++;
        }
      }
      assertThat(rejectedCount).isEqualTo(150);
    }

    assertThat(passedCount).hasValue(50);
  }

  private void exhaust(AuthenticatedPrincipal apiKey, int limitPerMinute) throws Exception {
    for (int count = 0; count < limitPerMinute; count++) {
      assertThat(send(apiKey, limitPerMinute).getStatus()).isEqualTo(200);
    }
    assertThat(send(apiKey, limitPerMinute).getStatus()).isEqualTo(429);
  }

  private MockHttpServletResponse send(AuthenticatedPrincipal principal, Integer limitPerMinute)
      throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/customers");
    request.setAttribute(ApiCredentialAuthenticationFilter.PRINCIPAL_ATTRIBUTE, principal);
    request.setAttribute(
        ApiCredentialAuthenticationFilter.RATE_LIMIT_PER_MINUTE_ATTRIBUTE, limitPerMinute);
    MockHttpServletResponse response = new MockHttpServletResponse();
    rateLimitFilter.doFilter(request, response, countingChain);
    return response;
  }

  private static AuthenticatedPrincipal apiKeyPrincipal() {
    return new AuthenticatedPrincipal(
        PrincipalKind.API_KEY,
        UUID.randomUUID(),
        UUID.randomUUID(),
        null,
        null,
        "live",
        null,
        Set.of("read"));
  }

  private static final class MutableClock extends Clock {

    private volatile Instant currentAt;

    private MutableClock(Instant currentAt) {
      this.currentAt = currentAt;
    }

    private void advance(Duration duration) {
      currentAt = currentAt.plus(duration);
    }

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return currentAt;
    }
  }
}
