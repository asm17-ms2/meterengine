package com.meterengine.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ForbiddenException;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.ServletWebRequest;

class CurrentOrganizationIdArgumentResolverTest {

  private static final UUID ORGANIZATION_ID = UUID.randomUUID();

  private final OrganizationAccessPolicy organizationAccessPolicy =
      mock(OrganizationAccessPolicy.class);

  private final CurrentOrganizationIdArgumentResolver resolver =
      new CurrentOrganizationIdArgumentResolver(organizationAccessPolicy);

  private final MockHttpServletRequest request = new MockHttpServletRequest();

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

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

  @Test
  void 인증할_때_도입사가_활성이_아니라고_확인된_키는_DB를_읽지_않고_403_organization_not_active다() {
    authenticateApiKeyOfOrganizationActive(false);

    assertThatThrownBy(this::resolveOrganizationId)
        .isInstanceOfSatisfying(
            ForbiddenException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ORGANIZATION_NOT_ACTIVE));
    verifyNoInteractions(organizationAccessPolicy);
  }

  @Test
  void 인증할_때_도입사가_활성이라고_확인된_키는_DB를_읽지_않고_도입사를_돌려준다() {
    authenticateApiKeyOfOrganizationActive(true);

    assertThat(resolveOrganizationId()).isEqualTo(ORGANIZATION_ID);
    verifyNoInteractions(organizationAccessPolicy);
  }

  @Test
  void 도입사_상태를_싣지_않은_요청은_DB로_도입사를_확인한다() {
    authenticate(PrincipalKind.SESSION);

    assertThat(resolveOrganizationId()).isEqualTo(ORGANIZATION_ID);
    verify(organizationAccessPolicy).check(ORGANIZATION_ID);
  }

  private void authenticateApiKeyOfOrganizationActive(boolean organizationActive) {
    authenticate(PrincipalKind.API_KEY);
    request.setAttribute(
        ApiCredentialAuthenticationFilter.ORGANIZATION_ACTIVE_ATTRIBUTE, organizationActive);
  }

  private static void authenticate(PrincipalKind kind) {
    AuthenticatedPrincipal principal =
        new AuthenticatedPrincipal(
            kind, ORGANIZATION_ID, null, null, null, "live", null, Set.of("read"));
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.authorities()));
  }

  private UUID resolveOrganizationId() {
    return resolver.resolveArgument(
        parameterOf("uuid", UUID.class), null, new ServletWebRequest(request), null);
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
