package com.meterengine.audit.service;

import com.meterengine.global.security.AuthenticatedPrincipal;
import java.util.UUID;

public record AuditActor(String type, UUID id, UUID organizationId) {

  private static final String USER_ACCOUNT_TYPE = "user_account";

  private static final String API_KEY_TYPE = "api_key";

  private static final String SYSTEM_TYPE = "system";

  public static AuditActor system() {
    return system(null);
  }

  public static AuditActor system(UUID organizationId) {
    return new AuditActor(SYSTEM_TYPE, null, organizationId);
  }

  public static AuditActor from(AuthenticatedPrincipal principal) {
    return switch (principal.kind()) {
      case API_KEY ->
          new AuditActor(API_KEY_TYPE, principal.apiKeyId(), principal.organizationId());
      case SESSION -> userAccount(principal.userAccountId(), principal.organizationId());
    };
  }

  public static AuditActor userAccount(UUID userAccountId, UUID organizationId) {
    return new AuditActor(USER_ACCOUNT_TYPE, userAccountId, organizationId);
  }

  // 같은 행위자를 도입사만 바꿔 돌려주고, null을 주면 도입사 없는 행이 된다.
  public AuditActor withOrganizationId(UUID organizationId) {
    return new AuditActor(type, id, organizationId);
  }
}
