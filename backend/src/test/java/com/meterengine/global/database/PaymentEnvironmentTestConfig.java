package com.meterengine.global.database;

import com.meterengine.global.crypto.CryptoProperties;
import com.meterengine.global.crypto.EncryptedStringConverter;
import com.meterengine.payment.config.PaymentProperties;
import com.meterengine.payment.controller.*;
import com.meterengine.payment.repository.PaymentArchiveRepository;
import com.meterengine.payment.service.*;
import com.meterengine.webhook.repository.WebhookEventRepository;
import com.meterengine.webhook.service.WebhookEventService;
import java.util.stream.Stream;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@TestComponent
@EnableConfigurationProperties({PaymentProperties.class, CryptoProperties.class})
@EnableJpaRepositories(basePackages = "com.meterengine.payment.repository")
@Import({
  EncryptedStringConverter.class,
  PaymentSettingService.class,
  TossPaymentsCredentialService.class,
  PaymentAttemptService.class,
  PaymentCancellationService.class,
  InvoicePaymentService.class,
  PaymentRetryEligibilityService.class,
  PaymentRetryPolicy.class,
  PaymentMetrics.class,
  PaymentMethodService.class,
  PaymentMethodRegistrationService.class,
  BillingKeyService.class,
  PaymentArchiveRepository.class,
  WebhookEventRepository.class,
  WebhookEventService.class,
  PaymentSettingController.class,
  PaymentMethodController.class,
  TossPaymentsCredentialController.class,
  PaymentAttemptController.class,
  PaymentCancellationController.class,
  InvoicePaymentController.class,
  com.meterengine.query.service.WorkspaceQueryService.class,
  com.meterengine.query.service.SearchCsvService.class,
  com.meterengine.query.controller.WorkspaceQueryController.class,
  com.meterengine.dashboard.service.DashboardService.class,
  com.meterengine.dashboard.controller.DashboardController.class
})
public class PaymentEnvironmentTestConfig extends BillingEnvironmentTestConfig {
  @org.springframework.beans.factory.annotation.Autowired
  org.springframework.beans.factory.config.ConfigurableListableBeanFactory beanFactory;

  @Override
  @org.springframework.context.annotation.Bean
  org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean entityManagerFactory(
      SchemaRoutingDataSource dataSource,
      org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer customizer) {
    var factory = super.entityManagerFactory(dataSource, customizer);
    factory
        .getJpaPropertyMap()
        .put(
            "hibernate.resource.beans.container",
            new org.springframework.orm.jpa.hibernate.SpringBeanContainer(beanFactory));
    return factory;
  }

  @Override
  protected String[] managedTypes() {
    return Stream.concat(
            Stream.of(super.managedTypes()),
            Stream.of(
                "com.meterengine.payment.entity.PaymentSetting",
                "com.meterengine.payment.entity.TossPaymentsCredential",
                "com.meterengine.payment.entity.BillingKey",
                "com.meterengine.payment.entity.PaymentAttempt",
                "com.meterengine.payment.entity.PaymentCancellation",
                "com.meterengine.payment.entity.PaymentMethodRegistration",
                "com.meterengine.payment.entity.PaymentReconciliationMismatch"))
        .toArray(String[]::new);
  }
}
