package com.meterengine.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;

import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ForbiddenException;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class BusinessEnvironmentInterceptorTest {

  private static final Object NON_CONTROLLER_HANDLER = new Object();

  @Mock private OrganizationEnvironments organizationEnvironments;

  private BusinessEnvironmentInterceptor interceptor;

  @BeforeEach
  void setUp() {
    interceptor = new BusinessEnvironmentInterceptor(organizationEnvironments);
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void API_키_요청은_환경을_DB에서_찾지_않고_키의_환경으로_통과한다() {
    AuthenticatedPrincipal principal = authenticateApiKey("live");
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(BusinessEnvironmentInterceptor.HEADER, "live");

    boolean proceeded =
        interceptor.preHandle(request, new MockHttpServletResponse(), NON_CONTROLLER_HANDLER);

    assertThat(proceeded).isTrue();
    assertThat(request.getAttribute(BusinessEnvironmentContext.ATTRIBUTE))
        .isEqualTo(
            new BusinessEnvironment(
                principal.organizationId(), principal.environmentId(), principal.mode()));
    verifyNoInteractions(organizationEnvironments);
  }

  @Test
  void API_키와_다른_환경_헤더는_environment_mismatch로_거절한다() {
    authenticateApiKey("live");
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(BusinessEnvironmentInterceptor.HEADER, "test");

    assertThatThrownBy(
            () ->
                interceptor.preHandle(
                    request, new MockHttpServletResponse(), NON_CONTROLLER_HANDLER))
        .isInstanceOfSatisfying(
            ForbiddenException.class,
            exception ->
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.ENVIRONMENT_MISMATCH));
  }

  private static AuthenticatedPrincipal authenticateApiKey(String mode) {
    AuthenticatedPrincipal principal =
        new AuthenticatedPrincipal(
            PrincipalKind.API_KEY,
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            null,
            mode,
            null,
            Set.of("ingest"),
            UUID.randomUUID());
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.authorities()));
    return principal;
  }
}
