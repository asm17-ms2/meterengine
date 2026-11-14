package com.meterengine.liveenvironment.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("meterengine.business-registration")
public record BusinessRegistrationProperties(
    @DefaultValue("false") boolean enabled,
    String serviceKey,
    @DefaultValue("https://api.odcloud.kr/api/nts-businessman/v1") URI baseUrl) {

  public BusinessRegistrationProperties {
    if (enabled && (serviceKey == null || serviceKey.isBlank())) {
      throw new IllegalArgumentException(
          "meterengine.business-registration.service-key is required when enabled");
    }
  }

  @Override
  public String toString() {
    return "BusinessRegistrationProperties[enabled="
        + enabled
        + ", serviceKey=(redacted), baseUrl="
        + baseUrl
        + "]";
  }
}
