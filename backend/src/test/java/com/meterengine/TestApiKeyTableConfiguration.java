package com.meterengine;

import com.meterengine.apikey.service.ApiKeyTableLoader;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@TestConfiguration(proxyBeanMethods = false)
public class TestApiKeyTableConfiguration {

  // 시험 컨텍스트마다 JdbcTemplate과 키 테이블이 따로 있어 둘을 짝지어 등록한다.
  // 시험 트랜잭션 안에서 JPA로 바꾼 키와 도입사는 flush해야 같은 커넥션의 SQL이 본다.
  @Bean
  SmartInitializingSingleton apiKeyTableRefresherRegistration(
      JdbcTemplate jdbcTemplate, EntityManager entityManager, ApiKeyTableLoader apiKeyTableLoader) {
    return () ->
        TestCredentials.registerApiKeyTableRefresher(
            jdbcTemplate,
            () -> {
              if (TransactionSynchronizationManager.isActualTransactionActive()) {
                entityManager.flush();
              }
              apiKeyTableLoader.refresh();
            });
  }
}
