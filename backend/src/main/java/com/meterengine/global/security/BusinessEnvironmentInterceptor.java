package com.meterengine.global.security;

import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ForbiddenException;
import com.meterengine.global.error.InvalidRequestException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Collections;
import java.util.Objects;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

public class BusinessEnvironmentInterceptor implements HandlerInterceptor {
  public static final String HEADER = "X-MeterEngine-Environment";
  public static final String ORGANIZATION_HEADER = "X-MeterEngine-Organization-Id";
  private final OrganizationEnvironments organizationEnvironments;

  public BusinessEnvironmentInterceptor(OrganizationEnvironments organizationEnvironments) {
    this.organizationEnvironments = organizationEnvironments;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null
        || !(authentication.getPrincipal() instanceof AuthenticatedPrincipal principal))
      return true;
    var organizations = Collections.list(request.getHeaders(ORGANIZATION_HEADER));
    if (organizations.size() > 1) throw new InvalidRequestException(ErrorCode.VALIDATION_ERROR);
    if (!organizations.isEmpty()) {
      java.util.UUID expected;
      try {
        expected = java.util.UUID.fromString(organizations.getFirst());
        if (!expected.toString().equalsIgnoreCase(organizations.getFirst()))
          throw new IllegalArgumentException();
      } catch (IllegalArgumentException exception) {
        throw new InvalidRequestException(ErrorCode.VALIDATION_ERROR);
      }
      if (!expected.equals(principal.organizationId()))
        throw new ForbiddenException(ErrorCode.PERMISSION_DENIED);
    }
    if (principal.organizationId() == null) return true;
    var values = Collections.list(request.getHeaders(HEADER));
    if (values.size() > 1) throw new InvalidRequestException(ErrorCode.VALIDATION_ERROR);
    String requested = values.isEmpty() ? null : values.getFirst();
    if (requested != null && !java.util.Set.of("legacy", "test", "live").contains(requested))
      throw new InvalidRequestException(ErrorCode.VALIDATION_ERROR);

    BusinessEnvironment environment;
    if (principal.kind() == PrincipalKind.API_KEY) {
      if (principal.environmentId() == null) {
        if (requested != null && !"legacy".equals(requested))
          throw new ForbiddenException(ErrorCode.ENVIRONMENT_MISMATCH);
        environment = new BusinessEnvironment(principal.organizationId(), null, null);
      } else {
        if (requested != null && !Objects.equals(principal.mode(), requested))
          throw new ForbiddenException(ErrorCode.ENVIRONMENT_MISMATCH);
        environment =
            organizationEnvironments.resolve(principal.organizationId(), principal.mode());
        if (!Objects.equals(environment.environmentId(), principal.environmentId()))
          throw new ForbiddenException(ErrorCode.ENVIRONMENT_MISMATCH);
      }
    } else {
      environment =
          requested == null || "legacy".equals(requested)
              ? new BusinessEnvironment(principal.organizationId(), null, null)
              : organizationEnvironments.resolve(principal.organizationId(), requested);
    }
    request.setAttribute(BusinessEnvironmentContext.ATTRIBUTE, environment);
    response.setHeader(HEADER, environment.mode() == null ? "legacy" : environment.mode());
    AuthenticatedPrincipal scoped = principal.inEnvironment(environment.environmentId());
    request.setAttribute(ApiCredentialAuthenticationFilter.PRINCIPAL_ATTRIBUTE, scoped);
    SecurityContextHolder.getContext()
        .setAuthentication(
            UsernamePasswordAuthenticationToken.authenticated(scoped, null, scoped.authorities()));
    if (environment.environmentId() != null
        && handler instanceof HandlerMethod method
        && !method.hasMethodAnnotation(EnvironmentReady.class)
        && !method.getBeanType().isAnnotationPresent(EnvironmentReady.class))
      throw new ConflictException(ErrorCode.ENVIRONMENT_NOT_READY);
    return true;
  }
}
