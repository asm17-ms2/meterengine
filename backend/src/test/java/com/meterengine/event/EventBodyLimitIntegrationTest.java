package com.meterengine.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.ServletContext;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class EventBodyLimitIntegrationTest {

  private static final int SINGLE_LIMIT_BYTES = 64 * 1024;
  private static final int BATCH_LIMIT_BYTES = 512 * 1024;

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private MeterRegistry meterRegistry;

  private MockMvcTester mvc;
  private String authorization;
  private UUID customerId;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
    UUID organizationId = UUID.randomUUID();
    authorization = TestCredentials.issueApiKey(jdbcTemplate, organizationId);
    customerId =
        jdbcTemplate.queryForObject(
            "INSERT INTO customer (organization_id, name) VALUES (?, 'acme') RETURNING id",
            UUID.class,
            organizationId);
  }

  @Test
  void 단건_본문이_64KB를_넘으면_413이고_code가_request_body_too_large다() {
    MvcTestResult result = post("/v1/events", eventLargerThan(SINGLE_LIMIT_BYTES));

    assertThat(result)
        .hasStatus(413)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo("request_body_too_large");
  }

  @Test
  void 배치는_64KB를_넘는_본문도_512KB까지_받는다() {
    String batch = "{\"events\":[" + eventLargerThan(SINGLE_LIMIT_BYTES) + "]}";

    assertThat(post("/v1/event-batches", batch)).hasStatusOk();
  }

  @Test
  void 길이_없이_온_단건_본문이_64KB를_넘으면_413이고_code가_request_body_too_large다() {
    MvcTestResult result =
        postWithoutContentLength("/v1/events", eventLargerThan(SINGLE_LIMIT_BYTES));

    assertThat(result)
        .hasStatus(413)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo("request_body_too_large");
  }

  @Test
  void 길이_없이_온_배치_본문이_512KB를_넘으면_413이고_code가_request_body_too_large다() {
    String batch = "{\"events\":[" + eventLargerThan(BATCH_LIMIT_BYTES) + "]}";

    MvcTestResult result = postWithoutContentLength("/v1/event-batches", batch);

    assertThat(result)
        .hasStatus(413)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo("request_body_too_large");
  }

  @Test
  void 길이_없이_온_큰_단건_본문은_읽을_수_없는_본문_거절로_세지_않는다() {
    double before = malformedRequestBodyRejectedCount();

    postWithoutContentLength("/v1/events", eventLargerThan(SINGLE_LIMIT_BYTES));

    assertThat(malformedRequestBodyRejectedCount() - before).isZero();
  }

  @Test
  void 키가_없는_요청은_본문이_커도_401이다() {
    MvcTestResult result =
        mvc.post()
            .uri("/v1/events")
            .contentType(MediaType.APPLICATION_JSON)
            .content(eventLargerThan(SINGLE_LIMIT_BYTES))
            .exchange();

    assertThat(result).hasStatus(401);
  }

  private MvcTestResult post(String uri, String json) {
    return mvc.post()
        .uri(uri)
        .header(AUTHORIZATION, authorization)
        .contentType(MediaType.APPLICATION_JSON)
        .content(json)
        .exchange();
  }

  private MvcTestResult postWithoutContentLength(String uri, String json) {
    return mvc.perform(
        servletContext -> {
          MockHttpServletRequest request = new UnknownLengthRequest(servletContext, uri);
          request.addHeader(AUTHORIZATION, authorization);
          request.setContentType(MediaType.APPLICATION_JSON_VALUE);
          request.setContent(json.getBytes(StandardCharsets.UTF_8));
          return request;
        });
  }

  private double malformedRequestBodyRejectedCount() {
    return meterRegistry
        .find("meterengine.event.ingest")
        .tag("result", "rejected")
        .tag("reason", "malformed_request_body")
        .counters()
        .stream()
        .mapToDouble(Counter::count)
        .sum();
  }

  private String eventLargerThan(int bytes) {
    return """
        {"transaction_id":"tx-large","customer_id":"%s","type":"api_call",
         "properties":{"note":"%s"},"timestamp":"%s"}
        """
        .formatted(customerId, "a".repeat(bytes), OffsetDateTime.now());
  }

  // MockMvc는 본문 길이를 Content-Length로 채운다. 나눠 보내는 요청처럼 길이를 모르게 한다.
  private static final class UnknownLengthRequest extends MockHttpServletRequest {

    private static final int UNKNOWN_LENGTH = -1;

    private UnknownLengthRequest(ServletContext servletContext, String uri) {
      super(servletContext, "POST", uri);
    }

    @Override
    public int getContentLength() {
      return UNKNOWN_LENGTH;
    }

    @Override
    public long getContentLengthLong() {
      return UNKNOWN_LENGTH;
    }
  }
}
