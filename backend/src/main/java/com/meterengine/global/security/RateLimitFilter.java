package com.meterengine.global.security;

import com.meterengine.global.error.ErrorCode;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

public class RateLimitFilter extends OncePerRequestFilter {

  static final String REJECTION_COUNTER_NAME = "meterengine.api.key.rate.limit.rejections";

  private static final long REFILL_PERIOD_MILLIS = 60_000;

  private static final long MILLIS_PER_SECOND = 1_000;

  private static final long MINIMUM_RETRY_AFTER_SECONDS = 1;

  private final Clock clock;

  private final ErrorResponseWriter errorResponseWriter;

  private final Counter rejections;

  private final Map<UUID, TokenBucket> bucketsByApiKeyId = new ConcurrentHashMap<>();

  public RateLimitFilter(
      Clock clock, ErrorResponseWriter errorResponseWriter, MeterRegistry meterRegistry) {
    this.clock = clock;
    this.errorResponseWriter = errorResponseWriter;
    this.rejections =
        Counter.builder(REJECTION_COUNTER_NAME)
            .description("API 키 분당 호출 한도를 넘어 429로 거절한 요청 수")
            .register(meterRegistry);
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    if (request.getAttribute(ApiCredentialAuthenticationFilter.PRINCIPAL_ATTRIBUTE)
            instanceof AuthenticatedPrincipal principal
        && principal.kind() == PrincipalKind.API_KEY
        && request.getAttribute(ApiCredentialAuthenticationFilter.RATE_LIMIT_PER_MINUTE_ATTRIBUTE)
            instanceof Integer limitPerMinute) {
      long nowMillis = clock.millis();
      TokenBucket bucket =
          bucketsByApiKeyId.compute(
              principal.apiKeyId(),
              (apiKeyId, current) -> TokenBucket.take(current, limitPerMinute, nowMillis));
      if (bucket.retryAfterSeconds() > 0) {
        rejections.increment();
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(bucket.retryAfterSeconds()));
        errorResponseWriter.write(response, ErrorCode.RATE_LIMIT_EXCEEDED);
        return;
      }
    }
    filterChain.doFilter(request, response);
  }

  // 토큰 하나를 60000단위로 세고 밀리초마다 분당 한도만큼 단위를 채워 정수로만 계산한다.
  private record TokenBucket(long units, long refilledAtMillis, long retryAfterSeconds) {

    static TokenBucket take(TokenBucket current, int limitPerMinute, long nowMillis) {
      long capacity = limitPerMinute * REFILL_PERIOD_MILLIS;
      long units = capacity;
      long refilledAtMillis = nowMillis;
      if (current != null) {
        long elapsedMillis =
            Math.clamp(nowMillis - current.refilledAtMillis(), 0, REFILL_PERIOD_MILLIS);
        units = Math.min(capacity, current.units() + elapsedMillis * limitPerMinute);
        refilledAtMillis = Math.max(nowMillis, current.refilledAtMillis());
      }
      if (units >= REFILL_PERIOD_MILLIS) {
        return new TokenBucket(units - REFILL_PERIOD_MILLIS, refilledAtMillis, 0);
      }
      long waitMillis = Math.ceilDiv(REFILL_PERIOD_MILLIS - units, (long) limitPerMinute);
      long retryAfterSeconds =
          Math.max(MINIMUM_RETRY_AFTER_SECONDS, Math.ceilDiv(waitMillis, MILLIS_PER_SECOND));
      return new TokenBucket(units, refilledAtMillis, retryAfterSeconds);
    }
  }
}
