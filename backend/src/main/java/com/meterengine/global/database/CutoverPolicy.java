package com.meterengine.global.database;

import java.util.Set;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public final class CutoverPolicy {
  private final boolean readOnly;
  private final String deployment;

  public CutoverPolicy(Environment environment) {
    deployment = environment.getProperty("METERENGINE_DEPLOYMENT_ENVIRONMENT");
    if (deployment == null
        || !Set.of("local", "test", "staging", "production", "sut").contains(deployment))
      throw new IllegalArgumentException("missing or invalid METERENGINE_DEPLOYMENT_ENVIRONMENT");
    String mode = environment.getProperty("METERENGINE_CUTOVER_MODE");
    if (!"active".equals(mode) && !"read_only".equals(mode))
      throw new IllegalArgumentException("missing or invalid METERENGINE_CUTOVER_MODE");
    readOnly = "read_only".equals(mode);
  }

  public boolean readOnly() {
    return readOnly;
  }

  public boolean staging() {
    return "staging".equals(deployment);
  }

  public void requireWritable() {
    if (readOnly) throw new IllegalStateException("deployment is read_only");
  }
}
