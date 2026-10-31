package com.meterengine;

import com.meterengine.global.database.DatabaseScopes;
import com.meterengine.global.database.SideEffectEnvironmentTestConfig;
import java.util.function.Supplier;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = SideEffectEnvironmentTestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.docker.compose.enabled=false",
      "meterengine.scheduling.enabled=false",
      "meterengine.webhook.enabled=false",
      "meterengine.webhook.allow-private-network=true",
      "METERENGINE_DEPLOYMENT_ENVIRONMENT=test",
      "METERENGINE_CUTOVER_MODE=active",
      "crypto.encryption-key=AQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQE="
    })
public abstract class SideEffectTestSupport extends MeteringEnvironmentTestSupport {
  protected <T> T in(String mode, Supplier<T> action) {
    return DatabaseScopes.call(scope(mode), action);
  }

  protected void in(String mode, Runnable action) {
    in(
        mode,
        () -> {
          action.run();
          return null;
        });
  }
}
