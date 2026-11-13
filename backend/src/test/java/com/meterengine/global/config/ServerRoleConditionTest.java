package com.meterengine.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

class ServerRoleConditionTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(RoleConfig.class);

  @Test
  void 역할을_주지_않으면_all로_보고_빈을_만든다() {
    contextRunner.run(context -> assertThat(context).hasBean("consumerOnly"));
  }

  @Test
  void 적은_역할이면_빈을_만든다() {
    contextRunner
        .withPropertyValues("meterengine.role=consumer")
        .run(context -> assertThat(context).hasBean("consumerOnly"));
  }

  @Test
  void 적지_않은_역할이면_빈을_만들지_않는다() {
    contextRunner
        .withPropertyValues("meterengine.role=ingest")
        .run(context -> assertThat(context).doesNotHaveBean("consumerOnly"));
  }

  @Test
  void 모르는_역할이면_컨텍스트가_뜨지_않는다() {
    contextRunner
        .withPropertyValues("meterengine.role=consumr")
        .run(context -> assertThat(context).hasFailed());
  }

  @Configuration(proxyBeanMethods = false)
  static class RoleConfig {

    @Bean
    @ConditionalOnServerRole({ServerRole.ALL, ServerRole.CONSUMER})
    String consumerOnly() {
      return "consumer";
    }
  }
}
