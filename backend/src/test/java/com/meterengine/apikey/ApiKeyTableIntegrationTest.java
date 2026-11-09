package com.meterengine.apikey;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.apikey.service.ApiKeyHash;
import com.meterengine.apikey.service.ApiKeyService;
import com.meterengine.apikey.service.ApiKeyTable;
import com.meterengine.apikey.service.ApiKeyTableLoader;
import com.meterengine.apikey.service.IssuedApiKey;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

// DB에 커밋된 키를 읽는지, 커밋 뒤에 테이블을 고치는지 보려고 시험 트랜잭션 없이 커밋한다.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ApiKeyTableIntegrationTest {

  @Autowired private JdbcTemplate jdbcTemplate;
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

    apiKeyService.delete(organizationId, issued.apiKey().getId());

    assertThat(apiKeyTable.find(keyHash))
        .hasValueSatisfying(snapshot -> assertThat(snapshot.revokedAt()).isNotNull());
  }

  @Test
  void 같은_서버에서_교체하면_옛_키의_당겨진_만료_시각이_커밋_뒤_바로_테이블에_반영된다() {
    IssuedApiKey old = issue(null);

    issue(old.apiKey().getId());

    assertThat(apiKeyTable.find(ApiKeyHash.of(old.secret())))
        .hasValueSatisfying(snapshot -> assertThat(snapshot.expiresAt()).isNotNull());
  }

  private IssuedApiKey issue(UUID replacedApiKeyId) {
    return apiKeyService.create(organizationId, "테이블 키", "live", List.of("read"), replacedApiKeyId);
  }
}
