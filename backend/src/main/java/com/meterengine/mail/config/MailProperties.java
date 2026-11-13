package com.meterengine.mail.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("meterengine.mail")
public record MailProperties(
    @DefaultValue("log") Sender sender,
    String from,
    @DefaultValue("ap-northeast-2") String sesRegion) {

  public enum Sender {
    LOG,
    SES
  }

  public MailProperties {
    if (sender == Sender.SES && (from == null || from.isBlank())) {
      throw new IllegalArgumentException("meterengine.mail.from is required for the ses sender");
    }
  }
}
