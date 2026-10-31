package com.meterengine.global.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.postgresql.PostgreSQLContainer;

class SchemaMigratorStandaloneCommandIntegrationTest {

  @TempDir Path directory;

  @Test
  void 실제_TLS_owner_명령은_Spring과_예약_작업_없이_종료하고_원본_DB를_보존한다() throws Exception {
    Path key = directory.resolve("server.key");
    Path certificate = directory.resolve("server.crt");
    var openssl =
        new ProcessBuilder(
                "openssl",
                "req",
                "-x509",
                "-newkey",
                "rsa:2048",
                "-nodes",
                "-keyout",
                key.toString(),
                "-out",
                certificate.toString(),
                "-days",
                "1",
                "-subj",
                "/CN=localhost",
                "-addext",
                "subjectAltName=DNS:localhost,IP:127.0.0.1")
            .redirectErrorStream(true)
            .redirectOutput(directory.resolve("openssl.log").toFile())
            .start();
    assertThat(openssl.waitFor(30, TimeUnit.SECONDS)).isTrue();
    assertThat(openssl.exitValue()).isZero();
    String script =
        "#!/bin/sh\nset -eu\ncat > \"$PGDATA/server.crt\" <<'CERT'\n"
            + Files.readString(certificate)
            + "CERT\ncat > \"$PGDATA/server.key\" <<'KEY'\n"
            + Files.readString(key)
            + "KEY\n"
            + "chmod 0600 \"$PGDATA/server.key\"\n"
            + "printf '\\n"
            + "ssl=on\\n"
            + "' >> \"$PGDATA/postgresql.conf\"\n";
    try (var postgres =
        new PostgreSQLContainer("postgres:18.4-alpine")
            .withDatabaseName("postgres")
            .withUsername("meterengine_owner")
            .withPassword("owner-fixture-secret-7391")
            .withCopyToContainer(
                Transferable.of(script, 0755), "/docker-entrypoint-initdb.d/00-ssl.sh")) {
      postgres.start();
      var env = new HashMap<String, String>();
      env.put("DB_HOST", postgres.getHost());
      env.put("DB_PORT", postgres.getMappedPort(5432).toString());
      env.put("DB_NAME", "meterengine_app");
      env.put("DB_MIGRATION_USERNAME", postgres.getUsername());
      env.put("DB_MIGRATION_PASSWORD", postgres.getPassword());
      env.put("DB_SSL_ROOT_CERT", certificate.toString());
      env.put("DB_TARGET_IDENTIFIER", "tls-fixture-staging");
      env.put("OPERATOR_EMAIL", "must-not-bootstrap@example.invalid");
      env.put("OPERATOR_PASSWORD", "bootstrap-fixture-secret");
      for (var scope : new String[] {"SHARED", "TEST", "LIVE"}) {
        env.put(
            "DB_" + scope + "_USERNAME", "meterengine_" + scope.toLowerCase(java.util.Locale.ROOT));
        env.put("DB_" + scope + "_PASSWORD", "runtime-fixture-secret-" + scope);
      }
      try (var c =
          DriverManager.getConnection(
              postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
        c.createStatement().execute("CREATE DATABASE meterengine");
      }
      try (var c =
          DriverManager.getConnection(
              postgres.getJdbcUrl().replace("/postgres", "/meterengine"),
              postgres.getUsername(),
              postgres.getPassword())) {
        c.createStatement().execute("CREATE TABLE public.original(value text)");
        c.createStatement().execute("INSERT INTO public.original VALUES ('preserve')");
      }
      run(env, true, "--bootstrap-database");
      run(env, true, "--bootstrap-database");
      run(env, true, "--migrate-only");
      run(env, true, "--migrate-only");
      run(env, true, "--activate-target", "--fresh-target");
      try (var c = MigrationSettings.from(env).dataSource("meterengine_app").getConnection()) {
        assertThat(
                SchemaMigratorInitialSchemaIntegrationTest.scalar(
                    c, "SELECT ssl FROM pg_stat_ssl WHERE pid=pg_backend_pid()"))
            .isEqualTo("t");
        assertThat(
                SchemaMigratorInitialSchemaIntegrationTest.scalar(
                    c,
                    "SELECT count(*) FROM shared.flyway_schema_history WHERE type='SQL' AND"
                        + " success"))
            .isEqualTo("3");
        assertThat(
                SchemaMigratorInitialSchemaIntegrationTest.scalar(
                    c, "SELECT count(*) FROM shared.legacy_import"))
            .isEqualTo("0");
        assertThat(
                SchemaMigratorInitialSchemaIntegrationTest.scalar(
                    c, "SELECT count(*) FROM shared.user_account"))
            .isEqualTo("0");
      }
      env.put("DB_NAME", "meterengine");
      run(env, false, "--migrate-only");
      try (var c =
          DriverManager.getConnection(
              postgres.getJdbcUrl().replace("/postgres", "/meterengine"),
              postgres.getUsername(),
              postgres.getPassword())) {
        assertThat(
                SchemaMigratorInitialSchemaIntegrationTest.scalar(
                    c, "SELECT value FROM public.original"))
            .isEqualTo("preserve");
      }
    }
  }

  private void run(Map<String, String> env, boolean success, String... flags) throws Exception {
    var classpath = new ArrayList<String>();
    for (ClassLoader loader = getClass().getClassLoader();
        loader != null;
        loader = loader.getParent()) {
      if (loader instanceof URLClassLoader urls)
        for (var url : urls.getURLs())
          if (url.getProtocol().equals("file")) classpath.add(Path.of(url.toURI()).toString());
    }
    var roots = getClass().getClassLoader().getResources("");
    while (roots.hasMoreElements()) classpath.add(Path.of(roots.nextElement().toURI()).toString());
    var manifests = getClass().getClassLoader().getResources("META-INF/MANIFEST.MF");
    while (manifests.hasMoreElements()) {
      var url = manifests.nextElement();
      if (url.openConnection() instanceof java.net.JarURLConnection jar)
        classpath.add(Path.of(jar.getJarFileURL().toURI()).toString());
    }
    var command = new ArrayList<String>();
    command.add(Path.of(System.getProperty("java.home"), "bin", "java").toString());
    command.add("-cp");
    command.add(String.join(java.io.File.pathSeparator, classpath));
    command.add("com.meterengine.MeterEngineApplication");
    command.addAll(java.util.List.of(flags));
    Path output = Files.createTempFile(directory, "standalone-", ".log");
    var builder =
        new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(output.toFile());
    builder
        .environment()
        .keySet()
        .removeIf(
            name ->
                name.startsWith("DB_")
                    || name.startsWith("SPRING_")
                    || name.startsWith("OPERATOR_"));
    builder.environment().putAll(env);
    var process = builder.start();
    boolean finished = process.waitFor(45, TimeUnit.SECONDS);
    if (!finished) process.destroyForcibly();
    assertThat(finished).isTrue();
    String log = Files.readString(output);
    assertThat(log)
        .doesNotContain(
            "Spring Boot",
            "Started MeterEngineApplication",
            "HikariPool",
            "owner-fixture-secret",
            "runtime-fixture-secret",
            "bootstrap-fixture-secret");
    if (success) {
      assertThat(process.exitValue()).withFailMessage(log).isZero();
      assertThat(log).contains("Standalone database command completed");
    } else assertThat(process.exitValue()).isNotZero();
  }
}
