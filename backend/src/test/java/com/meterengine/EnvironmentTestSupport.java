package com.meterengine;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.global.database.DatabaseScopes;
import com.meterengine.global.database.EnvironmentTestConfig;
import com.meterengine.global.security.SecureTokens;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(
    classes = EnvironmentTestConfig.class,
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
public abstract class EnvironmentTestSupport {
  @LocalServerPort protected int port;
  @Autowired protected JdbcTemplate jdbc;
  @Autowired protected JsonMapper mapper;
  @Autowired protected PasswordEncoder passwordEncoder;
  @Autowired protected org.springframework.context.ApplicationContext context;
  @Autowired protected com.meterengine.global.database.ScopeTransactions transactions;
  @Autowired protected org.testcontainers.postgresql.PostgreSQLContainer environmentPostgres;
  protected UUID organizationId;
  protected UUID userId;
  protected String session;
  protected static final String HEADER = "X-MeterEngine-Environment";
  private final HttpClient client = HttpClient.newHttpClient();

  @BeforeEach
  void prepareIdentity() {
    organizationId = UUID.randomUUID();
    userId = UUID.randomUUID();
    jdbc.update("INSERT INTO shared.organization(id,name) VALUES (?,'S3 fixture')", organizationId);
    jdbc.update(
        "INSERT INTO shared.user_account(id,email,password_hash,name,role,created_at) VALUES (?,?,?,'fixture','operator',now())",
        userId,
        userId + "@example.com",
        passwordEncoder.encode("fixture-password"));
    jdbc.update(
        "INSERT INTO shared.organization_member(organization_id,user_account_id,created_at,role,scopes) VALUES (?,?,now(),'owner',ARRAY['admin'])",
        organizationId,
        userId);
    session = issueSession(userId);
  }

  @AfterEach
  void checkScope() {
    assertThat(DatabaseScopes.current())
        .isEqualTo(com.meterengine.global.database.DatabaseScope.SHARED);
  }

  protected String issueSession(UUID user) {
    String token = SecureTokens.generate(SecureTokens.SESSION_TOKEN_PREFIX);
    jdbc.update(
        "INSERT INTO shared.user_session(id,user_account_id,token_hash,organization_id,created_at,expires_at) VALUES (?,?,?,?,now(),?)",
        UUID.randomUUID(),
        user,
        SecureTokens.hash(token),
        organizationId,
        OffsetDateTime.now().plusHours(1));
    return "Bearer " + token;
  }

  protected String issueKey(String mode, String scope, boolean hasEnvironment, Integer limit) {
    String key = SecureTokens.generate("me_" + mode + "_");
    jdbc.update(
        "INSERT INTO shared.api_key(id,organization_id,mode,label,key_hash,last_four,scopes,created_at,environment_id,rate_limit_per_minute) VALUES (?,?,?,'fixture',?,?,ARRAY[?]::varchar[],now(),?,?)",
        UUID.randomUUID(),
        organizationId,
        mode,
        SecureTokens.hash(key),
        SecureTokens.lastFour(key),
        scope,
        hasEnvironment ? environmentId(mode) : null,
        limit);
    return "Bearer " + key;
  }

  protected UUID environmentId(String mode) {
    return jdbc.queryForObject(
        "SELECT id FROM shared.organization_environment WHERE organization_id=? AND mode=?",
        UUID.class,
        organizationId,
        mode);
  }

  protected HttpResponse<String> request(
      String method,
      String path,
      String authorization,
      String mode,
      String payload,
      String... extraHeaders) {
    try {
      var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
      if (authorization != null) builder.header("Authorization", authorization);
      if (mode != null) builder.header(HEADER, mode);
      for (int index = 0; index < extraHeaders.length; index += 2)
        builder.header(extraHeaders[index], extraHeaders[index + 1]);
      if (payload != null) builder.header("Content-Type", "application/json");
      return client.send(
          builder
              .method(
                  method,
                  payload == null
                      ? HttpRequest.BodyPublishers.noBody()
                      : HttpRequest.BodyPublishers.ofString(payload))
              .build(),
          HttpResponse.BodyHandlers.ofString());
    } catch (Exception exception) {
      throw new IllegalStateException(exception);
    }
  }

  protected JsonNode json(HttpResponse<String> response) {
    return mapper.readTree(response.body());
  }

  protected HttpResponse<String> get(String path, String mode) {
    return request("GET", path, session, mode, null);
  }

  protected HttpResponse<String> customer(String mode, String externalId, String key) {
    return request(
        "POST",
        "/v1/customers",
        session,
        mode,
        "{\"name\":\"customer\",\"external_id\":\""
            + externalId
            + "\",\"billing_email\":\"private@example.com\"}",
        "Idempotency-Key",
        key);
  }

  protected org.springframework.jdbc.core.JdbcTemplate ownerJdbc() {
    return new org.springframework.jdbc.core.JdbcTemplate(
        new org.springframework.jdbc.datasource.DriverManagerDataSource(
            environmentPostgres.getJdbcUrl().replace("/postgres", "/meterengine_app"),
            environmentPostgres.getUsername(),
            environmentPostgres.getPassword()));
  }

  protected void rejectAudit(String action, Runnable assertion) {
    var owner = ownerJdbc();
    owner.execute(
        "CREATE FUNCTION shared.s3_reject_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.action = TG_ARGV[0] THEN RAISE EXCEPTION 's3-audit-failure'; END IF; RETURN NEW; END $$");
    owner.execute(
        "CREATE TRIGGER s3_reject_audit BEFORE INSERT ON shared.audit_log FOR EACH ROW EXECUTE FUNCTION shared.s3_reject_audit('"
            + action
            + "')");
    try {
      assertion.run();
    } finally {
      owner.execute("DROP TRIGGER s3_reject_audit ON shared.audit_log");
      owner.execute("DROP FUNCTION shared.s3_reject_audit()");
    }
  }
}
