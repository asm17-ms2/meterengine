package com.meterengine.global.security;

import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.InvalidRequestException;
import java.util.Collection;
import java.util.List;

public final class AccessScopes {
  public static final String PATTERN = "^(ingest|read|admin|payments)$";
  public static final List<String> ALL = List.of("ingest", "read", "admin", "payments");
  public static final List<String> FULL = List.of("admin", "payments");
  public static final List<String> READ = List.of("read");

  private AccessScopes() {}

  public static List<String> normalize(List<String> scopes) {
    if (scopes == null
        || scopes.isEmpty()
        || scopes.stream().anyMatch(scope -> scope == null || !ALL.contains(scope)))
      throw new InvalidRequestException(ErrorCode.VALIDATION_ERROR);
    return scopes.stream().distinct().toList();
  }

  // admin은 ingest와 read를 포함하지만 결제 요청과 취소(payments)는 따로 받아야 한다.
  public static boolean grants(Collection<String> scopes, String scope) {
    return scope != null
        && ALL.contains(scope)
        && (scopes.contains(scope) || (scopes.contains("admin") && !"payments".equals(scope)));
  }
}
