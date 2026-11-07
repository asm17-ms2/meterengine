package com.meterengine.event.config;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;

// id를 문자열로 받아, 값이 틀려도 바인딩 실패로 기동을 막지 않고 예열만 건너뛴다.
@ConfigurationProperties("meterengine.ingest-warmup")
public record IngestWarmupProperties(
    String apiKey,
    String organizationId,
    String customerId,
    int requests,
    @DurationUnit(ChronoUnit.SECONDS) Duration timeLimit) {

  @Override
  public String toString() {
    return "IngestWarmupProperties[apiKey=(redacted), organizationId="
        + organizationId
        + ", customerId="
        + customerId
        + ", requests="
        + requests
        + ", timeLimit="
        + timeLimit
        + "]";
  }
}
