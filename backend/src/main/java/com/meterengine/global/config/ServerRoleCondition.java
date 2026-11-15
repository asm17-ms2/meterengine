package com.meterengine.global.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

class ServerRoleCondition implements Condition {

  private static final String DEFAULT_ROLE = "all";

  // 모르는 값은 valueOf가 던져 서버가 뜨지 않는다. 오타로 역할이 조용히 바뀌는 것을 막는다.
  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    Map<String, Object> attributes =
        metadata.getAnnotationAttributes(ConditionalOnServerRole.class.getName());
    ServerRole[] allowed = (ServerRole[]) attributes.get("value");
    String configured = context.getEnvironment().getProperty(ServerRole.PROPERTY, DEFAULT_ROLE);
    ServerRole current = ServerRole.valueOf(configured.trim().toUpperCase(Locale.ROOT));
    return Arrays.asList(allowed).contains(current);
  }
}
