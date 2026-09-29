package com.meterengine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.orm.jpa.support.OpenEntityManagerInViewInterceptor;
import org.springframework.web.client.RestClient;

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

  @Test
  void 토스_인증_헤더가_붙은_RestClient는_빈으로_노출되지_않는다() {
    assertThat(applicationContext.getBeanNamesForType(RestClient.class)).isEmpty();
  }
}
