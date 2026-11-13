package com.meterengine.liveenvironment.config;

import com.meterengine.liveenvironment.client.NtsBusinessRegistrationLookup;
import com.meterengine.liveenvironment.service.BusinessRegistrationLookup;
import com.meterengine.liveenvironment.service.UncheckedBusinessRegistrationLookup;
import java.time.Duration;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class BusinessRegistrationConfig {

  // 신청 요청이 프론트의 기다림 안에 끝나게 짧게 둔다. 넘기면 신청은 받고 "자동 확인 못 함"으로 남긴다.
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(1);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

  @Bean
  BusinessRegistrationLookup businessRegistrationLookup(
      RestClient.Builder builder, BusinessRegistrationProperties properties) {
    if (!properties.enabled()) return new UncheckedBusinessRegistrationLookup();
    return new NtsBusinessRegistrationLookup(
        builder
            .requestFactory(
                ClientHttpRequestFactoryBuilder.jdk()
                    .build(
                        HttpClientSettings.defaults().withTimeouts(CONNECT_TIMEOUT, READ_TIMEOUT)))
            .build(),
        properties.baseUrl(),
        properties.serviceKey());
  }
}
