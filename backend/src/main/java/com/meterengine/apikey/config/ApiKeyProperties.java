package com.meterengine.apikey.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("meterengine.api-key")
public record ApiKeyProperties(
    @DefaultValue("24h") Duration replacementGrace,
    @DefaultValue("5s") Duration tableLoadRetryInterval,
    @DefaultValue("30s") Duration tableRefreshInterval,
    @DefaultValue("300") int testRateLimitPerMinute) {

  public ApiKeyProperties {
    if (testRateLimitPerMinute <= 0) {
      throw new IllegalArgumentException(
          "meterengine.api-key.test-rate-limit-per-minute must be positive");
    }
  }
}
