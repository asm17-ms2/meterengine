package com.meterengine.customer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.customer.dto.CreateCustomerRequest;
import com.meterengine.customer.dto.CustomerRegistration;
import com.meterengine.customer.service.CustomerRegistrationService;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(properties = "meterengine.webhook.enabled=false")
@Import(TestcontainersConfiguration.class)
class CustomerExternalIdIntegrationTest {
  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private JsonMapper jsonMapper;
  @Autowired private CustomerRegistrationService customerRegistrationService;
  private MockMvcTester mvc;
  private UUID organizationId;
  private String authorization;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
    organizationId = UUID.randomUUID();
    authorization = TestCredentials.issueApiKey(jdbcTemplate, organizationId, "ingest", "read");
  }

  @Test
  void 같은_외부_ID는_201_다음_200으로_기존_고객을_돌려준다() {
    MvcTestResult first = create("{\"name\":\"첫 이름\",\"external_id\":\"customer-1\"}");
    MvcTestResult repeated = create("{\"name\":\"다른 이름\",\"external_id\":\"customer-1\"}");
    assertThat(first).hasStatus(201);
    assertThat(repeated).hasStatusOk();
    assertThat(body(first).path("id").asString()).isEqualTo(body(repeated).path("id").asString());
    assertThat(body(repeated).path("name").asString()).isEqualTo("첫 이름");
    MvcTestResult listed =
        mvc.get()
            .uri("/v1/customers")
            .param("external_id", "customer-1")
            .header("Authorization", authorization)
            .exchange();
    assertThat(listed).hasStatusOk();
    assertThat(body(listed).path("customers").size()).isEqualTo(1);
  }

  @Test
  void 두_요청이_동시에_생성해도_고객은_하나다() throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first =
          executor.submit(
              () -> {
                start.await();
                return customerRegistrationService.create(
                    organizationId, new CreateCustomerRequest("첫 이름", null, "same"));
              });
      var second =
          executor.submit(
              () -> {
                start.await();
                return customerRegistrationService.create(
                    organizationId, new CreateCustomerRequest("둘째 이름", null, "same"));
              });
      start.countDown();
      CustomerRegistration left = first.get(15, TimeUnit.SECONDS);
      CustomerRegistration right = second.get(15, TimeUnit.SECONDS);
      assertThat(left.customer().getId()).isEqualTo(right.customer().getId());
      assertThat(left.created()).isNotEqualTo(right.created());
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT count(*) FROM customer WHERE organization_id = ? AND external_id = 'same'",
                  Integer.class,
                  organizationId))
          .isEqualTo(1);
    }
  }

  @Test
  void 고객_단건은_test와_live_read_키로_읽고_다른_도입사는_거절한다() {
    UUID id =
        UUID.fromString(
            body(create("{\"name\":\"고객\",\"external_id\":\"readable\"}")).path("id").asString());
    for (String mode : new String[] {"test", "live"}) {
      String read = TestCredentials.issueApiKey(jdbcTemplate, organizationId, mode, "read");
      assertThat(mvc.get().uri("/v1/customers/" + id).header("Authorization", read).exchange())
          .hasStatusOk();
    }
    String other = TestCredentials.issueApiKey(jdbcTemplate, UUID.randomUUID(), "read");
    assertThat(mvc.get().uri("/v1/customers/" + id).header("Authorization", other).exchange())
        .hasStatus(404);
    String ingestOnly = TestCredentials.issueApiKey(jdbcTemplate, organizationId, "ingest");
    assertThat(mvc.get().uri("/v1/customers/" + id).header("Authorization", ingestOnly).exchange())
        .hasStatus(403);
  }

  private MvcTestResult create(String json) {
    return mvc.post()
        .uri("/v1/customers")
        .header("Authorization", authorization)
        .contentType(MediaType.APPLICATION_JSON)
        .content(json)
        .exchange();
  }

  private JsonNode body(MvcTestResult result) {
    return jsonMapper.readTree(
        new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8));
  }
}
