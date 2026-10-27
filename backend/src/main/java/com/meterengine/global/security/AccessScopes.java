package com.meterengine.global.security;

import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.InvalidRequestException;
import java.util.Collection;
import java.util.List;

public final class AccessScopes {
  public static final String PATTERN = "^(ingest|read|admin)$";
  public static final List<String> ALL = List.of("ingest", "read", "admin");
  public static final List<String> ADMIN = List.of("admin");
  public static final List<String> READ = List.of("read");

  private AccessScopes() {}

  public static List<String> normalize(List<String> scopes) {
    if (scopes == null
        || scopes.isEmpty()
        || scopes.stream().anyMatch(scope -> scope == null || !ALL.contains(scope)))
      throw new InvalidRequestException(ErrorCode.VALIDATION_ERROR);
    return scopes.stream().distinct().toList();
  }

  public static boolean grants(Collection<String> scopes, String scope) {
    return scope != null
        && ALL.contains(scope)
        && (scopes.contains("admin") || scopes.contains(scope));
  }
}
