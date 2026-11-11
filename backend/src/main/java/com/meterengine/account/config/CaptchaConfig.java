package com.meterengine.account.config;

import com.meterengine.account.client.TurnstileCaptchaVerifier;
import com.meterengine.account.service.CaptchaVerifier;
import com.meterengine.account.service.PassingCaptchaVerifier;
import java.time.Duration;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class CaptchaConfig {

  // 합이 4초라 가입과 다시 받기 액션의 기다림(15초) 안에 captcha_unavailable이 화면에 닿는다.
  private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(1);
  private static final Duration READ_TIMEOUT = Duration.ofSeconds(3);

  @Bean
  CaptchaVerifier captchaVerifier(RestClient.Builder builder, TurnstileProperties properties) {
    if (!properties.enabled()) return new PassingCaptchaVerifier();
    return new TurnstileCaptchaVerifier(
        builder
            .requestFactory(
                ClientHttpRequestFactoryBuilder.jdk()
                    .build(
                        HttpClientSettings.defaults().withTimeouts(CONNECT_TIMEOUT, READ_TIMEOUT)))
            .build(),
        properties.siteverifyUrl(),
        properties.secretKey(),
        properties.allowedHostnames());
  }
}
