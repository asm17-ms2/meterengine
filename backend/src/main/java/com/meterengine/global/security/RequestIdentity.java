package com.meterengine.global.security;

import java.util.UUID;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

public record RequestIdentity(String actorType, UUID actorId, UUID requestId) {
  public static RequestIdentity current() {
    var authentication = SecurityContextHolder.getContext().getAuthentication();
    AuthenticatedPrincipal principal =
        authentication != null
                && authentication.getPrincipal() instanceof AuthenticatedPrincipal actor
            ? actor
            : null;
    String type =
        principal == null
            ? "system"
            : switch (principal.kind()) {
              case API_KEY -> "api_key";
              case SESSION -> "user_account";
            };
    UUID actorId =
        principal == null
            ? null
            : switch (principal.kind()) {
              case API_KEY -> principal.apiKeyId();
              case SESSION -> principal.userAccountId();
            };
    RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    UUID requestId =
        attributes == null
            ? null
            : (UUID)
                attributes.getAttribute(
                    RequestAuditFilter.REQUEST_ID_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
    return new RequestIdentity(type, actorId, requestId);
  }
}
