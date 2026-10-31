package com.meterengine.global.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.DriverManager;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class DatabaseBootstrapIntegrationTest {

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:18.4-alpine").withDatabaseName("postgres");

  @BeforeEach
  @AfterEach
  void removeOwnedTarget() throws Exception {
    try (var c =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      c.createStatement().execute("DROP DATABASE IF EXISTS meterengine_app");
      for (var role : new String[] {"meterengine_shared", "meterengine_test", "meterengine_live"})
        c.createStatement().execute("DROP ROLE IF EXISTS " + role);
    }
  }

  static Map<String, String> environment() {
    var env = new HashMap<String, String>();
    env.put("DB_HOST", POSTGRES.getHost());
    env.put("DB_PORT", POSTGRES.getMappedPort(5432).toString());
    env.put("DB_NAME", "meterengine_app");
    env.put("DB_MIGRATION_USERNAME", POSTGRES.getUsername());
    env.put("DB_MIGRATION_PASSWORD", POSTGRES.getPassword());
    env.put("DB_SSL_ROOT_CERT", "/fixture/ca.pem");
    for (var scope : new String[] {"SHARED", "TEST", "LIVE"}) {
      env.put("DB_" + scope + "_USERNAME", "meterengine_" + scope.toLowerCase());
      env.put("DB_" + scope + "_PASSWORD", "fixture-'\\-" + scope);
    }
    return env;
  }

  @Test
  void 빈_대상만_생성하고_다시_실행하면_DB와_역할을_변경하지_않는다() throws Exception {
    var settings = MigrationSettings.from(environment());
    var targetSource =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl().replace("/postgres", "/meterengine_app"),
            POSTGRES.getUsername(),
            POSTGRES.getPassword());
    try (var admin =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      assertThat(DatabaseBootstrap.run(admin, targetSource, settings, environment())).isTrue();
      assertThat(DatabaseBootstrap.run(admin, targetSource, settings, environment())).isFalse();
      var changed = environment();
      changed.put("DB_SHARED_PASSWORD", "must-not-rotate");
      assertThat(DatabaseBootstrap.run(admin, targetSource, settings, changed)).isFalse();
      try (var runtime =
          DriverManager.getConnection(
              targetSource.getUrl(),
              "meterengine_shared",
              environment().get("DB_SHARED_PASSWORD"))) {
        assertThat(
                SchemaMigratorInitialSchemaIntegrationTest.scalar(runtime, "SELECT session_user"))
            .isEqualTo("meterengine_shared");
      }
      try (var rs =
          admin
              .createStatement()
              .executeQuery(
                  "SELECT count(*) FROM pg_roles WHERE rolname IN"
                      + " ('meterengine_shared','meterengine_test','meterengine_live') AND"
                      + " rolcanlogin AND NOT rolsuper AND NOT rolcreatedb AND NOT rolcreaterole"
                      + " AND NOT rolreplication AND NOT rolbypassrls AND NOT rolinherit")) {
        rs.next();
        assertThat(rs.getInt(1)).isEqualTo(3);
      }
    }
  }

  @Test
  void 원본_DB와_owner를_runtime으로_쓰는_설정을_접속_전에_거절한다() {
    var env = environment();
    env.put("DB_NAME", "meterengine");
    assertThatThrownBy(() -> MigrationSettings.from(env))
        .isInstanceOf(IllegalArgumentException.class);
    env.put("DB_NAME", "meterengine_app");
    env.put("DB_MIGRATION_USERNAME", "meterengine_test");
    assertThatThrownBy(() -> MigrationSettings.from(env))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void 소유자_설정의_문자열에는_비밀번호가_없다() {
    assertThat(MigrationSettings.from(environment()).toString())
        .doesNotContain(POSTGRES.getPassword());
  }

  @Test
  void 사용자_자료나_사용자_자료형이_있는_대상은_변경하지_않는다() throws Exception {
    var settings = MigrationSettings.from(environment());
    var target =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl().replace("/postgres", "/meterengine_app"),
            POSTGRES.getUsername(),
            POSTGRES.getPassword());
    try (var admin =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      DatabaseBootstrap.run(admin, target, settings, environment());
      try (var c = target.getConnection()) {
        c.createStatement().execute("CREATE TYPE public.user_data_type AS ENUM ('preserve')");
        try {
          assertThatThrownBy(() -> DatabaseBootstrap.run(admin, target, settings, environment()))
              .isInstanceOf(IllegalArgumentException.class);
          assertThat(
                  SchemaMigratorInitialSchemaIntegrationTest.scalar(
                      c, "SELECT 'preserve'::public.user_data_type"))
              .isEqualTo("preserve");
        } finally {
          c.createStatement().execute("DROP TYPE public.user_data_type");
        }
        c.createStatement().execute("CREATE TYPE public.unexpected_composite AS (value text)");
        try {
          assertThatThrownBy(() -> DatabaseBootstrap.run(admin, target, settings, environment()))
              .isInstanceOf(IllegalArgumentException.class);
          assertThat(
                  SchemaMigratorInitialSchemaIntegrationTest.scalar(
                      c, "SELECT to_regtype('public.unexpected_composite') IS NOT NULL"))
              .isEqualTo("t");
        } finally {
          c.createStatement().execute("DROP TYPE public.unexpected_composite");
        }
        c.createStatement().execute("CREATE TABLE public.user_data(value text)");
        c.createStatement().execute("INSERT INTO public.user_data VALUES ('preserve')");
        try {
          assertThatThrownBy(() -> DatabaseBootstrap.run(admin, target, settings, environment()))
              .isInstanceOf(IllegalArgumentException.class);
          assertThat(
                  SchemaMigratorInitialSchemaIntegrationTest.scalar(
                      c, "SELECT value FROM public.user_data"))
              .isEqualTo("preserve");
        } finally {
          c.createStatement().execute("DROP TABLE public.user_data");
        }
      }
    }
  }

  @Test
  void 사용자_표가_있는_DB에는_새_마이그레이션_이력을_만들지_않는다() throws Exception {
    var target =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl().replace("/postgres", "/meterengine_app"),
            POSTGRES.getUsername(),
            POSTGRES.getPassword());
    try (var admin =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      DatabaseBootstrap.run(admin, target, MigrationSettings.from(environment()), environment());
    }
    try (var c = target.getConnection()) {
      c.createStatement().execute("CREATE TABLE public.user_data(value text)");
      c.createStatement().execute("INSERT INTO public.user_data VALUES ('preserve')");
      assertThatThrownBy(() -> new SchemaMigrator(target).migrate())
          .isInstanceOf(IllegalArgumentException.class);
      assertThat(
              SchemaMigratorInitialSchemaIntegrationTest.scalar(
                  c, "SELECT value FROM public.user_data"))
          .isEqualTo("preserve");
      assertThat(
              SchemaMigratorInitialSchemaIntegrationTest.scalar(
                  c, "SELECT to_regclass('shared.flyway_schema_history') IS NULL"))
          .isEqualTo("t");
    }
  }

  @Test
  void RDS가_이미_만든_빈_target에는_없는_역할과_경계만_준비한다() throws Exception {
    var target =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl().replace("/postgres", "/meterengine_app"),
            POSTGRES.getUsername(),
            POSTGRES.getPassword());
    try (var admin =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      admin.createStatement().execute("CREATE DATABASE meterengine_app");
      assertThat(
              DatabaseBootstrap.run(
                  admin, target, MigrationSettings.from(environment()), environment()))
          .isTrue();
      assertThat(
              DatabaseBootstrap.run(
                  admin, target, MigrationSettings.from(environment()), environment()))
          .isFalse();
      try (var c =
          DriverManager.getConnection(
              target.getUrl(), "meterengine_test", environment().get("DB_TEST_PASSWORD"))) {
        SchemaMigratorInitialSchemaIntegrationTest.denied(c, "CREATE TEMP TABLE forbidden(id int)");
      }
    }
  }

  @Test
  void runtime에_이미_DDL_권한이_있으면_bootstrap과_migrate를_거절한다() throws Exception {
    var target =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl().replace("/postgres", "/meterengine_app"),
            POSTGRES.getUsername(),
            POSTGRES.getPassword());
    try (var admin =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      DatabaseBootstrap.run(admin, target, MigrationSettings.from(environment()), environment());
      admin
          .createStatement()
          .execute("GRANT CREATE ON DATABASE meterengine_app TO meterengine_test");
      assertThatThrownBy(
              () ->
                  DatabaseBootstrap.run(
                      admin, target, MigrationSettings.from(environment()), environment()))
          .isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> new SchemaMigrator(target).migrate())
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  @Test
  void 기존_빈_target의_충돌_default_ACL은_bootstrap_쓰기에_앞서_거절한다() throws Exception {
    var target =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl().replace("/postgres", "/meterengine_app"),
            POSTGRES.getUsername(),
            POSTGRES.getPassword());
    try (var admin =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      admin.createStatement().execute("CREATE DATABASE meterengine_app");
      admin
          .createStatement()
          .execute("CREATE ROLE meterengine_test LOGIN NOINHERIT PASSWORD 'fixture-password'");
      String databaseAcl =
          SchemaMigratorInitialSchemaIntegrationTest.scalar(
              admin, "SELECT datacl::text FROM pg_database WHERE datname='meterengine_app'");
      try (var c = target.getConnection()) {
        c.createStatement()
            .execute("ALTER DEFAULT PRIVILEGES GRANT ALL ON TABLES TO meterengine_test");
        String defaultAcl =
            SchemaMigratorInitialSchemaIntegrationTest.scalar(
                c,
                "SELECT defaclacl::text FROM pg_default_acl WHERE defaclrole=current_user::regrole"
                    + " AND defaclobjtype='r'");
        assertThatThrownBy(
                () ->
                    DatabaseBootstrap.run(
                        admin, target, MigrationSettings.from(environment()), environment()))
            .isInstanceOf(IllegalArgumentException.class);
        assertThat(
                SchemaMigratorInitialSchemaIntegrationTest.scalar(
                    admin,
                    "SELECT count(*) FROM pg_roles WHERE rolname IN"
                        + " ('meterengine_shared','meterengine_test','meterengine_live')"))
            .isEqualTo("1");
        assertThat(
                SchemaMigratorInitialSchemaIntegrationTest.scalar(
                    admin, "SELECT datacl::text FROM pg_database WHERE datname='meterengine_app'"))
            .isEqualTo(databaseAcl);
        assertThat(
                SchemaMigratorInitialSchemaIntegrationTest.scalar(
                    c,
                    "SELECT defaclacl::text FROM pg_default_acl WHERE"
                        + " defaclrole=current_user::regrole AND defaclobjtype='r'"))
            .isEqualTo(defaultAcl);
        assertThat(
                SchemaMigratorInitialSchemaIntegrationTest.scalar(
                    c, "SELECT to_regclass('shared.flyway_schema_history') IS NULL"))
            .isEqualTo("t");
      }
    }
  }

  @Test
  void 충돌_default_ACL은_migrate_쓰기에_앞서_거절하고_초기_로그인_권한을_보존한다() throws Exception {
    var target =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl().replace("/postgres", "/meterengine_app"),
            POSTGRES.getUsername(),
            POSTGRES.getPassword());
    try (var admin =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      DatabaseBootstrap.run(admin, target, MigrationSettings.from(environment()), environment());
    }
    try (var c = target.getConnection()) {
      c.createStatement()
          .execute("ALTER DEFAULT PRIVILEGES GRANT ALL ON TABLES TO meterengine_test");
      String defaultAcl =
          SchemaMigratorInitialSchemaIntegrationTest.scalar(
              c,
              "SELECT defaclacl::text FROM pg_default_acl WHERE defaclrole=current_user::regrole"
                  + " AND defaclobjtype='r'");
      assertThatThrownBy(() -> new SchemaMigrator(target).migrate())
          .isInstanceOf(IllegalArgumentException.class);
      assertThat(
              SchemaMigratorInitialSchemaIntegrationTest.scalar(
                  c, "SELECT to_regclass('shared.flyway_schema_history') IS NULL"))
          .isEqualTo("t");
      assertThat(
              SchemaMigratorInitialSchemaIntegrationTest.scalar(
                  c,
                  "SELECT defaclacl::text FROM pg_default_acl WHERE"
                      + " defaclrole=current_user::regrole AND defaclobjtype='r'"))
          .isEqualTo(defaultAcl);
      c.createStatement()
          .execute("ALTER DEFAULT PRIVILEGES REVOKE ALL ON TABLES FROM meterengine_test");
    }
    var migrator = new SchemaMigrator(target);
    assertThat(migrator.migrate()).isEqualTo(3);
    assertThat(migrator.migrate()).isZero();
    try (var login =
        DriverManager.getConnection(
            target.getUrl(), "meterengine_test", environment().get("DB_TEST_PASSWORD"))) {
      SchemaMigratorInitialSchemaIntegrationTest.denied(
          login, "SELECT * FROM shared.legacy_import");
      SchemaMigratorInitialSchemaIntegrationTest.denied(
          login, "INSERT INTO shared.organization(name) VALUES ('forbidden')");
    }
    try (var c = target.getConnection()) {
      c.createStatement()
          .execute(
              "ALTER DEFAULT PRIVILEGES IN SCHEMA shared GRANT ALL ON TABLES TO meterengine_test");
      assertThatThrownBy(migrator::migrate).isInstanceOf(IllegalArgumentException.class);
      assertThatThrownBy(() -> migrator.activate(true, "fixture-staging", null))
          .isInstanceOf(IllegalArgumentException.class);
      assertThat(
              SchemaMigratorInitialSchemaIntegrationTest.scalar(
                  c,
                  "SELECT count(*) FROM shared.flyway_schema_history WHERE type='SQL' AND success"))
          .isEqualTo("3");
    }
  }

  @Test
  void owner만_남은_default_ACL과_V3의_정상_함수_회수_기록은_허용한다() throws Exception {
    var target =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl().replace("/postgres", "/meterengine_app"),
            POSTGRES.getUsername(),
            POSTGRES.getPassword());
    try (var admin =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      admin.createStatement().execute("CREATE DATABASE meterengine_app");
      try (var c = target.getConnection()) {
        c.createStatement()
            .execute("ALTER DEFAULT PRIVILEGES REVOKE TRUNCATE ON TABLES FROM CURRENT_USER");
      }
      assertThat(
              DatabaseBootstrap.run(
                  admin, target, MigrationSettings.from(environment()), environment()))
          .isTrue();
      assertThat(
              DatabaseBootstrap.run(
                  admin, target, MigrationSettings.from(environment()), environment()))
          .isFalse();
    }
    var migrator = new SchemaMigrator(target);
    assertThat(migrator.migrate()).isEqualTo(3);
    assertThat(migrator.migrate()).isZero();
    try (var c = target.getConnection()) {
      assertThat(
              SchemaMigratorInitialSchemaIntegrationTest.scalar(
                  c,
                  "SELECT count(*) FROM pg_default_acl d CROSS JOIN LATERAL aclexplode(d.defaclacl)"
                      + " a WHERE d.defaclrole=current_user::regrole AND d.defaclobjtype='f' AND"
                      + " a.grantee<>d.defaclrole"))
          .isEqualTo("0");
    }
  }
}
