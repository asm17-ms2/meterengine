package com.meterengine.global.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public record AuthenticatedPrincipal(
    PrincipalKind kind,
    UUID organizationId,
    UUID apiKeyId,
    UUID userAccountId,
    UUID userSessionId,
    String mode,
    String role,
    Set<String> scopes) {

  private static final String ADMIN_SCOPE = "admin";

  private static final List<String> ALL_SCOPES = List.of("ingest", "read", ADMIN_SCOPE);

  private static final String SCOPE_AUTHORITY_PREFIX = "SCOPE_";

  private static final String ROLE_AUTHORITY_PREFIX = "ROLE_";

  public AuthenticatedPrincipal {
    scopes = Set.copyOf(scopes);
  }

  // 스코프와 역할을 메서드 보안이 검사할 권한으로 펼친다.
  public List<GrantedAuthority> authorities() {
    List<GrantedAuthority> authorities = new ArrayList<>();
    for (String scope : ALL_SCOPES) {
      if (grants(scope)) {
        authorities.add(new SimpleGrantedAuthority(SCOPE_AUTHORITY_PREFIX + scope));
      }
    }
    if (role != null) {
      authorities.add(new SimpleGrantedAuthority(ROLE_AUTHORITY_PREFIX + role));
    }
    return List.copyOf(authorities);
  }

  private boolean grants(String scope) {
    return AccessScopes.grants(scopes, scope);
  }
}
