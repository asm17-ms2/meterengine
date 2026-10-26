package com.meterengine.account.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("meterengine.bootstrap")
public record BootstrapProperties(
    String operatorEmail,
    String operatorPassword,
    String frontendApiKey,
    String frontendOrganizationId) {

  @Override
  public String toString() {
    return "BootstrapProperties[operatorEmail="
        + operatorEmail
        + ", operatorPassword=(redacted), frontendApiKey=(redacted), frontendOrganizationId="
        + frontendOrganizationId
        + "]";
  }
}
