package com.meterengine;

import com.meterengine.global.database.MeteringEnvironmentTestConfig;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
    classes = MeteringEnvironmentTestConfig.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.docker.compose.enabled=false",
      "spring.jpa.open-in-view=false",
      "METERENGINE_DEPLOYMENT_ENVIRONMENT=test",
      "METERENGINE_CUTOVER_MODE=active",
      "meterengine.auth-rate-limit.enabled=true",
      "meterengine.auth-rate-limit.login-ip-limit=100",
      "meterengine.auth-rate-limit.login-account-limit=10"
    })
public abstract class MeteringEnvironmentTestSupport extends EnvironmentTestSupport {
  protected static com.meterengine.global.database.DatabaseScope scope(String mode) {
    return com.meterengine.global.database.DatabaseScope.valueOf(
        mode.toUpperCase(java.util.Locale.ROOT));
  }

  protected void createCustomer(String mode, java.util.UUID customerId, String externalId) {
    transactions.write(
        scope(mode),
        () -> {
          jdbc.update(
              "INSERT INTO customer(id,organization_id,name,external_id) VALUES (?,?,'같은 고객',?)",
              customerId,
              organizationId,
              externalId);
          return null;
        });
  }

  protected java.net.http.HttpResponse<String> createMetric(
      String mode, String code, String aggregation) {
    return request(
        "POST",
        "/v1/billable-metrics",
        session,
        mode,
        "{\"code\":\""
            + code
            + "\",\"name\":\"같은 미터\",\"event_type\":\"tokens\",\"aggregation\":\""
            + aggregation
            + "\""
            + (aggregation.equals("count") ? "" : ",\"target_property\":\"quantity\"")
            + "}");
  }

  protected String eventJson(String transactionId, java.util.UUID customerId, String properties) {
    return "{\"transaction_id\":\""
        + transactionId
        + "\",\"customer_id\":\""
        + customerId
        + "\",\"type\":\"tokens\",\"properties\":"
        + properties
        + ",\"timestamp\":\""
        + java.time.OffsetDateTime.now().minusSeconds(10)
        + "\"}";
  }

  protected void pastEvent(
      String mode,
      String id,
      java.util.UUID customerId,
      String type,
      String properties,
      java.time.OffsetDateTime occurredAt,
      java.time.OffsetDateTime receivedAt) {
    var owner = ownerJdbc();
    String table = scope(mode).name().toLowerCase(java.util.Locale.ROOT) + ".event";
    owner.execute("ALTER TABLE " + table + " DISABLE TRIGGER event_set_received_at");
    try {
      owner.update(
          "INSERT INTO "
              + table
              + "(organization_id,transaction_id,customer_id,type,properties,occurred_at,received_at) VALUES (?,?,?,?,?::jsonb,?,?)",
          organizationId,
          id,
          customerId,
          type,
          properties,
          occurredAt,
          receivedAt);
    } finally {
      owner.execute("ALTER TABLE " + table + " ENABLE TRIGGER event_set_received_at");
    }
  }
}
