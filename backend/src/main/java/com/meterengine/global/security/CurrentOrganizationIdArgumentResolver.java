package com.meterengine.global.security;

import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ForbiddenException;
import com.meterengine.global.error.UnauthorizedException;
import com.meterengine.organization.service.OrganizationService;
import java.util.UUID;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class CurrentOrganizationIdArgumentResolver implements HandlerMethodArgumentResolver {

  private static final String ACTIVE_STATUS = "active";

  private final OrganizationService organizationService;

  public CurrentOrganizationIdArgumentResolver(OrganizationService organizationService) {
    this.organizationService = organizationService;
  }

  @Override
  public boolean supportsParameter(MethodParameter parameter) {
    if (!parameter.hasParameterAnnotation(CurrentOrganizationId.class)) {
      return false;
    }
    if (parameter.getParameterType() != UUID.class) {
      throw new IllegalStateException(
          "@CurrentOrganizationId는 UUID 파라미터에만 붙인다: " + parameter.getExecutable());
    }
    return true;
  }

  @Override
  public UUID resolveArgument(
      MethodParameter parameter,
      ModelAndViewContainer mavContainer,
      NativeWebRequest webRequest,
      WebDataBinderFactory binderFactory) {
    Authentication authentication =
        SecurityContextHolder.getContextHolderStrategy().getContext().getAuthentication();
    if (authentication == null
        || !(authentication.getPrincipal() instanceof AuthenticatedPrincipal principal)) {
      throw new UnauthorizedException(ErrorCode.AUTHENTICATION_REQUIRED);
    }
    UUID organizationId = principal.organizationId();
    if (organizationId == null) {
      throw new ForbiddenException(ErrorCode.PERMISSION_DENIED);
    }
    if (!ACTIVE_STATUS.equals(organizationService.get(organizationId).getStatus())) {
      throw new ForbiddenException(ErrorCode.ORGANIZATION_NOT_ACTIVE);
    }
    return organizationId;
  }
}
