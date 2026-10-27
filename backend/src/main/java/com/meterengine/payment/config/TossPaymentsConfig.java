package com.meterengine.payment.config;

import com.meterengine.payment.client.TossPaymentsClient;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class TossPaymentsConfig {

  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

  @Bean
  TossPaymentsClient tossPaymentsClient(
      RestClient.Builder builder, TossPaymentsProperties properties, MeterRegistry meterRegistry) {
    return new TossPaymentsClient(
        withTossDefaults(
                builder.requestFactory(
                    ClientHttpRequestFactoryBuilder.jdk()
                        .build(
                            HttpClientSettings.defaults()
                                .withTimeouts(CONNECT_TIMEOUT, READ_TIMEOUT))),
                properties)
            .build(),
        meterRegistry);
  }

  public static RestClient.Builder withTossDefaults(
      RestClient.Builder builder, TossPaymentsProperties properties) {
    return builder.baseUrl(properties.baseUrl().toString());
  }
}
