package com.meterengine;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MeterEngineApplicationTest {

  @Test
  void 컨텍스트가_뜬다() {}
}
