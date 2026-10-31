package com.meterengine.global.database;

import java.util.stream.Stream;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@TestComponent
@ComponentScan(basePackages = "com.meterengine.invoice")
@EnableJpaRepositories(
    basePackages = {"com.meterengine.invoice.repository", "com.meterengine.mail.repository"})
public class BillingEnvironmentTestConfig extends MeteringEnvironmentTestConfig {
  @Override
  protected String[] managedTypes() {
    return Stream.concat(
            Stream.of(super.managedTypes()),
            Stream.of(
                "com.meterengine.invoice.entity.Invoice",
                "com.meterengine.invoice.entity.InvoiceLine",
                "com.meterengine.mail.entity.OutboundEmail"))
        .toArray(String[]::new);
  }
}
