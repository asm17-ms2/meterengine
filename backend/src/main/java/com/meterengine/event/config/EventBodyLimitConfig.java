package com.meterengine.event.config;

import com.meterengine.global.security.RequestBodyLimit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class EventBodyLimitConfig {

  @Bean
  RequestBodyLimit singleEventBodyLimit(EventBodyLimitProperties properties) {
    return new RequestBodyLimit("/v1/events", properties.single().toBytes());
  }

  @Bean
  RequestBodyLimit eventBatchBodyLimit(EventBodyLimitProperties properties) {
    return new RequestBodyLimit("/v1/event-batches", properties.batch().toBytes());
  }
}
