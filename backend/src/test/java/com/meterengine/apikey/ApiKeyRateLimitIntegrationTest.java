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
@SpringBootTest
@Transactional
class ApiKeyRateLimitIntegrationTest {
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
  void 발급한_유한_한도가_실제_요청에_적용되고_조회와_감사에도_남는다() throws Exception {
    UUID organizationId = UUID.randomUUID();
    String session = TestCredentials.issueMemberSession(jdbcTemplate, organizationId);
    MvcTestResult result = create(session, "1");
    assertThat(result)
        .hasStatus(201)
        .bodyJson()
        .extractingPath("$.rate_limit_per_minute")
        .isEqualTo(1);
    String body = result.getResponse().getContentAsString();
    String key = JsonPath.read(body, "$.secret");
    assertThat(mvc.get().uri("/v1/customers").header("Authorization", "Bearer " + key).exchange())
        .hasStatusOk();
    MvcTestResult limited =
        mvc.get().uri("/v1/customers").header("Authorization", "Bearer " + key).exchange();
    assertThat(limited)
        .hasStatus(429)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo("rate_limit_exceeded");
    assertThat(limited.getResponse().getHeader("Retry-After")).isNotBlank();
    assertThat(mvc.get().uri("/v1/api-keys").header("Authorization", session).exchange())
        .hasStatusOk()
        .bodyJson()
        .extractingPath("$.api_keys[0].rate_limit_per_minute")
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "select detail->>'rate_limit_per_minute' from audit_log where organization_id = ? and action = 'api_key_created'",
                String.class,
                organizationId))
        .isEqualTo("1");
  }

  @Test
  void null_한도는_무제한으로_발급된다() throws Exception {
    String session = TestCredentials.issueMemberSession(jdbcTemplate, UUID.randomUUID());
    MvcTestResult result = create(session, "null");
    assertThat(result).hasStatus(201).bodyJson().extractingPath("$.rate_limit_per_minute").isNull();
    String key = JsonPath.read(result.getResponse().getContentAsString(), "$.secret");
    for (int index = 0; index < 3; index++)
      assertThat(mvc.get().uri("/v1/customers").header("Authorization", "Bearer " + key).exchange())
          .hasStatusOk();
  }

  @Test
  void 한도가_0이나_음수이면_발급하지_않는다() {
    UUID organizationId = UUID.randomUUID();
    String session = TestCredentials.issueMemberSession(jdbcTemplate, organizationId);
    assertThat(create(session, "0")).hasStatus(400);
    assertThat(create(session, "-1")).hasStatus(400);
    assertThat(
            jdbcTemplate.queryForObject(
                "select count(*) from api_key where organization_id = ?",
                Long.class,
                organizationId))
        .isZero();
  }

  private MvcTestResult create(String session, String limit) {
    return mvc.post()
        .uri("/v1/api-keys")
        .header("Authorization", session)
        .contentType(MediaType.APPLICATION_JSON)
        .content(
            "{\"label\":\"조회 키\",\"mode\":\"test\",\"scopes\":[\"read\"],\"rate_limit_per_minute\":"
                + limit
                + "}")
        .exchange();
  }
}
