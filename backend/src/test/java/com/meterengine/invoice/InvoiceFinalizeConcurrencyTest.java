package com.meterengine.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.TestEvents;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.invoice.dto.InvoiceFinalizationResult;
import com.meterengine.invoice.service.InvoiceFinalizationService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class InvoiceFinalizeConcurrencyTest {

  private static final YearMonth AUGUST = YearMonth.of(2026, 8);
  private static final OffsetDateTime OCCURRED_AT =
      OffsetDateTime.parse("2026-08-10T12:00:00+09:00");

  @Autowired private InvoiceFinalizationService invoiceFinalizationService;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void 같은_달을_동시에_두_번_확정해도_고객마다_인보이스는_한_장이고_실패는_없다() throws Exception {
    UUID organizationId = organizationWithTokenBillableMetric("1");
    UUID acme = insertCustomer(organizationId, "아크메");
    UUID beta = insertCustomer(organizationId, "베타");
    insertEvent(organizationId, "tx-1", acme, "1000");
    insertEvent(organizationId, "tx-2", beta, "2000");
    CountDownLatch start = new CountDownLatch(1);

    List<CompletableFuture<InvoiceFinalizationResult>> runs =
        List.of(finalizeAfter(start, organizationId), finalizeAfter(start, organizationId));
    start.countDown();
    List<InvoiceFinalizationResult> results =
        List.of(runs.get(0).get(30, TimeUnit.SECONDS), runs.get(1).get(30, TimeUnit.SECONDS));

    assertThat(results).allSatisfy(result -> assertThat(result.failedCount()).isZero());
    assertThat(results.stream().mapToInt(result -> result.invoices().size()).sum()).isEqualTo(2);
    assertThat(invoiceCount(organizationId, acme)).isEqualTo(1);
    assertThat(invoiceCount(organizationId, beta)).isEqualTo(1);
  }

  @Test
  void 한_고객의_확정이_실패해도_다른_고객은_확정되고_실패한_고객은_실패_수에_든다() {
    UUID organizationId = organizationWithTokenBillableMetric("1");
    UUID first = insertCustomer(organizationId, "가");
    UUID failing = insertCustomer(organizationId, "나");
    UUID third = insertCustomer(organizationId, "다");
    insertEvent(organizationId, "tx-1", first, "1000");
    insertEvent(organizationId, "tx-2", failing, "1000000000000000000");
    insertEvent(organizationId, "tx-3", third, "3000");

    InvoiceFinalizationResult result = invoiceFinalizationService.finalize(organizationId, AUGUST);

    assertThat(result.failedCount()).isEqualTo(1);
    assertThat(result.invoices()).extracting("customerId").containsExactly(first, third);
    assertThat(invoiceCount(organizationId, first)).isEqualTo(1);
    assertThat(invoiceCount(organizationId, failing)).isZero();
    assertThat(invoiceCount(organizationId, third)).isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM outbound_email WHERE organization_id = ?",
                Integer.class,
                organizationId))
        .isEqualTo(2);
  }

  private CompletableFuture<InvoiceFinalizationResult> finalizeAfter(
      CountDownLatch start, UUID organizationId) {
    return CompletableFuture.supplyAsync(
        () -> {
          try {
            start.await();
          } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
          }
          return invoiceFinalizationService.finalize(organizationId, AUGUST);
        });
  }

  private int invoiceCount(UUID organizationId, UUID customerId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM invoice WHERE organization_id = ? AND customer_id = ?",
        Integer.class,
        organizationId,
        customerId);
  }

  private UUID organizationWithTokenBillableMetric(String unitPrice) {
    UUID organizationId =
        jdbcTemplate.queryForObject(
            "INSERT INTO organization (name) VALUES ('확정 동시성 도입사') RETURNING id", UUID.class);
    jdbcTemplate.update(
        """
        INSERT INTO billable_metric
          (organization_id, code, name, event_type, aggregation, target_property)
        VALUES (?, 'token-usage', '토큰 사용량', 'chat_completion', 'sum', 'token')
        """,
        organizationId);
    jdbcTemplate.update(
        """
        INSERT INTO price_rate (organization_id, billable_metric_code, dimension_values, unit_price)
        VALUES (?, 'token-usage', '{}'::jsonb, ?)
        """,
        organizationId,
        new BigDecimal(unitPrice));
    return organizationId;
  }

  private UUID insertCustomer(UUID organizationId, String name) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO customer (organization_id, name) VALUES (?, ?) RETURNING id",
        UUID.class,
        organizationId,
        name);
  }

  private void insertEvent(
      UUID organizationId, String transactionId, UUID customerId, String token) {
    TestEvents.insert(
        jdbcTemplate,
        organizationId,
        transactionId,
        customerId,
        "chat_completion",
        "{\"token\": %s}".formatted(token),
        OCCURRED_AT,
        OCCURRED_AT);
  }
}
