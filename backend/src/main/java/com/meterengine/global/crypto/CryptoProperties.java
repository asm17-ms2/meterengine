package com.meterengine.global.crypto;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "crypto")
public record CryptoProperties(@NotBlank String encryptionKey) {

  public static final String LOCAL_KEY = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=";
  public static final String TEST_KEY = "AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE=";

  @Override
  public String toString() {
    return "CryptoProperties[encryptionKey=<masked>]";
  }
}
