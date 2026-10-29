package com.meterengine.global.security;

import java.util.UUID;

public interface RequestAuditRecorder {
  void record(
      AuthenticatedPrincipal principal,
      String method,
      String route,
      int status,
      long durationMillis,
      UUID requestId,
      RequestAuditTarget target);
}
