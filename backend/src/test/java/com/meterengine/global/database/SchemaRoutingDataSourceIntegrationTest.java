package com.meterengine.global.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meterengine.global.migration.DatabaseBootstrap;
import com.meterengine.global.migration.MigrationSettings;
import com.meterengine.global.migration.SchemaMigrator;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers
class SchemaRoutingDataSourceIntegrationTest {

  @Container
  static final PostgreSQLContainer POSTGRES =
      new PostgreSQLContainer("postgres:18.4-alpine").withDatabaseName("postgres");

  static AnnotationConfigApplicationContext context;
  static SchemaRoutingDataSource dataSource;
  static EntityManagerFactory entityManagerFactory;
  static EntityManager entityManager;
  static JdbcTemplate jdbc;
  static ScopeTransactions transactions;
  static DriverManagerDataSource owner;
  static final AtomicInteger ACQUISITIONS = new AtomicInteger();

  @BeforeAll
  static void prepareDatabase() throws Exception {
    var environment = new HashMap<String, String>();
    environment.put("DB_NAME", "meterengine_app");
    environment.put("DB_HOST", POSTGRES.getHost());
    environment.put("DB_PORT", POSTGRES.getMappedPort(5432).toString());
    environment.put("DB_SSL_ROOT_CERT", "/fixture/ca.pem");
    environment.put("DB_MIGRATION_USERNAME", POSTGRES.getUsername());
    environment.put("DB_MIGRATION_PASSWORD", POSTGRES.getPassword());
    for (var scope : new String[] {"SHARED", "TEST", "LIVE"}) {
      environment.put(
          "DB_" + scope + "_USERNAME", "meterengine_" + scope.toLowerCase(java.util.Locale.ROOT));
      environment.put("DB_" + scope + "_PASSWORD", "fixture-password");
    }
    owner =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl().replace("/postgres", "/meterengine_app"),
            POSTGRES.getUsername(),
            POSTGRES.getPassword());
    try (var c =
        DriverManager.getConnection(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
      DatabaseBootstrap.run(c, owner, MigrationSettings.from(environment), environment);
    }
    var migrator = new SchemaMigrator(owner);
    assertThat(migrator.migrate()).isEqualTo(3);
    migrator.activate(true, "s2-disposable", null);
    try (var c = owner.getConnection()) {
      c.createStatement()
          .execute("CREATE TABLE shared.scope_probe (id uuid PRIMARY KEY, value text NOT NULL)");
      c.createStatement()
          .execute("GRANT SELECT,INSERT ON shared.scope_probe TO meterengine_shared");
      for (var scope : new String[] {"test", "live"}) {
        c.createStatement()
            .execute(
                "CREATE TABLE "
                    + scope
                    + ".business_probe (id uuid PRIMARY KEY, value text NOT NULL)");
        c.createStatement()
            .execute("GRANT SELECT,INSERT ON " + scope + ".business_probe TO meterengine_" + scope);
      }
    }
    context = openContext();
    dataSource = context.getBean(SchemaRoutingDataSource.class);
    entityManagerFactory = context.getBean(EntityManagerFactory.class);
    entityManager = SharedEntityManagerCreator.createSharedEntityManager(entityManagerFactory);
    jdbc = context.getBean(JdbcTemplate.class);
    transactions = context.getBean(ScopeTransactions.class);
  }

  static AnnotationConfigApplicationContext openContext() {
    var result = new AnnotationConfigApplicationContext();
    result.registerBean(
        "dataSource", SchemaRoutingDataSource.class, SchemaRoutingDataSourceIntegrationTest::pools);
    result.register(DatabaseConfig.class, ProbeConfig.class);
    try {
      result.refresh();
      return result;
    } catch (RuntimeException error) {
      result.close();
      throw error;
    }
  }

  static SchemaRoutingDataSource pools() {
    var poolsByScope = new EnumMap<DatabaseScope, HikariDataSource>(DatabaseScope.class);
    for (var scope : DatabaseScope.values()) {
      var config = new HikariConfig();
      config.setJdbcUrl(owner.getUrl());
      config.setUsername("meterengine_" + scope.name().toLowerCase(java.util.Locale.ROOT));
      config.setPassword("fixture-password");
      config.addDataSourceProperty(
          "currentSchema", scope.name().toLowerCase(java.util.Locale.ROOT));
      config.setMaximumPoolSize(2);
      config.setMinimumIdle(0);
      config.setPoolName("s2-" + scope);
      poolsByScope.put(scope, new CountingPool(config));
    }
    return new SchemaRoutingDataSource(poolsByScope);
  }

  @AfterAll
  static void closeContext() {
    if (context != null) context.close();
  }

  @AfterEach
  void clearScope() {
    DatabaseScopes.clear();
    assertThat(TransactionSynchronizationManager.getResourceMap()).isEmpty();
  }

  @Test
  void 교대_업무_scope는_같은_UUID와_외부_ID를_서로_섞지_않는다() {
    var id = UUID.randomUUID();
    var organizationId = organization();
    for (var scope : new DatabaseScope[] {DatabaseScope.TEST, DatabaseScope.LIVE}) {
      transactions.write(
          scope,
          () -> {
            entityManager.persist(new BusinessProbe(id, scope.name()));
            jdbc.update(
                "INSERT INTO customer(id,organization_id,name,external_id) VALUES (?,?,'probe',?)",
                id,
                organizationId,
                id.toString());
            assertIdentity(scope);
            return null;
          });
    }
    for (var scope :
        new DatabaseScope[] {
          DatabaseScope.LIVE, DatabaseScope.TEST, DatabaseScope.LIVE, DatabaseScope.TEST
        }) {
      transactions.read(
          scope,
          () -> {
            assertThat(entityManager.find(BusinessProbe.class, id).value).isEqualTo(scope.name());
            assertThat(
                    jdbc.queryForObject(
                        "SELECT external_id FROM customer WHERE id=?", String.class, id))
                .isEqualTo(id.toString());
            assertIdentity(scope);
            assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
            return null;
          });
    }
  }

  @Test
  void 동시에_같은_UUID를_써도_각_역할과_schema를_유지한다() throws Exception {
    var id = UUID.randomUUID();
    try (var executor = Executors.newFixedThreadPool(2)) {
      var barrier = new java.util.concurrent.CyclicBarrier(2);
      var first = executor.submit(() -> persistAndRead(DatabaseScope.TEST, id, barrier));
      var second = executor.submit(() -> persistAndRead(DatabaseScope.LIVE, id, barrier));
      assertThat(first.get()).isEqualTo("TEST");
      assertThat(second.get()).isEqualTo("LIVE");
    }
  }

  @Test
  void JPA와_JDBC는_같은_물리_연결을_사용하고_혼합_쓰기를_함께_롤백한다() {
    for (var scope : new DatabaseScope[] {DatabaseScope.TEST, DatabaseScope.LIVE}) {
      var jpaId = UUID.randomUUID();
      var jdbcId = UUID.randomUUID();
      assertThatThrownBy(
              () ->
                  transactions.write(
                      scope,
                      () -> {
                        entityManager.persist(new BusinessProbe(jpaId, "jpa"));
                        entityManager.flush();
                        jdbc.update(
                            "INSERT INTO business_probe(id,value) VALUES (?,?)", jdbcId, "jdbc");
                        assertIdentity(scope);
                        throw new IllegalStateException("rollback-probe");
                      }))
          .hasMessage("rollback-probe");
      transactions.read(
          scope,
          () -> {
            assertThat(entityManager.find(BusinessProbe.class, jpaId)).isNull();
            assertThat(
                    jdbc.queryForObject(
                        "SELECT count(*) FROM business_probe WHERE id IN (?,?)",
                        Long.class,
                        jpaId,
                        jdbcId))
                .isZero();
            assertIdentity(scope);
            return null;
          });
    }
  }

  @Test
  void 반대_schema의_실제_customer_SELECT를_권한으로_거절한다() {
    for (var scope : new DatabaseScope[] {DatabaseScope.TEST, DatabaseScope.LIVE}) {
      var opposite = scope == DatabaseScope.TEST ? "live" : "test";
      assertThatThrownBy(
              () ->
                  transactions.read(
                      scope, () -> jdbc.queryForList("SELECT * FROM " + opposite + ".customer")))
          .hasRootCauseInstanceOf(java.sql.SQLException.class)
          .hasStackTraceContaining("permission denied for schema " + opposite);
    }
  }

  @Test
  void 트랜잭션_중_다른_scope와_clear를_SQL과_연결_획득_전에_거절한다() {
    var executions = new AtomicInteger();
    transactions.write(
        DatabaseScope.SHARED,
        () -> {
          long connections = connectionCount();
          long statements = statistics().getPrepareStatementCount();
          assertThatThrownBy(
                  () ->
                      transactions.write(
                          DatabaseScope.TEST,
                          () -> {
                            executions.incrementAndGet();
                            return jdbc.queryForObject("SELECT 1", Integer.class);
                          }))
              .isInstanceOf(IllegalStateException.class);
          assertThatThrownBy(
                  () ->
                      DatabaseScopes.call(
                          DatabaseScope.LIVE,
                          () -> {
                            executions.incrementAndGet();
                            return null;
                          }))
              .isInstanceOf(IllegalStateException.class);
          assertThat(executions).hasValue(0);
          assertThat(connectionCount()).isEqualTo(connections);
          assertThat(statistics().getPrepareStatementCount()).isEqualTo(statements);
          return null;
        });
    transactions.write(
        DatabaseScope.TEST,
        () -> {
          assertThatThrownBy(DatabaseScopes::clear).isInstanceOf(IllegalStateException.class);
          assertThat(DatabaseScopes.current()).isEqualTo(DatabaseScope.TEST);
          return null;
        });
  }

  @Test
  void 활성_트랜잭션_없는_바인딩_EntityManager도_scope_변경을_거절한다() {
    var bound = entityManagerFactory.createEntityManager();
    TransactionSynchronizationManager.bindResource(
        entityManagerFactory, new EntityManagerHolder(bound));
    try {
      long connections = connectionCount();
      assertThatThrownBy(() -> transactions.read(DatabaseScope.TEST, () -> 1))
          .isInstanceOf(IllegalStateException.class);
      assertThat(connectionCount()).isEqualTo(connections);
    } finally {
      TransactionSynchronizationManager.unbindResource(entityManagerFactory);
      bound.close();
    }
  }

  @Test
  void 같은_scope의_중첩은_새_트랜잭션이며_내부_커밋은_외부_롤백과_분리된다() {
    var outerId = UUID.randomUUID();
    var innerId = UUID.randomUUID();
    assertThatThrownBy(
            () ->
                transactions.write(
                    DatabaseScope.TEST,
                    () -> {
                      jdbc.update(
                          "INSERT INTO business_probe(id,value) VALUES (?,'outer')", outerId);
                      int outerPid = jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
                      transactions.write(
                          DatabaseScope.TEST,
                          () -> {
                            assertThat(
                                    jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class))
                                .isNotEqualTo(outerPid);
                            jdbc.update(
                                "INSERT INTO business_probe(id,value) VALUES (?,'inner')", innerId);
                            return null;
                          });
                      throw new IllegalStateException("outer-rollback");
                    }))
        .hasMessage("outer-rollback");
    transactions.read(
        DatabaseScope.TEST,
        () -> {
          assertThat(
                  jdbc.queryForObject(
                      "SELECT count(*) FROM business_probe WHERE id=?", Long.class, outerId))
              .isZero();
          assertThat(
                  jdbc.queryForObject(
                      "SELECT count(*) FROM business_probe WHERE id=?", Long.class, innerId))
              .isEqualTo(1);
          return null;
        });
  }

  @Test
  void shared_probe는_명시한_schema와_shared_역할을_사용한다() {
    var id = UUID.randomUUID();
    transactions.write(
        DatabaseScope.SHARED,
        () -> {
          entityManager.persist(new SharedProbe(id, "shared"));
          entityManager.flush();
          assertIdentity(DatabaseScope.SHARED);
          assertThat(
                  jdbc.queryForObject(
                      "SELECT value FROM shared.scope_probe WHERE id=?", String.class, id))
              .isEqualTo("shared");
          return null;
        });
  }

  @Test
  void 배치_예외와_중첩_call은_이전_scope를_복원하고_스레드를_정리한다() {
    assertThat(DatabaseScopes.current()).isEqualTo(DatabaseScope.SHARED);
    DatabaseScopes.call(
        DatabaseScope.TEST,
        () -> {
          assertThatThrownBy(
                  () ->
                      DatabaseScopes.call(
                          DatabaseScope.LIVE,
                          () -> {
                            throw new IllegalArgumentException("nested");
                          }))
              .hasMessage("nested");
          assertThat(DatabaseScopes.current()).isEqualTo(DatabaseScope.TEST);
          return null;
        });
    assertThatThrownBy(
            () ->
                transactions.write(
                    DatabaseScope.LIVE,
                    () -> {
                      throw new IllegalArgumentException("batch");
                    }))
        .hasMessage("batch");
    assertThat(DatabaseScopes.current()).isEqualTo(DatabaseScope.SHARED);
    transactions.read(
        DatabaseScope.TEST,
        () -> {
          assertIdentity(DatabaseScope.TEST);
          return null;
        });
  }

  @Test
  void 요청_필터는_헤더를_무시하고_shared로_시작하며_예외_뒤_재사용을_정리한다() throws Exception {
    var filter = new DatabaseScopeFilter();
    var request = new MockHttpServletRequest();
    request.addHeader("X-MeterEngine-Mode", "live");
    DatabaseScopes.call(
        DatabaseScope.LIVE,
        () -> {
          assertThatThrownBy(
                  () ->
                      filter.doFilter(
                          request,
                          new MockHttpServletResponse(),
                          (_, _) -> {
                            assertThat(DatabaseScopes.current()).isEqualTo(DatabaseScope.SHARED);
                            transactions.read(
                                DatabaseScope.TEST,
                                () -> {
                                  assertIdentity(DatabaseScope.TEST);
                                  return null;
                                });
                            throw new jakarta.servlet.ServletException("request-failed");
                          }))
              .isInstanceOf(jakarta.servlet.ServletException.class)
              .hasMessage("request-failed");
          assertThat(DatabaseScopes.current()).isEqualTo(DatabaseScope.SHARED);
          return null;
        });
    filter.doFilter(
        new MockHttpServletRequest(),
        new MockHttpServletResponse(),
        (_, _) -> {
          assertThat(DatabaseScopes.current()).isEqualTo(DatabaseScope.SHARED);
          transactions.read(
              DatabaseScope.LIVE,
              () -> {
                assertIdentity(DatabaseScope.LIVE);
                return null;
              });
        });
    assertThat(DatabaseScopes.current()).isEqualTo(DatabaseScope.SHARED);
  }

  @Test
  void bootstrap_연결은_호출_scope와_무관한_test_역할로_두_schema_메타데이터를_본다() {
    var provider = context.getBean(ScopeTenantConnectionProvider.class);
    DatabaseScopes.call(
        DatabaseScope.LIVE,
        () -> {
          try (var c = provider.getAnyConnection()) {
            assertThat(scalar(c, "SELECT current_user||':'||current_schema()"))
                .isEqualTo("meterengine_test:test");
            for (var schema : new String[] {"shared", "test", "live"}) {
              try (var columns =
                  c.getMetaData()
                      .getColumns(
                          null,
                          schema,
                          schema.equals("shared") ? "scope_probe" : "business_probe",
                          "value")) {
                assertThat(columns.next()).isTrue();
              }
            }
            System.out.println(
                "S2 bootstrap metadata: meterengine_test:test sees shared/test/live probe columns");
          } catch (Exception error) {
            throw new IllegalStateException(error);
          }
          return null;
        });
  }

  @Test
  void test가_정상이어도_live_필수_열이_빠지면_기동을_거절한다() throws Exception {
    try (var c = owner.getConnection()) {
      c.createStatement().execute("ALTER TABLE live.business_probe DROP COLUMN value");
    }
    try {
      assertThatCode(
              () ->
                  entityManagerFactory
                      .unwrap(SessionFactory.class)
                      .getSchemaManager()
                      .forSchema("test")
                      .validateMappedObjects())
          .doesNotThrowAnyException();
      assertThatThrownBy(SchemaRoutingDataSourceIntegrationTest::openContext)
          .isInstanceOf(org.hibernate.tool.schema.spi.SchemaManagementException.class)
          .hasMessageContaining("missing column [value]");
    } finally {
      try (var c = owner.getConnection()) {
        c.createStatement()
            .execute(
                "ALTER TABLE live.business_probe ADD COLUMN value text NOT NULL DEFAULT 'restored'");
      }
    }
  }

  static long connectionCount() {
    return ACQUISITIONS.get();
  }

  static org.hibernate.stat.Statistics statistics() {
    return entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
  }

  static UUID organization() {
    return transactions.write(
        DatabaseScope.SHARED,
        () -> {
          var id = UUID.randomUUID();
          jdbc.update("INSERT INTO organization(id,name) VALUES (?,'probe')", id);
          return id;
        });
  }

  static String persistAndRead(
      DatabaseScope scope, UUID id, java.util.concurrent.CyclicBarrier barrier) {
    return transactions.write(
        scope,
        () -> {
          entityManager.persist(new BusinessProbe(id, scope.name()));
          entityManager.flush();
          try {
            barrier.await(10, java.util.concurrent.TimeUnit.SECONDS);
          } catch (Exception error) {
            throw new IllegalStateException(error);
          }
          assertIdentity(scope);
          return jdbc.queryForObject(
              "SELECT value FROM business_probe WHERE id=?", String.class, id);
        });
  }

  static void assertIdentity(DatabaseScope scope) {
    Object[] jpa =
        (Object[])
            entityManager
                .createNativeQuery("SELECT current_user,current_schema(),pg_backend_pid()")
                .getSingleResult();
    var direct = jdbc.queryForMap("SELECT current_user,current_schema(),pg_backend_pid()");
    String schema = scope.name().toLowerCase(java.util.Locale.ROOT);
    assertThat(jpa[0]).isEqualTo("meterengine_" + schema);
    assertThat(jpa[1]).isEqualTo(schema);
    assertThat(jpa[2]).isEqualTo(direct.get("pg_backend_pid"));
    assertThat(direct.get("current_user")).isEqualTo(jpa[0]);
    assertThat(direct.get("current_schema")).isEqualTo(jpa[1]);
    System.out.println(
        "S2 identity: "
            + scope
            + " role="
            + jpa[0]
            + " schema="
            + jpa[1]
            + " JPA/JDBC pid="
            + jpa[2]);
  }

  static String scalar(Connection c, String sql) throws Exception {
    try (var statement = c.createStatement();
        var result = statement.executeQuery(sql)) {
      result.next();
      return result.getString(1);
    }
  }

  static class CountingPool extends HikariDataSource {
    CountingPool(HikariConfig config) {
      super(config);
    }

    @Override
    public Connection getConnection() throws java.sql.SQLException {
      ACQUISITIONS.incrementAndGet();
      return super.getConnection();
    }
  }

  @Configuration(proxyBeanMethods = false)
  static class ProbeConfig {
    @Bean
    LocalContainerEntityManagerFactoryBean entityManagerFactory(
        SchemaRoutingDataSource dataSource,
        org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer customizer) {
      var factory = new LocalContainerEntityManagerFactoryBean();
      factory.setDataSource(dataSource);
      factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
      factory.setManagedTypes(
          org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes.of(
              BusinessProbe.class.getName(), SharedProbe.class.getName()));
      var properties = new HashMap<String, Object>();
      properties.put("hibernate.hbm2ddl.auto", "none");
      properties.put("hibernate.generate_statistics", true);
      customizer.customize(properties);
      factory.setJpaPropertyMap(properties);
      return factory;
    }
  }

  @Entity(name = "BusinessProbe")
  @Table(name = "business_probe")
  static class BusinessProbe {
    @Id UUID id;
    String value;

    protected BusinessProbe() {}

    BusinessProbe(UUID id, String value) {
      this.id = id;
      this.value = value;
    }
  }

  @Entity(name = "SharedProbe")
  @Table(name = "scope_probe", schema = "shared")
  static class SharedProbe {
    @Id UUID id;
    String value;

    protected SharedProbe() {}

    SharedProbe(UUID id, String value) {
      this.id = id;
      this.value = value;
    }
  }
}
