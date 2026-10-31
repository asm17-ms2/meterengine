package com.meterengine.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meterengine.global.database.DatabaseScope;
import com.meterengine.global.database.DatabaseScopes;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

class BusinessEnvironmentFilterTest {
  private final UUID organization = UUID.randomUUID();
  private final UUID environment = UUID.randomUUID();
  private final BusinessEnvironmentFilter filter =
      new BusinessEnvironmentFilter(
          new OrganizationEnvironments() {
            public BusinessEnvironment resolve(UUID id, String mode) {
              return new BusinessEnvironment(id, environment, mode);
            }

            public List<BusinessEnvironment> list(UUID id) {
              return List.of();
            }
          },
          new ErrorResponseWriter(JsonMapper.builder().build()));

  @AfterEach
  void clearSecurity() {
    SecurityContextHolder.clearContext();
    DatabaseScopes.clear();
  }

  @Test
  void checked_예외는_원문을_보존하고_같은_스레드의_다음_요청은_shared다() throws Exception {
    for (Exception failure : List.of(new ServletException("servlet"), new IOException("io"))) {
      authenticate();
      var request = new MockHttpServletRequest("GET", "/v1/customers");
      request.addHeader(BusinessEnvironmentInterceptor.HEADER, "live");
      assertThatThrownBy(
              () ->
                  filter.doFilter(
                      request,
                      new MockHttpServletResponse(),
                      (_, _) -> {
                        assertThat(DatabaseScopes.current()).isEqualTo(DatabaseScope.LIVE);
                        if (failure instanceof IOException io) throw io;
                        throw (ServletException) failure;
                      }))
          .isSameAs(failure);
      assertThat(DatabaseScopes.current()).isEqualTo(DatabaseScope.SHARED);
      assertThat(BusinessEnvironmentContext.current()).isNull();
      filter.doFilter(
          new MockHttpServletRequest("GET", "/v1/sessions/current"),
          new MockHttpServletResponse(),
          (_, _) -> assertThat(DatabaseScopes.current()).isEqualTo(DatabaseScope.SHARED));
    }
  }

  @Test
  void 독립_ERROR는_shared이고_중첩_ERROR는_진행_중인_scope를_지우지_않는다() throws Exception {
    var error = new MockHttpServletRequest("GET", "/error");
    error.setDispatcherType(DispatcherType.ERROR);
    filter.doFilter(
        error,
        new MockHttpServletResponse(),
        (_, _) -> assertThat(DatabaseScopes.current()).isEqualTo(DatabaseScope.SHARED));
    authenticate();
    var request = new MockHttpServletRequest("GET", "/v1/customers");
    request.addHeader(BusinessEnvironmentInterceptor.HEADER, "test");
    filter.doFilter(
        request,
        new MockHttpServletResponse(),
        (_, _) -> {
          assertThat(DatabaseScopes.current()).isEqualTo(DatabaseScope.TEST);
          filter.doFilter(
              error,
              new MockHttpServletResponse(),
              (_, _) -> assertThat(DatabaseScopes.current()).isEqualTo(DatabaseScope.TEST));
          assertThat(DatabaseScopes.current()).isEqualTo(DatabaseScope.TEST);
        });
    assertThat(DatabaseScopes.current()).isEqualTo(DatabaseScope.SHARED);
  }

  private void authenticate() {
    var principal =
        new AuthenticatedPrincipal(
            PrincipalKind.SESSION,
            organization,
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
  }
}
