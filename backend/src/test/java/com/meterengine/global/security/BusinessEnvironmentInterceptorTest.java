package com.meterengine.global.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meterengine.global.error.InvalidRequestException;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class BusinessEnvironmentInterceptorTest {
  @AfterEach
  void clearSecurity() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void 업무_세션은_환경_헤더가_없으면_거절한다() {
    assertMissingEnvironment(null);
  }

  @Test
  void 업무_세션은_legacy_헤더를_거절한다() {
    assertMissingEnvironment("legacy");
  }

  private void assertMissingEnvironment(String mode) {
    var principal =
        new AuthenticatedPrincipal(
            PrincipalKind.SESSION,
            UUID.randomUUID(),
            null,
            UUID.randomUUID(),
            UUID.randomUUID(),
            null,
            "member",
            Set.of("admin"));
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.authorities()));
    var request = new MockHttpServletRequest("GET", "/v1/customers");
    if (mode != null) request.addHeader(BusinessEnvironmentInterceptor.HEADER, mode);
    var interceptor =
        new BusinessEnvironmentInterceptor(
            new OrganizationEnvironments() {
              public BusinessEnvironment resolve(UUID organization, String selected) {
                return new BusinessEnvironment(organization, UUID.randomUUID(), selected);
              }

              public java.util.List<BusinessEnvironment> list(UUID organization) {
                return java.util.List.of();
              }
            });
    assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), null))
        .isInstanceOf(InvalidRequestException.class);
  }
}
