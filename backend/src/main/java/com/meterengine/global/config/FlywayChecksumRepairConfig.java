package com.meterengine.global.config;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class FlywayChecksumRepairConfig {

  // 적용된 마이그레이션의 체크섬을 파일에 맞춘 뒤 마이그레이션을 돌린다.
  @Bean
  FlywayMigrationStrategy repairThenMigrate() {
    return flyway -> {
      flyway.repair();
      flyway.migrate();
    };
  }
}
