package com.meterengine.account.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("meterengine.email-verification")
public record EmailVerificationProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("24h") Duration ttl,
    @DefaultValue("http://localhost:3000") URI linkBaseUrl) {

  public EmailVerificationProperties {
    if (ttl.compareTo(Duration.ofMinutes(1)) < 0 || ttl.compareTo(Duration.ofDays(7)) > 0) {
      throw new IllegalArgumentException("meterengine.email-verification.ttl must be 1m to 7d");
    }
    String scheme = linkBaseUrl.getScheme();
    if (!("https".equals(scheme) || "http".equals(scheme))
        || linkBaseUrl.getHost() == null
        || linkBaseUrl.getRawQuery() != null
        || linkBaseUrl.getRawFragment() != null) {
      throw new IllegalArgumentException(
          "meterengine.email-verification.link-base-url must be an http(s) origin");
    }
  }
}
