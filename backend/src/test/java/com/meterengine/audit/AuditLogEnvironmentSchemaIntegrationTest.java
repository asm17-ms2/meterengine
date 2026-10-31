package com.meterengine.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.EnvironmentTestSupport;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditLogEnvironmentSchemaIntegrationTest extends EnvironmentTestSupport {
  @Test
  void 업무_감사는_선택_환경과_조직_공통_action만_보여준다() {
    var test = customer("test", "test-customer", "test-key");
    var live = customer("live", "live-customer", "live-key");
    UUID common = UUID.randomUUID();
    UUID excluded = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO shared.audit_log(id,occurred_at,organization_id,actor_type,action,target_id) VALUES (?,now(),?,'system','organization_member_created','common')",
        common,
        organizationId);
    jdbc.update(
        "INSERT INTO shared.audit_log(id,occurred_at,organization_id,actor_type,action,target_id) VALUES (?,now(),?,'system','customer_updated','unscoped-private')",
        excluded,
        organizationId);
    var listed = get("/v1/audit-logs?size=100", "test");
    assertThat(listed.statusCode()).isEqualTo(200);
    assertThat(listed.body())
        .contains(json(test).path("id").asString(), common.toString())
        .doesNotContain(json(live).path("id").asString(), excluded.toString());
    var page = get("/v1/audit-logs?action=customer_created&sort=occurred_at_asc&size=1", "test");
    assertThat(page.statusCode()).isEqualTo(200);
    assertThat(json(page).path("total").asLong()).isEqualTo(1);
    assertThat(json(page).path("audit_logs").get(0).path("target_id").asString())
        .isEqualTo(json(test).path("id").asString());
    var operations =
        get("/v1/operations/audit-logs?q=" + organizationId.toString().substring(0, 12), null);
    assertThat(operations.statusCode()).isEqualTo(200);
    assertThat(json(operations).path("total").asLong()).isGreaterThanOrEqualTo(4);
  }

  @Test
  void 감사_검색은_SQL_와일드카드를_문자_그대로_찾는다() {
    jdbc.update(
        "INSERT INTO shared.audit_log(id,occurred_at,organization_id,environment_id,actor_type,action,target_id) VALUES (?,now(),?,?,'system','customer_updated','literal_%value')",
        UUID.randomUUID(), organizationId, environmentId("test"));
    jdbc.update(
        "INSERT INTO shared.audit_log(id,occurred_at,organization_id,environment_id,actor_type,action,target_id) VALUES (?,now(),?,?,'system','customer_updated','literal_Xvalue')",
        UUID.randomUUID(),
        organizationId,
        environmentId("test"));
    var result =
        get(
            "/v1/audit-logs?q="
                + java.net.URLEncoder.encode("literal_%", java.nio.charset.StandardCharsets.UTF_8),
            "test");
    assertThat(result.statusCode()).isEqualTo(200);
    assertThat(json(result).path("total").asLong()).isEqualTo(1);
    assertThat(json(result).path("audit_logs").get(0).path("target_id").asString())
        .isEqualTo("literal_%value");
  }
}
