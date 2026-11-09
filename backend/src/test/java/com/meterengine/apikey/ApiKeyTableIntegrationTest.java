package com.meterengine.apikey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.apikey.service.ApiKeyCredentialService;
import com.meterengine.apikey.service.ApiKeyHash;
import com.meterengine.apikey.service.ApiKeyService;
import com.meterengine.apikey.service.ApiKeySnapshot;
import com.meterengine.apikey.service.ApiKeyTable;
import com.meterengine.apikey.service.ApiKeyTableLoader;
import com.meterengine.apikey.service.IssuedApiKey;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.security.SecureTokens;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.context.WebApplicationContext;

// DB에 커밋된 키를 읽는지, 커밋 뒤에 테이블을 고치는지 보려고 시험 트랜잭션 없이 커밋한다.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ApiKeyTableIntegrationTest {

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ApiKeyCredentialService apiKeyCredentialService;
  @Autowired private ApiKeyService apiKeyService;
  @Autowired private ApiKeyTable apiKeyTable;
  @Autowired private ApiKeyTableLoader apiKeyTableLoader;

  private UUID organizationId;

  @BeforeEach
  void setUp() {
    organizationId = UUID.randomUUID();
    jdbcTemplate.update(
        "INSERT INTO organization (id, name) VALUES (?, '키 테이블 테스트 도입사')", organizationId);
  }

  @Test
  void 다시_읽으면_폐기되거나_만료된_키는_빼고_쓸_수_있는_키만_넣는다() {
    String usable =
        TestCredentials.rawKeyOf(TestCredentials.issueApiKey(jdbcTemplate, organizationId));
    String revoked =
        TestCredentials.rawKeyOf(TestCredentials.issueApiKey(jdbcTemplate, organizationId));
    String expired =
        TestCredentials.rawKeyOf(TestCredentials.issueApiKey(jdbcTemplate, organizationId));
    jdbcTemplate.update(
        "UPDATE api_key SET revoked_at = now() WHERE key_hash = sha256(convert_to(?, 'UTF8'))",
        revoked);
    jdbcTemplate.update(
        "UPDATE api_key SET expires_at = now() - interval '1 second' WHERE key_hash = sha256(convert_to(?, 'UTF8'))",
        expired);

    apiKeyTableLoader.refresh();

    assertThat(apiKeyTable.find(ApiKeyHash.of(usable))).isPresent();
    assertThat(apiKeyTable.find(ApiKeyHash.of(revoked))).isEmpty();
    assertThat(apiKeyTable.find(ApiKeyHash.of(expired))).isEmpty();
  }

  @Test
  void 같은_서버에서_발급하고_폐기하면_커밋_뒤_바로_테이블에_반영된다() {
    IssuedApiKey issued = issue(null);
    ApiKeyHash keyHash = ApiKeyHash.of(issued.secret());

    assertThat(apiKeyTable.find(keyHash))
        .hasValueSatisfying(snapshot -> assertThat(snapshot.revokedAt()).isNull());
    assertThat(apiKeyCredentialService.authenticate(issued.secret()))
        .contains(ApiKeySnapshot.of(issued.apiKey()).credential(true));

    apiKeyService.delete(organizationId, issued.apiKey().getId());

    assertThat(apiKeyTable.find(keyHash))
        .hasValueSatisfying(snapshot -> assertThat(snapshot.revokedAt()).isNotNull());
    assertThat(apiKeyCredentialService.authenticate(issued.secret())).isEmpty();
  }

  @Test
  void 테이블에_없는_키는_DB에_있어도_401_invalid_credentials다() {
    String rawKey = SecureTokens.generate("me_live_");
    jdbcTemplate.update(
        """
        INSERT INTO api_key (id, organization_id, mode, label, key_hash, last_four, scopes, created_at)
        VALUES (?, ?, 'live', '테이블 밖 키', sha256(convert_to(?, 'UTF8')), ?, '{read}', now())
        """,
        UUID.randomUUID(),
        organizationId,
        rawKey,
        rawKey.substring(rawKey.length() - 4));

    MvcTestResult result =
        mvc().get().uri("/v1/customers").header(AUTHORIZATION, "Bearer " + rawKey).exchange();

    assertThat(result).hasStatus(401);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(ErrorCode.INVALID_CREDENTIALS.getCode());
  }

  @Test
  void 같은_서버에서_교체하면_옛_키의_당겨진_만료_시각이_커밋_뒤_바로_테이블에_반영된다() {
    IssuedApiKey old = issue(null);

    issue(old.apiKey().getId());

    assertThat(apiKeyTable.find(ApiKeyHash.of(old.secret())))
        .hasValueSatisfying(snapshot -> assertThat(snapshot.expiresAt()).isNotNull());
  }

  @Test
  void 같은_서버에서_도입사를_정지하면_커밋_뒤_바로_그_도입사가_정지로_바뀐다() {
    IssuedApiKey issued = issue(null);
    assertThat(apiKeyTable.isOrganizationActive(organizationId)).isTrue();

    assertThat(updateOrganizationStatus("suspended")).hasStatusOk();

    assertThat(apiKeyTable.isOrganizationActive(organizationId)).isFalse();
    assertThat(apiKeyCredentialService.authenticate(issued.secret()))
        .contains(ApiKeySnapshot.of(issued.apiKey()).credential(false));
  }

  @Test
  void 정지했다가_다시_활성으로_돌리면_같은_키가_그대로_통과한다() {
    IssuedApiKey issued = issue(null);
    assertThat(updateOrganizationStatus("suspended")).hasStatusOk();

    assertThat(updateOrganizationStatus("active")).hasStatusOk();

    assertThat(mvc().get().uri("/v1/customers").header(AUTHORIZATION, "Bearer " + issued.secret()))
        .hasStatusOk();
  }

  private MvcTestResult updateOrganizationStatus(String status) {
    return mvc()
        .put()
        .uri("/v1/organizations/{id}/status", organizationId)
        .header(AUTHORIZATION, TestCredentials.issueOperatorSession(jdbcTemplate))
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"status\":\"%s\"}".formatted(status))
        .exchange();
  }

  private MockMvcTester mvc() {
    return MockMvcTester.from(
        webApplicationContext, builder -> builder.apply(springSecurity()).build());
  }

  private IssuedApiKey issue(UUID replacedApiKeyId) {
    return apiKeyService.create(organizationId, "테이블 키", "live", List.of("read"), replacedApiKeyId);
  }
}
