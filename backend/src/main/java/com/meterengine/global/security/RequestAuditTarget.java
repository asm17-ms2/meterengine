package com.meterengine.global.security;

import java.util.Map;

public record RequestAuditTarget(String id, Map<String, String> idByVariable, String mode) {
  public RequestAuditTarget {
    idByVariable = Map.copyOf(idByVariable);
  }

  public static RequestAuditTarget empty() {
    return new RequestAuditTarget(null, Map.of(), null);
  }
}
