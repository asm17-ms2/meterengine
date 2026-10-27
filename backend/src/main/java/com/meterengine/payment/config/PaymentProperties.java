package com.meterengine.payment.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "payment")
public record PaymentProperties(
    @DefaultValue("3") int retryMaxAttempts,
    @DefaultValue("PT24H") Duration retryInterval,
    @DefaultValue("PT2M") Duration confirmationDelay,
    @DefaultValue("PT10M") Duration notFoundFailureDelay,
    @DefaultValue("PT10M") Duration reconciliationLag) {

  // 토스 호출 상한(연결 3초, 읽기 60초)보다 넉넉히 길어야 응답 전의 승인을 없음으로 끝내지 않는다.
  private static final Duration MIN_NOT_FOUND_FAILURE_DELAY = Duration.ofMinutes(2);

  public PaymentProperties {
    if (notFoundFailureDelay.compareTo(confirmationDelay) < 0
        || notFoundFailureDelay.compareTo(MIN_NOT_FOUND_FAILURE_DELAY) < 0) {
      throw new IllegalArgumentException(
          "payment.not-found-failure-delay must be at least payment.confirmation-delay and "
              + MIN_NOT_FOUND_FAILURE_DELAY
              + ": not-found-failure-delay="
              + notFoundFailureDelay
              + ", confirmation-delay="
              + confirmationDelay);
    }
  }
}
