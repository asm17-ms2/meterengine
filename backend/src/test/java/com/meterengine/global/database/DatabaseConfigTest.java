package com.meterengine.global.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class DatabaseConfigTest {

  @Test
  void runtime은_세_고정_역할과_verify_full을_사용하고_비밀을_출력하지_않는다() {
    var config = new DatabaseConfig();
    for (var scope : DatabaseScope.values()) {
      var pool = config.poolConfig(environment(), scope);
      assertThat(pool.getUsername())
          .isEqualTo("meterengine_" + scope.name().toLowerCase(java.util.Locale.ROOT));
      assertThat(pool.getJdbcUrl())
          .isEqualTo("jdbc:postgresql://db.example.test:5432/meterengine_app");
      assertThat(pool.getDataSourceProperties())
          .containsEntry("sslmode", "verify-full")
          .containsEntry("sslrootcert", "/fixture/ca.pem")
          .containsEntry("socketTimeout", "30")
          .containsEntry("connectTimeout", "10")
          .containsEntry("currentSchema", scope.name().toLowerCase(java.util.Locale.ROOT));
      assertThat(pool.getConnectionInitSql()).isNull();
      assertThat(pool.toString()).doesNotContain("fixture-secret");
    }
  }

  @Test
  void 자격과_CA가_빠지거나_owner_역할과_다른_DB를_지정하면_연결_전에_거절한다() {
    var config = new DatabaseConfig();
    for (var key : settings().keySet()) {
      var values = settings();
      values.remove(key);
      var environment = new MockEnvironment();
      values.forEach(environment::setProperty);
      assertThatThrownBy(() -> config.dataSource(environment))
          .isInstanceOf(IllegalArgumentException.class);
    }
    for (var entry :
        Map.of(
                "DB_TEST_USERNAME",
                "postgres",
                "DB_NAME",
                "meterengine",
                "DB_SSL_ROOT_CERT",
                "relative-ca.pem",
                "DB_HOST",
                "db?sslmode=disable",
                "DB_PORT",
                "0")
            .entrySet()) {
      var environment = environment().withProperty(entry.getKey(), entry.getValue());
      assertThatThrownBy(() -> config.dataSource(environment))
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  static MockEnvironment environment() {
    var result = new MockEnvironment();
    settings().forEach(result::setProperty);
    return result;
  }

  static HashMap<String, String> settings() {
    var result = new HashMap<String, String>();
    result.put("METERENGINE_DEPLOYMENT_ENVIRONMENT", "test");
    result.put("METERENGINE_CUTOVER_MODE", "active");
    result.put("DB_NAME", "meterengine_app");
    result.put("DB_HOST", "db.example.test");
    result.put("DB_PORT", "5432");
    result.put("DB_SSL_ROOT_CERT", "/fixture/ca.pem");
    for (var scope : new String[] {"SHARED", "TEST", "LIVE"}) {
      result.put(
          "DB_" + scope + "_USERNAME", "meterengine_" + scope.toLowerCase(java.util.Locale.ROOT));
      result.put("DB_" + scope + "_PASSWORD", "fixture-secret");
    }
    return result;
  }
}
