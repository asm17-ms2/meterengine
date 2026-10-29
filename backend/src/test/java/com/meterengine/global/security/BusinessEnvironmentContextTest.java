package com.meterengine.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

class BusinessEnvironmentContextTest {
  @Test
  void 중첩된_범위와_예외는_이전_환경을_복원한다() {
    BusinessEnvironment test =
        new BusinessEnvironment(UUID.randomUUID(), UUID.randomUUID(), "test");
    BusinessEnvironment live =
        new BusinessEnvironment(test.organizationId(), UUID.randomUUID(), "live");
    assertThat(BusinessEnvironmentContext.current()).isNull();
    String result =
        BusinessEnvironmentContext.run(
            test,
            () -> {
              assertThat(BusinessEnvironmentContext.current()).isEqualTo(test);
              assertThatThrownBy(
                      () ->
                          BusinessEnvironmentContext.run(
                              live,
                              () -> {
                                assertThat(BusinessEnvironmentContext.current()).isEqualTo(live);
                                throw new IllegalStateException("failed");
                              }))
                  .isInstanceOf(IllegalStateException.class);
              assertThat(BusinessEnvironmentContext.current()).isEqualTo(test);
              return "restored";
            });
    assertThat(result).isEqualTo("restored");
    assertThat(BusinessEnvironmentContext.current()).isNull();
    assertThat(BusinessEnvironmentContext.scopeId()).isEqualTo(new UUID(0, 0));
  }

  @Test
  void 작업스레드의_환경은_다른스레드나_다음작업으로_남지_않는다() throws Exception {
    BusinessEnvironment test =
        new BusinessEnvironment(UUID.randomUUID(), UUID.randomUUID(), "test");
    try (var executor = Executors.newSingleThreadExecutor()) {
      BusinessEnvironmentContext.run(
          test,
          () -> {
            try {
              assertThat(executor.submit(BusinessEnvironmentContext::current).get()).isNull();
            } catch (Exception exception) {
              throw new IllegalStateException(exception);
            }
          });
      var failed =
          executor.submit(
              () ->
                  BusinessEnvironmentContext.run(
                      test,
                      () -> {
                        assertThat(BusinessEnvironmentContext.environmentId())
                            .isEqualTo(test.environmentId());
                        throw new IllegalStateException("failed task");
                      }));
      assertThatThrownBy(failed::get)
          .isInstanceOf(ExecutionException.class)
          .hasCauseInstanceOf(IllegalStateException.class);
      assertThat(executor.submit(BusinessEnvironmentContext::current).get()).isNull();
      assertThat(BusinessEnvironmentContext.current()).isNull();
    }
  }
}
