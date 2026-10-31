package com.meterengine.global.database;

import java.util.stream.Stream;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@TestComponent
@org.springframework.boot.context.properties.EnableConfigurationProperties(
    com.meterengine.payment.config.TossPaymentsProperties.class)
@ComponentScan(
    basePackages = {
      "com.meterengine.invoiceview",
      "com.meterengine.payment",
      "com.meterengine.webhook",
      "com.meterengine.scheduling",
      "com.meterengine.notification",
      "com.meterengine.dashboard"
    })
@EnableJpaRepositories(
    basePackages = {
      "com.meterengine.invoiceview.repository",
      "com.meterengine.scheduling.repository"
    })
public class SideEffectEnvironmentTestConfig extends PaymentEnvironmentTestConfig {
  @Override
  protected String[] managedTypes() {
    return Stream.concat(
            Stream.of(super.managedTypes()),
            Stream.of("com.meterengine.invoiceview.entity.InvoiceViewLink"))
        .toArray(String[]::new);
  }
}
