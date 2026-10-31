package com.meterengine.organization;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.EnvironmentTestSupport;
import org.junit.jupiter.api.Test;

class OrganizationEnvironmentSchemaIntegrationTest extends EnvironmentTestSupport {
  @Test
  void 두_환경은_같은_외부_ID와_재시도_키를_독립적으로_저장한다() {
    var test = customer("test", "same", "same-key");
    var live = customer("live", "same", "same-key");
    assertThat(test.statusCode()).isEqualTo(201);
    assertThat(live.statusCode()).isEqualTo(201);
    assertThat(json(test).path("id").asString()).isNotEqualTo(json(live).path("id").asString());
    for (var mode : new String[] {"test", "live"}) {
      var replay = customer(mode, "same", "same-key");
      assertThat(replay.statusCode()).isEqualTo(201);
      assertThat(replay.headers().firstValue("Idempotency-Replayed")).contains("true");
      assertThat(json(replay).path("id"))
          .isEqualTo(json(mode.equals("test") ? test : live).path("id"));
      assertThat(json(get("/v1/customers?external_id=same", mode)).path("customers").size())
          .isEqualTo(1);
      assertThat(
              request(
                      "POST",
                      "/v1/customers",
                      session,
                      mode,
                      "{\"name\":\"different\"}",
                      "Idempotency-Key",
                      "same-key")
                  .statusCode())
          .isEqualTo(409);
    }
    assertThat(get("/v1/customers/" + json(live).path("id").asString(), "test").statusCode())
        .isEqualTo(404);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM shared.audit_log WHERE organization_id=? AND action='customer_created'",
                Long.class,
                organizationId))
        .isEqualTo(2);
  }

  @Test
  void 공급자는_초기_빈_응답과_환경별_저장을_유지한다() {
    for (var mode : new String[] {"test", "live"}) {
      assertThat(get("/v1/organization/supplier", mode).statusCode()).isEqualTo(200);
      assertThat(json(get("/v1/organization/supplier", mode)).path("representative_name").isNull())
          .isTrue();
      assertThat(
              request(
                      "PUT",
                      "/v1/organization/supplier",
                      session,
                      mode,
                      "{\"business_registration_number\":\"1234567890\",\"representative_name\":\""
                          + mode
                          + "\",\"business_address\":\"address\"}")
                  .statusCode())
          .isEqualTo(200);
    }
    for (var mode : new String[] {"test", "live"})
      assertThat(
              json(get("/v1/organization/supplier", mode)).path("representative_name").asString())
          .isEqualTo(mode);
  }

  @Test
  void 고객과_멱등_행은_감사_실패와_함께_롤백한다() {
    rejectAudit(
        "customer_created",
        () -> {
          assertThat(customer("test", "rollback", "rollback-key").statusCode()).isEqualTo(500);
          transactions.read(
              com.meterengine.global.database.DatabaseScope.TEST,
              () -> {
                assertThat(
                        jdbc.queryForObject(
                            "SELECT count(*) FROM customer WHERE organization_id=?",
                            Long.class,
                            organizationId))
                    .isZero();
                assertThat(
                        jdbc.queryForObject(
                            "SELECT count(*) FROM idempotent_request WHERE organization_id=?",
                            Long.class,
                            organizationId))
                    .isZero();
                return null;
              });
        });
    assertThat(customer("test", "rollback", "rollback-key").statusCode()).isEqualTo(201);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM shared.audit_log WHERE organization_id=? AND action='customer_created'",
                Long.class,
                organizationId))
        .isEqualTo(1);
  }

  @Test
  void 공급자_변경은_감사_실패와_함께_롤백한다() {
    rejectAudit(
        "supplier_updated",
        () ->
            assertThat(
                    request(
                            "PUT",
                            "/v1/organization/supplier",
                            session,
                            "live",
                            "{\"business_registration_number\":\"1234567890\",\"representative_name\":\"rollback\",\"business_address\":\"address\"}")
                        .statusCode())
                .isEqualTo(500));
    assertThat(json(get("/v1/organization/supplier", "live")).path("representative_name").isNull())
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM shared.audit_log WHERE organization_id=? AND action='supplier_updated'",
                Long.class,
                organizationId))
        .isZero();
  }

  @Test
  void 같은_고객_UUID도_선택_역할과_schema에서만_조회한다() {
    var id = java.util.UUID.randomUUID();
    for (var scope :
        new com.meterengine.global.database.DatabaseScope[] {
          com.meterengine.global.database.DatabaseScope.TEST,
          com.meterengine.global.database.DatabaseScope.LIVE
        }) {
      transactions.write(
          scope,
          () -> {
            jdbc.update(
                "INSERT INTO customer(id,organization_id,name,external_id) VALUES (?,?,?,?)",
                id,
                organizationId,
                scope.name(),
                "same-uuid");
            assertThat(
                    jdbc.queryForObject("SELECT current_user||':'||current_schema()", String.class))
                .isEqualTo(
                    "meterengine_"
                        + scope.name().toLowerCase(java.util.Locale.ROOT)
                        + ":"
                        + scope.name().toLowerCase(java.util.Locale.ROOT));
            System.out.println(
                "S3 customer identity "
                    + scope
                    + " "
                    + jdbc.queryForMap("SELECT current_user,current_schema(),pg_backend_pid()"));
            return null;
          });
      assertThat(
              json(get("/v1/customers/" + id, scope.name().toLowerCase(java.util.Locale.ROOT)))
                  .path("name")
                  .asString())
          .isEqualTo(scope.name());
    }
  }

  @Test
  void 업무_역할은_shared_조직_원본과_반대_업무_schema를_조회하지_못한다() {
    for (var scope :
        new com.meterengine.global.database.DatabaseScope[] {
          com.meterengine.global.database.DatabaseScope.TEST,
          com.meterengine.global.database.DatabaseScope.LIVE
        }) {
      org.assertj.core.api.Assertions.assertThatThrownBy(
              () ->
                  transactions.read(
                      scope, () -> jdbc.queryForList("SELECT * FROM shared.organization")))
          .hasStackTraceContaining("permission denied for table organization");
      String opposite =
          scope == com.meterengine.global.database.DatabaseScope.TEST ? "live" : "test";
      org.assertj.core.api.Assertions.assertThatThrownBy(
              () ->
                  transactions.read(
                      scope, () -> jdbc.queryForList("SELECT * FROM " + opposite + ".customer")))
          .hasStackTraceContaining("permission denied for schema " + opposite);
    }
  }

  @Test
  void 조직_정지와_다른_조직_고객은_노출하지_않는다() {
    var created = customer("test", "private", "private-key");
    assertThat(created.statusCode()).isEqualTo(201);
    var other = java.util.UUID.randomUUID();
    jdbc.update("INSERT INTO shared.organization(id,name) VALUES (?,'other')", other);
    jdbc.update(
        "INSERT INTO shared.organization_member(organization_id,user_account_id,created_at,role,scopes) VALUES (?,?,now(),'owner',ARRAY['admin'])",
        other,
        userId);
    jdbc.update(
        "UPDATE shared.user_session SET organization_id=? WHERE user_account_id=?", other, userId);
    assertThat(get("/v1/customers/" + json(created).path("id").asString(), "test").statusCode())
        .isEqualTo(404);
    jdbc.update("UPDATE shared.organization SET status='suspended' WHERE id=?", other);
    var denied = get("/v1/customers", "test");
    assertThat(denied.statusCode()).isEqualTo(403);
    assertThat(json(denied).path("code").asString()).isEqualTo("organization_not_active");
    assertThat(get("/v1/organization/supplier", "live").statusCode()).isEqualTo(403);
    assertThat(get("/v1/sessions/current", null).statusCode()).isEqualTo(200);
  }

  @Test
  void 실제_고객_JPA와_감사_JDBC는_같은_연결의_트랜잭션이다() {
    var service = context.getBean(com.meterengine.customer.service.CustomerService.class);
    var entityManager =
        org.springframework.orm.jpa.SharedEntityManagerCreator.createSharedEntityManager(
            context.getBean(jakarta.persistence.EntityManagerFactory.class));
    for (var scope :
        new com.meterengine.global.database.DatabaseScope[] {
          com.meterengine.global.database.DatabaseScope.TEST,
          com.meterengine.global.database.DatabaseScope.LIVE
        }) {
      var selected =
          new com.meterengine.global.security.BusinessEnvironment(
              organizationId,
              environmentId(scope.name().toLowerCase(java.util.Locale.ROOT)),
              scope.name().toLowerCase(java.util.Locale.ROOT));
      transactions.write(
          scope,
          () ->
              com.meterengine.global.security.BusinessEnvironmentContext.run(
                  selected,
                  () -> {
                    var customer = service.create(organizationId, "physical-connection", null);
                    int jpaPid =
                        ((Number)
                                entityManager
                                    .createNativeQuery("SELECT pg_backend_pid()")
                                    .getSingleResult())
                            .intValue();
                    assertThat(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class))
                        .isEqualTo(jpaPid);
                    assertThat(
                            jdbc.queryForObject(
                                "SELECT count(*) FROM shared.audit_for_scope WHERE target_id=? AND action='customer_created'",
                                Long.class,
                                customer.getId().toString()))
                        .isEqualTo(1);
                    System.out.println(
                        "S3 actual Customer JPA/audit JDBC " + scope + " same pid=" + jpaPid);
                    return null;
                  }));
    }
  }
}
