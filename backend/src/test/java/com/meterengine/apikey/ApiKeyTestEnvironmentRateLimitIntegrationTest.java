package com.meterengine.apikey;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.jayway.jsonpath.JsonPath;
import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "meterengine.api-key.test-rate-limit-per-minute=2")
@Transactional
class ApiKeyTestEnvironmentRateLimitIntegrationTest {

  private static final String ENVIRONMENT_HEADER = "X-MeterEngine-Environment";

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
  }

  @Test
  void 한도_없이_발급한_테스트_환경_키도_플랫폼_상한에서_429다() throws Exception {
    String session = TestCredentials.issueMemberSession(jdbcTemplate, UUID.randomUUID());
    String key = create(session, "test", "null");

    assertThat(customers(key)).hasStatusOk();
    assertThat(customers(key)).hasStatusOk();
    MvcTestResult limited = customers(key);
    assertThat(limited)
        .hasStatus(429)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("rate_limit_exceeded");
    assertThat(limited.getResponse().getHeader("Retry-After")).isNotBlank();
  }

  @Test
  void 상한보다_큰_한도로_발급한_테스트_환경_키도_플랫폼_상한에서_429다() throws Exception {
    String session = TestCredentials.issueMemberSession(jdbcTemplate, UUID.randomUUID());
    String key = create(session, "test", "1000");

    assertThat(customers(key)).hasStatusOk();
    assertThat(customers(key)).hasStatusOk();
    assertThat(customers(key)).hasStatus(429);
  }

  @Test
  void 상한보다_작은_한도로_발급한_테스트_환경_키는_키의_한도에서_429다() throws Exception {
    String session = TestCredentials.issueMemberSession(jdbcTemplate, UUID.randomUUID());
    String key = create(session, "test", "1");

    assertThat(customers(key)).hasStatusOk();
    assertThat(customers(key)).hasStatus(429);
  }

  @Test
  void 실제_환경_키에는_플랫폼_상한을_걸지_않는다() throws Exception {
    String session = TestCredentials.issueMemberSession(jdbcTemplate, UUID.randomUUID());
    String key = create(session, "live", "null");

    for (int index = 0; index < 4; index++) assertThat(customers(key)).as("" + index).hasStatusOk();
  }

  private String create(String session, String mode, String limit) throws Exception {
    MvcTestResult result =
        mvc.post()
            .uri("/v1/api-keys")
            .header("Authorization", session)
            .header(ENVIRONMENT_HEADER, mode)
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                "{\"label\":\"조회 키\",\"mode\":\"%s\",\"scopes\":[\"read\"],\"rate_limit_per_minute\":%s}"
                    .formatted(mode, limit))
            .exchange();
    assertThat(result).hasStatus(201);
    TestCredentials.refreshApiKeyTable(jdbcTemplate);
    return JsonPath.read(result.getResponse().getContentAsString(), "$.secret");
  }

  private MvcTestResult customers(String key) {
    return mvc.get().uri("/v1/customers").header("Authorization", "Bearer " + key).exchange();
  }
}
