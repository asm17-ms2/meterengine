package com.meterengine.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.TestClock;
import com.meterengine.TestEvents;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.invoice.repository.InvoiceFinalizationAttemptRepository;
import com.meterengine.invoice.service.InvoiceAutoFinalizationService;
import com.meterengine.invoice.service.InvoiceFinalizationMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "meterengine.invoice.auto-finalize.enabled=true")
class InvoiceAutoFinalizeSchedulingTest {

  @Autowired private InvoiceAutoFinalizationService invoiceAutoFinalizationService;
  @Autowired private InvoiceFinalizationMetrics invoiceFinalizationMetrics;
  @Autowired private InvoiceFinalizationAttemptRepository invoiceFinalizationAttemptRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private MeterRegistry meterRegistry;
  @Autowired private TestClock testClock;

  @AfterEach
  void resetClock() {
    testClock.reset();
  }

  @Test
  void 켜져_있으면_정시_실행이_그_정시를_슬롯으로_한_번만_기록한다() {
    testClock.fix(OffsetDateTime.parse("2026-10-01T14:00:30+09:00"));

    invoiceAutoFinalizationService.run();
    invoiceAutoFinalizationService.run();

    List<Map<String, Object>> runs =
        jdbcTemplate.queryForList(
            """
            SELECT status FROM scheduled_job_run
            WHERE job_name = 'invoice_auto_finalize' AND scheduled_at = ?
            """,
            OffsetDateTime.parse("2026-10-01T14:00:00+09:00"));
    assertThat(runs).extracting(run -> run.get("status")).containsExactly("succeeded");
  }

  @Test
  void 정시_실행은_활성_도입사만_확정한다() {
    UUID active = organizationWithBilledCustomer("active");
    UUID suspended = organizationWithBilledCustomer("suspended");
    testClock.fix(OffsetDateTime.parse("2026-10-01T15:00:10+09:00"));

    invoiceAutoFinalizationService.run();

    assertThat(invoiceCount(active)).isEqualTo(1);
    assertThat(invoiceCount(suspended)).isZero();
  }

  @Test
  void 정시_실행이_끝나면_수집_때_DB를_읽지_않는_미확정_지표를_다시_센다() {
    UUID organizationId = organizationWithBilledCustomer("active", "2000000000000000000");
    invoiceFinalizationMetrics.refresh();
    testClock.fix(OffsetDateTime.parse("2026-10-01T16:00:10+09:00"));

    invoiceAutoFinalizationService.run();

    assertThat(
            jdbcTemplate.queryForObject(
                """
                SELECT result FROM invoice_finalization_attempt
                WHERE organization_id = ? AND period = '2026-09'
                """,
                String.class,
                organizationId))
        .isEqualTo("failed");
    assertThat(
            meterRegistry
                .get("meterengine.invoice.unfinalized")
                .tag("environment", "legacy")
                .tag("result", "failed")
                .gauge()
                .value())
        .isEqualTo(legacyFailedCount());
  }

  private double legacyFailedCount() {
    return invoiceFinalizationAttemptRepository.countOpen().stream()
        .filter(
            openCount ->
                openCount.getEnvironment().equals("legacy")
                    && openCount.getResult().equals("failed"))
        .mapToLong(InvoiceFinalizationAttemptRepository.OpenCount::getCount)
        .sum();
  }

  private int invoiceCount(UUID organizationId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM invoice WHERE organization_id = ?", Integer.class, organizationId);
  }

  private UUID organizationWithBilledCustomer(String status) {
    return organizationWithBilledCustomer(status, "1000");
  }

  // 9월에 단가가 있는 사용량을 낸 고객 하나를 가진 도입사를 만든다.
  private UUID organizationWithBilledCustomer(String status, String token) {
    UUID organizationId =
        jdbcTemplate.queryForObject(
            "INSERT INTO organization (name, status) VALUES ('정시 도입사', ?) RETURNING id",
            UUID.class,
            status);
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
        VALUES (?, 'token-usage', '{}'::jsonb, 1)
        """,
        organizationId);
    UUID customerId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO customer (organization_id, name, created_at)
            VALUES (?, '아크메', '2026-08-01T00:00:00+09:00')
            RETURNING id
            """,
            UUID.class,
            organizationId);
    TestEvents.insert(
        jdbcTemplate,
        organizationId,
        "tx-1",
        customerId,
        "chat_completion",
        "{\"token\": %s}".formatted(token),
        OffsetDateTime.parse("2026-09-30T10:00:00+09:00"),
        OffsetDateTime.parse("2026-09-30T10:00:00+09:00"));
    return organizationId;
  }
}
