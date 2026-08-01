package com.meterengine.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.meterengine.TestcontainersConfiguration;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * POST /v1/events 인수 조건 검증 (MS2-38): 유효 이벤트 저장, 멱등 재전송, 시각 구분 저장.
 *
 * <p>필수 필드 누락 4xx는 검증 규약 PR에서, 인증과 해소는 후속 PR에서 다룬다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EventIngestionApiTest {

  @Autowired MockMvc mockMvc;
  @Autowired JdbcTemplate jdbc;

  @Value("${meterengine.ingestion.organization-id}")
  UUID organizationId;

  @BeforeEach
  void seedOrganization() {
    jdbc.update("INSERT INTO organizations (id) VALUES (?) ON CONFLICT DO NOTHING", organizationId);
  }

  @Test
  void 유효한_이벤트를_보내면_201과_함께_저장된다() throws Exception {
    mockMvc
        .perform(
            post("/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"event": {
                      "transaction_id": "tx-created-1",
                      "external_customer_id": "acme-corp",
                      "code": "llm_tokens",
                      "occurred_at": "2026-07-29T14:03:11Z",
                      "properties": {"token_count": 1530, "model": "llm-large"}
                    }}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.event.transaction_id").value("tx-created-1"))
        .andExpect(jsonPath("$.event.external_customer_id").value("acme-corp"))
        .andExpect(jsonPath("$.event.code").value("llm_tokens"))
        .andExpect(jsonPath("$.event.properties.token_count").value(1530))
        .andExpect(jsonPath("$.event.received_at").exists())
        .andExpect(jsonPath("$.event.unresolved[0]").value("external_customer_id"))
        .andExpect(jsonPath("$.event.unresolved[1]").value("code"));

    assertThat(countByTransactionId("tx-created-1")).isEqualTo(1);
  }

  @Test
  void 같은_transaction_id_재전송은_저장_없이_200과_기존_이벤트를_반환한다() throws Exception {
    mockMvc
        .perform(
            post("/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventJson("tx-idem-1", "acme-corp", "2026-07-29T14:03:11Z")))
        .andExpect(status().isCreated());

    // 내용을 바꿔 재전송해도 first-write-wins: 처음 저장된 이벤트가 그대로 돌아온다
    mockMvc
        .perform(
            post("/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventJson("tx-idem-1", "someone-else", "2026-07-30T00:00:00Z")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.event.external_customer_id").value("acme-corp"))
        .andExpect(jsonPath("$.event.occurred_at").value("2026-07-29T14:03:11Z"));

    assertThat(countByTransactionId("tx-idem-1")).isEqualTo(1);
  }

  @Test
  void occurred_at과_received_at이_구분_저장된다() throws Exception {
    mockMvc
        .perform(
            post("/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventJson("tx-clock-1", "acme-corp", "2026-01-01T00:00:00Z")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.event.occurred_at").value("2026-01-01T00:00:00Z"));

    OffsetDateTime receivedAt =
        jdbc.queryForObject(
            "SELECT received_at FROM usage_events WHERE transaction_id = 'tx-clock-1'",
            OffsetDateTime.class);
    assertThat(receivedAt).isAfter(OffsetDateTime.parse("2026-01-01T00:00:00Z"));
  }

  @Test
  void properties를_생략하면_빈_객체로_저장된다() throws Exception {
    mockMvc
        .perform(
            post("/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(eventJson("tx-no-props-1", "acme-corp", "2026-07-29T14:03:11Z")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.event.properties").isMap())
        .andExpect(jsonPath("$.event.properties").isEmpty());
  }

  private static String eventJson(String transactionId, String customerId, String occurredAt) {
    return """
        {"event": {
          "transaction_id": "%s",
          "external_customer_id": "%s",
          "code": "llm_tokens",
          "occurred_at": "%s"
        }}
        """
        .formatted(transactionId, customerId, occurredAt);
  }

  private long countByTransactionId(String transactionId) {
    Long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM usage_events WHERE transaction_id = ?",
            Long.class,
            transactionId);
    return count == null ? 0 : count;
  }
}
