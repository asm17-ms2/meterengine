package com.meterengine.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;

class CurrentOrganizationIdArgumentResolverTest {

  private final CurrentOrganizationIdArgumentResolver resolver =
      new CurrentOrganizationIdArgumentResolver(mock(OrganizationAccessPolicy.class));

  @Test
  void UUID_파라미터에_붙은_어노테이션을_맡는다() {
    assertThat(resolver.supportsParameter(parameterOf("uuid", UUID.class))).isTrue();
  }

  @Test
  void 어노테이션이_없는_파라미터는_맡지_않는다() {
    assertThat(resolver.supportsParameter(parameterOf("plain", UUID.class))).isFalse();
  }

  @Test
  void UUID가_아닌_파라미터에_붙으면_다른_리졸버로_넘기지_않고_예외다() {
    assertThatThrownBy(() -> resolver.supportsParameter(parameterOf("text", String.class)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("CurrentOrganizationId");
  }

  private static MethodParameter parameterOf(String methodName, Class<?> parameterType) {
    try {
      return new MethodParameter(Handlers.class.getDeclaredMethod(methodName, parameterType), 0);
    } catch (NoSuchMethodException e) {
      throw new IllegalStateException(e);
    }
  }

  static class Handlers {

    void uuid(@CurrentOrganizationId UUID organizationId) {}

    void plain(UUID organizationId) {}

    void text(@CurrentOrganizationId String organizationId) {}
  }
}
