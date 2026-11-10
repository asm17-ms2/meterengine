package com.meterengine.global.security;

import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ForbiddenException;
import com.meterengine.global.error.UnauthorizedException;
import java.util.UUID;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

public class CurrentOrganizationIdArgumentResolver implements HandlerMethodArgumentResolver {

  private final OrganizationAccessPolicy organizationAccessPolicy;

  public CurrentOrganizationIdArgumentResolver(OrganizationAccessPolicy organizationAccessPolicy) {
    this.organizationAccessPolicy = organizationAccessPolicy;
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
    checkOrganizationActive(organizationId, webRequest);
    return organizationId;
  }

  // 인증하며 키 테이블에서 도입사 상태까지 확인했으면 DB를 다시 읽지 않는다.
  private void checkOrganizationActive(UUID organizationId, NativeWebRequest webRequest) {
    Object organizationActive =
        webRequest.getAttribute(
            ApiCredentialAuthenticationFilter.ORGANIZATION_ACTIVE_ATTRIBUTE,
            RequestAttributes.SCOPE_REQUEST);
    if (!(organizationActive instanceof Boolean active)) {
      organizationAccessPolicy.check(organizationId);
      return;
    }
    if (!active) {
      throw new ForbiddenException(ErrorCode.ORGANIZATION_NOT_ACTIVE);
    }
  }
}
