package com.meterengine.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.TestcontainersConfiguration;
import com.meterengine.event.dto.ReceivedAtRange;
import com.meterengine.invoice.dto.DraftInvoiceResponse.DraftInvoiceCustomer;
import com.meterengine.invoice.service.DraftInvoiceService;
import com.meterengine.metric.repository.BillableMetricUsageRepository;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class ReceivedAtRangeIntegrationTest {

  private static final YearMonth AUGUST = YearMonth.of(2026, 8);
  private static final String IN_AUGUST = "2026-08-10T12:00:00+09:00";
  private static final OffsetDateTime AUGUST_START =
      OffsetDateTime.parse("2026-08-01T00:00:00+09:00");
  private static final OffsetDateTime AUGUST_END =
      OffsetDateTime.parse("2026-09-01T00:00:00+09:00");

  @Autowired private DraftInvoiceService draftInvoiceService;
  @Autowired private BillableMetricUsageRepository billableMetricUsageRepository;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void 마감_시각_전에_받은_이벤트만_금액에_들어가고_마감_시각에_받은_이벤트는_빠진다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(organizationId, "아크메");
    OffsetDateTime firstReceivedAt = insertEvent(organizationId, "tx-1", acme, 3280);
    OffsetDateTime cutoff = insertEvent(organizationId, "tx-2", acme, 1000);

    assertThat(amountOf(organizationId, acme, new ReceivedAtRange(firstReceivedAt, cutoff)))
        .isEqualTo(1640);
  }

  @Test
  void 시작_시각에_받은_이벤트는_들어가고_그_전에_받은_이벤트는_빠진다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(organizationId, "아크메");
    insertEvent(organizationId, "tx-1", acme, 3280);
    OffsetDateTime start = insertEvent(organizationId, "tx-2", acme, 1000);

    assertThat(amountOf(organizationId, acme, new ReceivedAtRange(start, OffsetDateTime.MAX)))
        .isEqualTo(500);
  }

  @Test
  void 같은_구간으로_다시_읽으면_그_사이에_이벤트가_들어와도_금액이_같다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(organizationId, "아크메");
    OffsetDateTime start = insertEvent(organizationId, "tx-1", acme, 3280);
    OffsetDateTime cutoff = insertEvent(organizationId, "tx-2", acme, 1000);
    ReceivedAtRange receivedAtRange = new ReceivedAtRange(start, cutoff);

    long before = amountOf(organizationId, acme, receivedAtRange);
    insertEvent(organizationId, "tx-3", acme, 700);

    assertThat(amountOf(organizationId, acme, receivedAtRange)).isEqualTo(before);
    assertThat(amountOf(organizationId, acme, ReceivedAtRange.unbounded())).isEqualTo(2490);
  }

  @Test
  void 마감_시각부터만_이벤트를_받은_고객은_이벤트가_있는_고객에_나오지_않고_금액도_0이다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(organizationId, "아크메");
    UUID beta = insertCustomer(organizationId, "베타");
    OffsetDateTime start = insertEvent(organizationId, "tx-1", acme, 3280);
    OffsetDateTime cutoff = insertEvent(organizationId, "tx-2", beta, 1000);
    ReceivedAtRange receivedAtRange = new ReceivedAtRange(start, cutoff);

    assertThat(customerIdsWithEvents(organizationId, receivedAtRange)).containsExactly(acme);
    assertThat(amountOf(organizationId, beta, receivedAtRange)).isZero();
    assertThat(customerIdsWithEvents(organizationId, ReceivedAtRange.unbounded()))
        .containsExactlyInAnyOrder(acme, beta);
  }

  @Test
  void 다른_달에_발생한_이벤트만_있는_고객은_이벤트가_있는_고객에_나오지_않는다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(organizationId, "아크메");
    insertEvent(organizationId, "tx-1", acme, 3280, "2026-09-01T00:00:00+09:00");

    assertThat(customerIdsWithEvents(organizationId, ReceivedAtRange.unbounded())).isEmpty();
  }

  private Set<UUID> customerIdsWithEvents(UUID organizationId, ReceivedAtRange receivedAtRange) {
    return billableMetricUsageRepository.findCustomerIdsWithEvents(
        organizationId, AUGUST_START, AUGUST_END, receivedAtRange);
  }

  private long amountOf(UUID organizationId, UUID customerId, ReceivedAtRange receivedAtRange) {
    return draftInvoiceService.preview(organizationId, AUGUST, receivedAtRange).customers().stream()
        .filter(draftInvoiceCustomer -> draftInvoiceCustomer.customerId().equals(customerId))
        .findFirst()
        .map(DraftInvoiceCustomer::amount)
        .orElseThrow();
  }

  // 토큰 사용량 미터와 그 기본 단가를 가진 도입사를 만든다.
  private UUID organizationWithTokenBillableMetric() {
    UUID organizationId =
        jdbcTemplate.queryForObject(
            "INSERT INTO organization (name) VALUES ('도입사') RETURNING id", UUID.class);
    jdbcTemplate.update(
        """
        INSERT INTO billable_metric
          (organization_id, code, name, event_type, aggregation, target_property)
        VALUES (?, 'token-usage', '토큰 사용량', 'chat_completion', 'sum', 'token')
        """,
        organizationId);
    jdbcTemplate.update(
        "INSERT INTO price_policy (organization_id, billable_metric_code) VALUES (?, 'token-usage')",
        organizationId);
    jdbcTemplate.update(
        """
        INSERT INTO price_rate (organization_id, billable_metric_code, dimension_values, unit_price)
        VALUES (?, 'token-usage', '{}', 0.5)
        """,
        organizationId);
    return organizationId;
  }

  private UUID insertCustomer(UUID organizationId, String name) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO customer (organization_id, name) VALUES (?, ?) RETURNING id",
        UUID.class,
        organizationId,
        name);
  }

  private OffsetDateTime insertEvent(
      UUID organizationId, String transactionId, UUID customerId, int token) {
    return insertEvent(organizationId, transactionId, customerId, token, IN_AUGUST);
  }

  // 이벤트를 넣고 서버가 받은 시각을 돌려준다.
  private OffsetDateTime insertEvent(
      UUID organizationId, String transactionId, UUID customerId, int token, String occurredAt) {
    return jdbcTemplate.queryForObject(
        """
        INSERT INTO event
          (organization_id, transaction_id, customer_id, type, properties, occurred_at)
        VALUES (?, ?, ?, 'chat_completion', ?::jsonb, ?)
        RETURNING received_at
        """,
        OffsetDateTime.class,
        organizationId,
        transactionId,
        customerId,
        "{\"token\":%d}".formatted(token),
        OffsetDateTime.parse(occurredAt));
  }
}
