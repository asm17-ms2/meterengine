package com.meterengine.account.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("meterengine.auth-rate-limit")
public record AuthRateLimitProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue("100") int loginIpLimit,
    @DefaultValue("10") int loginAccountLimit,
    @DefaultValue("5m") Duration loginWindow,
    @DefaultValue("20") int signupIpLimit,
    @DefaultValue("3") int signupAccountLimit,
    @DefaultValue("30m") Duration signupWindow,
    List<String> trustedProxies) {

  public AuthRateLimitProperties {
    trustedProxies = trustedProxies == null ? List.of() : List.copyOf(trustedProxies);
    if (loginIpLimit < 1
        || loginAccountLimit < 1
        || signupIpLimit < 1
        || signupAccountLimit < 1
        || loginIpLimit > 1_000_000
        || loginAccountLimit > 1_000_000
        || signupIpLimit > 1_000_000
        || signupAccountLimit > 1_000_000) {
      throw new IllegalArgumentException("Authentication request limits must be 1 to 1000000");
    }
    if (!validWindow(loginWindow) || !validWindow(signupWindow)) {
      throw new IllegalArgumentException("Authentication request windows must be 1s to 24h");
    }
  }

  private static boolean validWindow(Duration value) {
    return value != null
        && value.compareTo(Duration.ofSeconds(1)) >= 0
        && value.compareTo(Duration.ofDays(1)) <= 0
        && value.getNano() == 0;
  }
}
