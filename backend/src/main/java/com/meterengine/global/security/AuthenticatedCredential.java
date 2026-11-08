package com.meterengine.global.security;

// organizationActive는 인증하며 도입사 상태까지 확인한 키에만 있고, 없으면 도입사 범위 요청이 DB로 확인한다.
public record AuthenticatedCredential(
    AuthenticatedPrincipal principal, Integer rateLimitPerMinute, Boolean organizationActive) {

  public AuthenticatedCredential(AuthenticatedPrincipal principal, Integer rateLimitPerMinute) {
    this(principal, rateLimitPerMinute, null);
  }
}
