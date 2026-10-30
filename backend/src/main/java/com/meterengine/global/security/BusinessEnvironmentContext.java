package com.meterengine.global.security;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

public final class BusinessEnvironmentContext {
  public static final String ATTRIBUTE = BusinessEnvironment.class.getName();
  private static final UUID LEGACY_SCOPE = new UUID(0, 0);
  private static final ThreadLocal<BusinessEnvironment> TASK_ENVIRONMENT = new ThreadLocal<>();

  private BusinessEnvironmentContext() {}

  public static BusinessEnvironment current() {
    BusinessEnvironment task = TASK_ENVIRONMENT.get();
    if (task != null) return task;
    RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
    return attributes == null
        ? null
        : (BusinessEnvironment) attributes.getAttribute(ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
  }

  public static UUID environmentId() {
    BusinessEnvironment environment = current();
    return environment == null ? null : environment.environmentId();
  }

  public static UUID scopeId() {
    return scopeId(environmentId());
  }

  public static UUID scopeId(UUID environmentId) {
    return environmentId == null ? LEGACY_SCOPE : environmentId;
  }

  public static <T> T run(BusinessEnvironment environment, Supplier<T> action) {
    Objects.requireNonNull(environment);
    Objects.requireNonNull(action);
    BusinessEnvironment previous = TASK_ENVIRONMENT.get();
    TASK_ENVIRONMENT.set(environment);
    try {
      return action.get();
    } finally {
      if (previous == null) TASK_ENVIRONMENT.remove();
      else TASK_ENVIRONMENT.set(previous);
    }
  }

  public static void run(BusinessEnvironment environment, Runnable action) {
    run(
        environment,
        () -> {
          action.run();
          return null;
        });
  }

  public static String mode() {
    BusinessEnvironment environment = current();
    return environment == null ? null : environment.mode();
  }

  public static boolean matches(UUID resourceEnvironmentId) {
    return Objects.equals(environmentId(), resourceEnvironmentId);
  }
}
