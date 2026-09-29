package com.meterengine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MeterEngineApplicationTest {

  @Autowired private ApplicationContext applicationContext;

  @Test
  void 컨텍스트가_뜬다() {}

  @Test
  void 요청이_끝날_때까지_엔티티_매니저를_열어_두지_않는다() {
    assertThat(applicationContext.getBeanNamesForType(OpenEntityManagerInViewInterceptor.class))
        .isEmpty();
  }
}
