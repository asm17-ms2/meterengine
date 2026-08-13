package com.meterengine.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meterengine.customer.CustomerRepository;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import tools.jackson.databind.json.JsonMapper;

/**
 * 수집 서비스의 분기 검증 (MS2-130).
 *
 * <p>동시 요청은 ON CONFLICT DO NOTHING 한 문장이 DB에서 직렬화하므로 실제 경합을 재현하는 대신, 제약 위반 예외가 터졌을 때의 경로를 단위 테스트로
 * 갈음한다 (하위작업 인수 기준).
 */
@ExtendWith(MockitoExtension.class)
class UsageEventIngestServiceTest {

  private static final UUID ORG_ID = UUID.randomUUID();
  private static final UUID CUSTOMER_ID = UUID.randomUUID();

  @Mock private UsageEventRepository usageEvents;
  @Mock private CustomerRepository customers;

  private UsageEventIngestService service;

  @BeforeEach
  void setUp() {
    service = new UsageEventIngestService(usageEvents, customers, JsonMapper.builder().build());
  }

  @Test
  void 저장에_성공하면_중복이_아니라고_응답한다() {
    when(customers.existsByOrganizationIdAndId(ORG_ID, CUSTOMER_ID)).thenReturn(true);
    when(usageEvents.insertIfAbsent(any(), any(), any(), any(), any(), any())).thenReturn(1);

    IngestEventResponse response = service.ingest(ORG_ID, request("tx-1"));

    assertThat(response.transactionId()).isEqualTo("tx-1");
    assertThat(response.duplicate()).isFalse();
  }

  @Test
  void 이미_있는_키라_저장이_건너뛰어지면_중복이라고_응답한다() {
    when(customers.existsByOrganizationIdAndId(ORG_ID, CUSTOMER_ID)).thenReturn(true);
    when(usageEvents.insertIfAbsent(any(), any(), any(), any(), any(), any())).thenReturn(0);

    assertThat(service.ingest(ORG_ID, request("tx-1")).duplicate()).isTrue();
  }

  @Test
  void 유니크_제약_위반_예외가_터져도_중복_성공으로_응답한다() {
    when(customers.existsByOrganizationIdAndId(ORG_ID, CUSTOMER_ID)).thenReturn(true);
    when(usageEvents.insertIfAbsent(any(), any(), any(), any(), any(), any()))
        .thenThrow(new DuplicateKeyException("duplicate key value violates unique constraint"));

    IngestEventResponse response = service.ingest(ORG_ID, request("tx-1"));

    assertThat(response.duplicate()).isTrue();
  }

  @Test
  void 이_도입사의_고객이_아니면_저장하지_않고_예외를_던진다() {
    when(customers.existsByOrganizationIdAndId(ORG_ID, CUSTOMER_ID)).thenReturn(false);

    assertThatThrownBy(() -> service.ingest(ORG_ID, request("tx-1")))
        .isInstanceOf(UnknownCustomerException.class)
        .hasMessageContaining(CUSTOMER_ID.toString())
        .hasMessageContaining(ORG_ID.toString());

    verify(usageEvents, never()).insertIfAbsent(any(), any(), any(), any(), any(), any());
  }

  @Test
  void properties는_판정_없이_그대로_직렬화해_넘긴다() {
    when(customers.existsByOrganizationIdAndId(ORG_ID, CUSTOMER_ID)).thenReturn(true);
    when(usageEvents.insertIfAbsent(any(), any(), any(), any(), any(), any())).thenReturn(1);

    service.ingest(
        ORG_ID,
        new IngestEventRequest(
            "tx-1",
            CUSTOMER_ID,
            "chat_completion",
            Map.of("whatever", "value"),
            OffsetDateTime.parse("2026-08-10T12:00:00+09:00")));

    verify(usageEvents)
        .insertIfAbsent(
            eq(ORG_ID),
            eq("tx-1"),
            eq(CUSTOMER_ID),
            eq("chat_completion"),
            anyString(),
            eq(OffsetDateTime.parse("2026-08-10T12:00:00+09:00")));
  }

  private IngestEventRequest request(String transactionId) {
    return new IngestEventRequest(
        transactionId,
        CUSTOMER_ID,
        "chat_completion",
        Map.of("model", "gpt-4o-mini", "token", 1200),
        OffsetDateTime.parse("2026-08-10T12:00:00+09:00"));
  }
}
