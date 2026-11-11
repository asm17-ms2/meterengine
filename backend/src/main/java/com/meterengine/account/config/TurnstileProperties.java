package com.meterengine.account.config;

import java.net.URI;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("meterengine.turnstile")
public record TurnstileProperties(
    @DefaultValue("false") boolean enabled,
    String secretKey,
    List<String> allowedHostnames,
    @DefaultValue("https://challenges.cloudflare.com/turnstile/v0/siteverify") URI siteverifyUrl) {

  public TurnstileProperties {
    allowedHostnames =
        allowedHostnames == null
            ? List.of()
            : allowedHostnames.stream().map(String::strip).filter(name -> !name.isEmpty()).toList();
    if (enabled && (secretKey == null || secretKey.isBlank() || allowedHostnames.isEmpty())) {
      throw new IllegalArgumentException(
          "meterengine.turnstile.secret-key and allowed-hostnames are required when enabled");
    }
  }

  @Override
  public String toString() {
    return "TurnstileProperties[enabled="
        + enabled
        + ", secretKey=(redacted), allowedHostnames="
        + allowedHostnames
        + ", siteverifyUrl="
        + siteverifyUrl
        + "]";
  }
}
