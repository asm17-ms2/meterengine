package com.meterengine.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meterengine.global.database.DatabaseScope;
import com.meterengine.global.database.DatabaseScopes;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class PaymentMetricsScopeTest {
  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final PaymentMetrics metrics = new PaymentMetrics(registry);
  private final OffsetDateTime oldest = OffsetDateTime.parse("2026-10-30T10:00:00Z");

  @Test
  void mode별_게이지는_반대_scope의_마지막_측정값을_보존한다() {
    update(DatabaseScope.TEST, "test", 1);
    update(DatabaseScope.LIVE, "test", 0);
    assertThat(count("test")).isEqualTo(1);
    update(DatabaseScope.TEST, "test", 0);
    update(DatabaseScope.LIVE, "test", 2);
    assertThat(count("test")).isEqualTo(2);
    update(DatabaseScope.TEST, "test", 3);
    assertThat(count("test")).isEqualTo(5);
    update(DatabaseScope.LIVE, "test", 1);
    assertThat(count("test")).isEqualTo(4);
    assertThat(count("live")).isZero();
    assertThat(DatabaseScopes.current()).isEqualTo(DatabaseScope.SHARED);
  }

  @Test
  void 가장_오래된_요청은_각_scope의_시각을_합쳐_가장_이른_값을_낸다() {
    in(DatabaseScope.TEST, () -> metrics.setStalePendingOldestRequestedAt("test", oldest));
    in(
        DatabaseScope.LIVE,
        () -> metrics.setStalePendingOldestRequestedAt("test", oldest.plusHours(1)));
    assertThat(oldest("test")).isEqualTo(oldest.toEpochSecond());
    in(DatabaseScope.TEST, () -> metrics.clearStalePendingOldestRequestedAt("test"));
    assertThat(oldest("test")).isEqualTo(oldest.plusHours(1).toEpochSecond());
    in(DatabaseScope.LIVE, () -> metrics.clearStalePendingOldestRequestedAt("test"));
    assertThat(oldest("test")).isZero();
  }

  @Test
  void SHARED에서는_건수와_최초시각을_업무_scope에_대신_기록하지_않는다() {
    assertThatThrownBy(() -> metrics.setStalePending("test", 7))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> metrics.setStalePendingOldestRequestedAt("test", oldest))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> metrics.clearStalePendingOldestRequestedAt("test"))
        .isInstanceOf(IllegalStateException.class);
    assertThat(count("test")).isZero();
    assertThat(oldest("test")).isZero();
  }

  private void update(DatabaseScope scope, String mode, long count) {
    in(scope, () -> metrics.setStalePending(mode, count));
  }

  private static void in(DatabaseScope scope, Runnable action) {
    DatabaseScopes.call(
        scope,
        () -> {
          action.run();
          return null;
        });
  }

  private double count(String mode) {
    return registry.get("meterengine.payment.stale.pending").tag("mode", mode).gauge().value();
  }

  private double oldest(String mode) {
    return registry
        .get("meterengine.payment.stale.pending.oldest.requested")
        .tag("mode", mode)
        .gauge()
        .value();
  }
}
