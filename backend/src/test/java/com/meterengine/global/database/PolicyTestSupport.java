package com.meterengine.global.database;

import org.springframework.mock.env.MockEnvironment;

public final class PolicyTestSupport {
  private PolicyTestSupport() {}

  public static EnvironmentSideEffectPolicy active() {
    var environment =
        new MockEnvironment()
            .withProperty("METERENGINE_DEPLOYMENT_ENVIRONMENT", "test")
            .withProperty("METERENGINE_CUTOVER_MODE", "active");
    return new EnvironmentSideEffectPolicy(new CutoverPolicy(environment), environment);
  }
}
