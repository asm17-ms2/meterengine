package com.meterengine.apikey;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.apikey.service.ApiKeyHash;
import com.meterengine.apikey.service.ApiKeyTable;
import com.meterengine.apikey.service.ApiKeyTableLoader;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

// DB에 커밋된 키를 읽는지 보려고 시험 트랜잭션 없이 커밋한다.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ApiKeyTableIntegrationTest {

  @Autowired private JdbcTemplate jdbcTemplate;
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
}
