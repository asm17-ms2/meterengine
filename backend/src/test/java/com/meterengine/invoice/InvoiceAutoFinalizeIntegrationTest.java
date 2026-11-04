package com.meterengine.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.meterengine.TestEvents;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.invoice.repository.InvoiceFinalizationAttemptRepository;
import com.meterengine.invoice.service.InvoiceAutoFinalizationService;
import com.meterengine.invoice.service.InvoiceFinalizationMetrics;
import com.meterengine.invoice.service.InvoiceFinalizationService;
import com.meterengine.scheduling.dto.ScheduledJobResult;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class InvoiceAutoFinalizeIntegrationTest {

  private static final String SEPTEMBER = "2026-09";
  private static final OffsetDateTime REGISTERED_AT =
      OffsetDateTime.parse("2026-08-01T00:00:00+09:00");
  private static final OffsetDateTime OCTOBER_1_AT_14 =
      OffsetDateTime.parse("2026-10-01T14:00:00+09:00");
  private static final OffsetDateTime OCTOBER_1_AT_15 =
      OffsetDateTime.parse("2026-10-01T15:00:00+09:00");

  @Autowired private InvoiceAutoFinalizationService invoiceAutoFinalizationService;
  @Autowired private InvoiceFinalizationService invoiceFinalizationService;
  @Autowired private InvoiceFinalizationAttemptRepository invoiceFinalizationAttemptRepository;
  @Autowired private InvoiceFinalizationMetrics invoiceFinalizationMetrics;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private MeterRegistry meterRegistry;

  @Test
  void 확정_시각이_지난_달은_보류_조건이_없는_고객만_마감_전에_받은_이벤트로_확정하고_고객마다_결과를_남긴다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID priced = insertCustomer(organizationId, "가");
    UUID unpriced = insertCustomer(organizationId, "나");
    UUID quiet = insertCustomer(organizationId, "다");
    UUID late = insertCustomer(organizationId, "라");
    UUID tiny = insertCustomer(organizationId, "마");
    insertSeptemberEvent(
        organizationId, "tx-1", priced, "{\"token\": 3299}", "2026-10-01T12:59:59+09:00");
    insertSeptemberEvent(
        organizationId, "tx-2", priced, "{\"token\": 1000}", "2026-10-01T13:00:00+09:00");
    insertSeptemberEvent(
        organizationId,
        "tx-3",
        unpriced,
        "{\"token\": 500, \"model\": \"new\"}",
        "2026-09-30T10:00:00+09:00");
    insertSeptemberEvent(
        organizationId, "tx-6", unpriced, "{\"token\": 1000}", "2026-09-30T10:00:00+09:00");
    insertSeptemberEvent(
        organizationId, "tx-4", late, "{\"token\": 1000}", "2026-10-01T13:30:00+09:00");
    insertSeptemberEvent(
        organizationId, "tx-5", tiny, "{\"token\": 1}", "2026-09-30T10:00:00+09:00");

    ScheduledJobResult result =
        invoiceAutoFinalizationService.finalize(organizationId, OCTOBER_1_AT_14);

    assertThat(result).isEqualTo(new ScheduledJobResult(1, 1, 0));
    assertThat(invoices(organizationId))
        .containsExactly(
            Map.of(
                "customer_id", priced,
                "period", SEPTEMBER,
                "supply_amount", 1640L,
                "cutoff_at", OffsetDateTime.parse("2026-10-01T13:00:00+09:00"),
                "finalized_at", OCTOBER_1_AT_14));
    assertThat(attempts(organizationId, SEPTEMBER))
        .extracting(attempt -> attempt.get("customer_id"), attempt -> attempt.get("result"))
        .containsExactlyInAnyOrder(
            tuple(priced, "finalized"),
            tuple(unpriced, "held"),
            tuple(quiet, "no_events"),
            tuple(late, "no_events"),
            tuple(tiny, "zero_amount"));
    assertThat(reason(organizationId, unpriced, SEPTEMBER))
        .isEqualTo("단가 없는 사용량: token-usage {model=new} 500");
  }

  @Test
  void 확정_시각_1분_전에는_지난달을_건드리지_않는다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(organizationId, "아크메");
    insertSeptemberEvent(
        organizationId, "tx-1", acme, "{\"token\": 3299}", "2026-09-30T10:00:00+09:00");

    invoiceAutoFinalizationService.finalize(
        organizationId, OffsetDateTime.parse("2026-10-01T13:59:00+09:00"));

    assertThat(invoices(organizationId)).isEmpty();
    assertThat(attempts(organizationId, SEPTEMBER)).isEmpty();
  }

  @Test
  void 보류된_고객은_이번_달부터_적용되는_단가를_넣어도_다음_정시에_다시_시도하지_않고_끝난_고객도_다시_시도하지_않는다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID priced = insertCustomer(organizationId, "가");
    UUID unpriced = insertCustomer(organizationId, "나");
    insertSeptemberEvent(
        organizationId, "tx-1", priced, "{\"token\": 3299}", "2026-09-30T10:00:00+09:00");
    insertSeptemberEvent(
        organizationId,
        "tx-2",
        unpriced,
        "{\"token\": 500, \"model\": \"new\"}",
        "2026-09-30T10:00:00+09:00");
    insertSeptemberEvent(
        organizationId, "tx-3", unpriced, "{\"token\": 1000}", "2026-09-30T10:00:00+09:00");
    invoiceAutoFinalizationService.finalize(organizationId, OCTOBER_1_AT_14);

    insertPrice(organizationId, null, "{\"model\": \"new\"}", "2", "2026-10");
    ScheduledJobResult result =
        invoiceAutoFinalizationService.finalize(organizationId, OCTOBER_1_AT_15);

    assertThat(result).isEqualTo(new ScheduledJobResult(0, 0, 0));
    assertThat(attempts(organizationId, SEPTEMBER))
        .extracting(attempt -> attempt.get("customer_id"), attempt -> attempt.get("result"))
        .containsExactly(tuple(priced, "finalized"), tuple(unpriced, "held"));
    assertThat(invoices(organizationId)).hasSize(1);
  }

  @Test
  void 단가_없는_사용량만_있어_0원인_고객은_보류하지_않고_0원으로_끝내며_다음_정시에_다시_시도하지_않는다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID unpriced = insertCustomer(organizationId, "가");
    insertSeptemberEvent(
        organizationId,
        "tx-1",
        unpriced,
        "{\"token\": 500, \"model\": \"new\"}",
        "2026-09-30T10:00:00+09:00");

    ScheduledJobResult first =
        invoiceAutoFinalizationService.finalize(organizationId, OCTOBER_1_AT_14);
    ScheduledJobResult next =
        invoiceAutoFinalizationService.finalize(organizationId, OCTOBER_1_AT_15);

    assertThat(first).isEqualTo(new ScheduledJobResult(0, 0, 0));
    assertThat(next).isEqualTo(new ScheduledJobResult(0, 0, 0));
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT result, reason, unpriced_usages FROM invoice_finalization_attempt"
                    + " WHERE organization_id = ? AND customer_id = ? AND period = ?",
                organizationId,
                unpriced,
                SEPTEMBER))
        .extracting(
            attempt -> attempt.get("result"),
            attempt -> attempt.get("reason"),
            attempt -> attempt.get("unpriced_usages"))
        .containsExactly(tuple("zero_amount", "단가 없는 사용량: token-usage {model=new} 500", null));
    assertThat(invoiceFinalizationAttemptRepository.findOpenAttempts(organizationId, null))
        .isEmpty();
    assertThat(invoices(organizationId)).isEmpty();
  }

  @Test
  void 보류된_고객을_수동으로_확정하면_단가_있는_사용량만_청구되고_자동_확정은_다시_시도하지_않는다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID mixed = insertCustomer(organizationId, "가");
    insertSeptemberEvent(
        organizationId, "tx-1", mixed, "{\"token\": 1000}", "2026-09-30T10:00:00+09:00");
    insertSeptemberEvent(
        organizationId,
        "tx-2",
        mixed,
        "{\"token\": 500, \"model\": \"new\"}",
        "2026-09-30T10:00:00+09:00");
    invoiceAutoFinalizationService.finalize(organizationId, OCTOBER_1_AT_14);

    invoiceFinalizationService.finalize(organizationId, YearMonth.of(2026, 9));
    ScheduledJobResult result =
        invoiceAutoFinalizationService.finalize(organizationId, OCTOBER_1_AT_15);

    assertThat(result).isEqualTo(new ScheduledJobResult(0, 0, 0));
    assertThat(attempts(organizationId, SEPTEMBER))
        .extracting(attempt -> attempt.get("result"))
        .containsExactly("held");
    assertThat(invoices(organizationId))
        .extracting(invoice -> invoice.get("supply_amount"))
        .containsExactly(500L);
  }

  @Test
  void 다_훑지_못하고_끊긴_달은_다음_정시가_끝난_시도가_없는_고객만_시도하고_다_훑었다고_표시한다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID done = insertCustomer(organizationId, "가");
    UUID left = insertCustomer(organizationId, "나");
    insertSeptemberEvent(
        organizationId, "tx-1", done, "{\"token\": 1000}", "2026-09-30T10:00:00+09:00");
    insertSeptemberEvent(
        organizationId, "tx-2", left, "{\"token\": 3000}", "2026-09-30T10:00:00+09:00");
    jdbcTemplate.update(
        """
        INSERT INTO invoice_finalization_attempt
          (id, organization_id, customer_id, period, result, attempted_at)
        VALUES (gen_random_uuid(), ?, ?, '2026-09', 'no_events', '2026-10-01T14:00:00+09:00')
        """,
        organizationId,
        done);

    invoiceAutoFinalizationService.finalize(organizationId, OCTOBER_1_AT_15);

    assertThat(attempts(organizationId, SEPTEMBER))
        .extracting(attempt -> attempt.get("customer_id"), attempt -> attempt.get("result"))
        .containsExactly(tuple(done, "no_events"), tuple(left, "finalized"));
    assertThat(sweptPeriods(organizationId)).contains(tuple(null, SEPTEMBER));
  }

  @Test
  void 한_고객의_확정이_실패해도_나머지는_확정되고_다음_정시가_그_고객만_다시_시도한다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID first = insertCustomer(organizationId, "가");
    UUID failing = insertCustomer(organizationId, "나");
    UUID third = insertCustomer(organizationId, "다");
    insertSeptemberEvent(
        organizationId, "tx-1", first, "{\"token\": 1000}", "2026-09-30T10:00:00+09:00");
    insertSeptemberEvent(
        organizationId,
        "tx-2",
        failing,
        "{\"token\": 2000000000000000000}",
        "2026-09-30T10:00:00+09:00");
    insertSeptemberEvent(
        organizationId, "tx-3", third, "{\"token\": 3000}", "2026-09-30T10:00:00+09:00");

    ScheduledJobResult atFourteen =
        invoiceAutoFinalizationService.finalize(organizationId, OCTOBER_1_AT_14);
    jdbcTemplate.update(
        "UPDATE price_rate SET unit_price = 0.000001 WHERE organization_id = ?", organizationId);
    ScheduledJobResult atFifteen =
        invoiceAutoFinalizationService.finalize(organizationId, OCTOBER_1_AT_15);

    assertThat(atFourteen).isEqualTo(new ScheduledJobResult(2, 0, 1));
    assertThat(atFifteen).isEqualTo(new ScheduledJobResult(1, 0, 0));
    assertThat(attempts(organizationId, SEPTEMBER))
        .extracting(attempt -> attempt.get("customer_id"), attempt -> attempt.get("result"))
        .containsExactly(
            tuple(first, "finalized"),
            tuple(failing, "failed"),
            tuple(third, "finalized"),
            tuple(failing, "finalized"));
    assertThat(reason(organizationId, failing, SEPTEMBER)).contains("ArithmeticException");
  }

  @Test
  void 도입사_계산이_실패하면_대상_고객을_실패로_남기고_다음_정시에_또_실패해도_실패_기록을_새로_쌓지_않는다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID overflowing = insertCustomer(organizationId, "가");
    UUID quiet = insertCustomer(organizationId, "나");
    insertSeptemberEvent(
        organizationId,
        "tx-1",
        overflowing,
        "{\"token\": 100000000000000000000}",
        "2026-09-30T10:00:00+09:00");

    ScheduledJobResult atFourteen =
        invoiceAutoFinalizationService.finalize(organizationId, OCTOBER_1_AT_14);
    ScheduledJobResult atFifteen =
        invoiceAutoFinalizationService.finalize(organizationId, OCTOBER_1_AT_15);

    assertThat(atFourteen).isEqualTo(new ScheduledJobResult(0, 0, 2));
    assertThat(atFifteen).isEqualTo(new ScheduledJobResult(0, 0, 2));
    assertThat(attempts(organizationId, SEPTEMBER))
        .extracting(attempt -> attempt.get("customer_id"), attempt -> attempt.get("result"))
        .containsExactly(tuple(overflowing, "failed"), tuple(quiet, "failed"));
  }

  @Test
  void 실패한_고객과_달은_다시_확정될_때까지_미확정_지표에_실패로_잡힌다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID failing = insertCustomer(organizationId, "가");
    insertSeptemberEvent(
        organizationId,
        "tx-1",
        failing,
        "{\"token\": 2000000000000000000}",
        "2026-09-30T10:00:00+09:00");
    double before = unfinalized("legacy", "failed");

    invoiceAutoFinalizationService.finalize(organizationId, OCTOBER_1_AT_14);
    double afterFailure = unfinalized("legacy", "failed");
    jdbcTemplate.update(
        "UPDATE price_rate SET unit_price = 0.000001 WHERE organization_id = ?", organizationId);
    invoiceAutoFinalizationService.finalize(organizationId, OCTOBER_1_AT_15);

    assertThat(afterFailure).isEqualTo(before + 1);
    assertThat(unfinalized("legacy", "failed")).isEqualTo(before);
  }

  @Test
  void 보류된_고객을_수동으로_확정하면_미확정_지표의_보류에서_빠지고_0원으로_끝난_고객은_보류로_세지_않는다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID mixed = insertCustomer(organizationId, "가");
    UUID unpricedOnly = insertCustomer(organizationId, "나");
    insertSeptemberEvent(
        organizationId, "tx-1", mixed, "{\"token\": 1000}", "2026-09-30T10:00:00+09:00");
    insertSeptemberEvent(
        organizationId,
        "tx-2",
        mixed,
        "{\"token\": 500, \"model\": \"new\"}",
        "2026-09-30T10:00:00+09:00");
    insertSeptemberEvent(
        organizationId,
        "tx-3",
        unpricedOnly,
        "{\"token\": 500, \"model\": \"new\"}",
        "2026-09-30T10:00:00+09:00");
    double before = unfinalized("legacy", "held");

    invoiceAutoFinalizationService.finalize(organizationId, OCTOBER_1_AT_14);
    double afterHeld = unfinalized("legacy", "held");
    invoiceFinalizationService.finalize(organizationId, YearMonth.of(2026, 9));

    assertThat(afterHeld).isEqualTo(before + 1);
    assertThat(unfinalized("legacy", "held")).isEqualTo(before);
  }

  @Test
  void 실패한_고객이_다음_정시에_0원으로_끝나면_미확정_지표의_실패에서_빠진다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID failing = insertCustomer(organizationId, "가");
    insertSeptemberEvent(
        organizationId,
        "tx-1",
        failing,
        "{\"token\": 2000000000000000000}",
        "2026-09-30T10:00:00+09:00");
    double before = unfinalized("legacy", "failed");

    invoiceAutoFinalizationService.finalize(organizationId, OCTOBER_1_AT_14);
    double afterFailure = unfinalized("legacy", "failed");
    jdbcTemplate.update(
        "UPDATE price_rate SET unit_price = 0 WHERE organization_id = ?", organizationId);
    invoiceAutoFinalizationService.finalize(organizationId, OCTOBER_1_AT_15);

    assertThat(afterFailure).isEqualTo(before + 1);
    assertThat(attempts(organizationId, SEPTEMBER))
        .extracting(attempt -> attempt.get("result"))
        .containsExactly("failed", "zero_amount");
    assertThat(unfinalized("legacy", "failed")).isEqualTo(before);
  }

  @Test
  void test_환경에서_실패한_고객과_달은_미확정_지표에_test_환경으로만_잡힌다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID testEnvironmentId = environmentId(organizationId, "test");
    insertBillableMetric(organizationId, testEnvironmentId);
    insertPrice(organizationId, testEnvironmentId, null, "{\"model\": null}", "0.5", "2000-01");
    UUID failing = insertCustomer(organizationId, testEnvironmentId, "가", REGISTERED_AT);
    insertEvent(
        organizationId,
        testEnvironmentId,
        "tx-1",
        failing,
        "{\"token\": 2000000000000000000}",
        "2026-09-30T10:00:00+09:00",
        "2026-09-30T10:00:00+09:00");
    double testBefore = unfinalized("test", "failed");
    double legacyBefore = unfinalized("legacy", "failed");
    double liveBefore = unfinalized("live", "failed");

    invoiceAutoFinalizationService.finalize(organizationId, OCTOBER_1_AT_14);

    assertThat(unfinalized("test", "failed")).isEqualTo(testBefore + 1);
    assertThat(unfinalized("legacy", "failed")).isEqualTo(legacyBefore);
    assertThat(unfinalized("live", "failed")).isEqualTo(liveBefore);
  }

  @Test
  void 수동으로_먼저_확정한_고객은_건너뛰고_인보이스는_한_장이다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(organizationId, "아크메");
    insertSeptemberEvent(
        organizationId, "tx-1", acme, "{\"token\": 3299}", "2026-09-30T10:00:00+09:00");
    invoiceFinalizationService.finalize(organizationId, YearMonth.of(2026, 9));

    ScheduledJobResult result =
        invoiceAutoFinalizationService.finalize(organizationId, OCTOBER_1_AT_14);

    assertThat(result).isEqualTo(new ScheduledJobResult(0, 0, 0));
    assertThat(invoices(organizationId)).hasSize(1);
    assertThat(attempts(organizationId, SEPTEMBER))
        .extracting(attempt -> attempt.get("result"))
        .containsExactly("skipped");
  }

  @Test
  void 확정_시각에_꺼져_있다가_며칠_뒤_켜져도_그_달의_마감_시각까지_받은_이벤트로_확정한다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(organizationId, "아크메");
    insertSeptemberEvent(
        organizationId, "tx-1", acme, "{\"token\": 3299}", "2026-09-30T10:00:00+09:00");
    insertSeptemberEvent(
        organizationId, "tx-2", acme, "{\"token\": 1000}", "2026-10-04T10:30:00+09:00");

    invoiceAutoFinalizationService.finalize(
        organizationId, OffsetDateTime.parse("2026-10-04T11:00:00+09:00"));

    assertThat(invoices(organizationId))
        .singleElement()
        .satisfies(
            invoice -> {
              assertThat(invoice.get("supply_amount")).isEqualTo(1640L);
              assertThat(invoice.get("cutoff_at"))
                  .isEqualTo(OffsetDateTime.parse("2026-10-01T13:00:00+09:00"));
            });
  }

  @Test
  void 확정_시각이_지난_달은_몇_달_전이어도_각_달의_마감_기준으로_확정한다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID acme = insertCustomer(organizationId, "아크메");
    insertEvent(
        organizationId,
        null,
        "tx-1",
        acme,
        "{\"token\": 1000}",
        "2026-08-31T10:00:00+09:00",
        "2026-08-31T10:00:00+09:00");
    insertSeptemberEvent(
        organizationId, "tx-2", acme, "{\"token\": 3299}", "2026-09-30T10:00:00+09:00");

    invoiceAutoFinalizationService.finalize(
        organizationId, OffsetDateTime.parse("2026-10-26T10:00:00+09:00"));

    assertThat(invoices(organizationId))
        .extracting(invoice -> invoice.get("period"), invoice -> invoice.get("cutoff_at"))
        .containsExactlyInAnyOrder(
            tuple("2026-08", OffsetDateTime.parse("2026-09-01T13:00:00+09:00")),
            tuple("2026-09", OffsetDateTime.parse("2026-10-01T13:00:00+09:00")));
  }

  @Test
  void 그_달_마감_뒤에_등록된_고객은_그_달의_대상이_아니다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    insertCustomer(organizationId, null, "아크메", OffsetDateTime.parse("2026-10-01T13:00:00+09:00"));

    invoiceAutoFinalizationService.finalize(organizationId, OCTOBER_1_AT_14);

    assertThat(attempts(organizationId, SEPTEMBER)).isEmpty();
  }

  @Test
  void 환경_구분_이전_범위와_각_환경을_따로_확정하고_시도와_다_훑은_표시를_그_환경에_남긴다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID liveEnvironmentId = environmentId(organizationId, "live");
    UUID testEnvironmentId = environmentId(organizationId, "test");
    insertBillableMetric(organizationId, liveEnvironmentId);
    insertPrice(organizationId, liveEnvironmentId, null, "{\"model\": null}", "2", "2000-01");
    UUID legacy = insertCustomer(organizationId, "가");
    UUID live = insertCustomer(organizationId, liveEnvironmentId, "나", REGISTERED_AT);
    UUID test = insertCustomer(organizationId, testEnvironmentId, "다", REGISTERED_AT);
    insertSeptemberEvent(
        organizationId, "tx-1", legacy, "{\"token\": 1000}", "2026-09-30T10:00:00+09:00");
    insertEvent(
        organizationId,
        liveEnvironmentId,
        "tx-2",
        live,
        "{\"token\": 1000}",
        "2026-09-30T10:00:00+09:00",
        "2026-09-30T10:00:00+09:00");

    ScheduledJobResult result =
        invoiceAutoFinalizationService.finalize(organizationId, OCTOBER_1_AT_14);

    assertThat(result).isEqualTo(new ScheduledJobResult(2, 0, 0));
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT customer_id, environment_id, supply_amount FROM invoice"
                    + " WHERE organization_id = ?",
                organizationId))
        .extracting(
            invoice -> invoice.get("customer_id"),
            invoice -> invoice.get("environment_id"),
            invoice -> invoice.get("supply_amount"))
        .containsExactlyInAnyOrder(
            tuple(legacy, null, 500L), tuple(live, liveEnvironmentId, 2000L));
    assertThat(attempts(organizationId, SEPTEMBER))
        .extracting(
            attempt -> attempt.get("customer_id"),
            attempt -> attempt.get("environment_id"),
            attempt -> attempt.get("result"))
        .containsExactlyInAnyOrder(
            tuple(legacy, null, "finalized"),
            tuple(live, liveEnvironmentId, "finalized"),
            tuple(test, testEnvironmentId, "no_events"));
    assertThat(sweptPeriods(organizationId))
        .contains(
            tuple(null, SEPTEMBER),
            tuple(liveEnvironmentId, SEPTEMBER),
            tuple(testEnvironmentId, SEPTEMBER));
  }

  @Test
  void live_환경에서_실패한_고객은_다음_정시에_그_환경에서_다시_시도한다() {
    UUID organizationId = organizationWithTokenBillableMetric();
    UUID liveEnvironmentId = environmentId(organizationId, "live");
    insertBillableMetric(organizationId, liveEnvironmentId);
    insertPrice(organizationId, liveEnvironmentId, null, "{\"model\": null}", "0.5", "2000-01");
    UUID live = insertCustomer(organizationId, liveEnvironmentId, "가", REGISTERED_AT);
    insertEvent(
        organizationId,
        liveEnvironmentId,
        "tx-1",
        live,
        "{\"token\": 2000000000000000000}",
        "2026-09-30T10:00:00+09:00",
        "2026-09-30T10:00:00+09:00");

    ScheduledJobResult atFourteen =
        invoiceAutoFinalizationService.finalize(organizationId, OCTOBER_1_AT_14);
    jdbcTemplate.update(
        "UPDATE price_rate SET unit_price = 0.000001 WHERE organization_id = ?", organizationId);
    ScheduledJobResult atFifteen =
        invoiceAutoFinalizationService.finalize(organizationId, OCTOBER_1_AT_15);

    assertThat(atFourteen).isEqualTo(new ScheduledJobResult(0, 0, 1));
    assertThat(atFifteen).isEqualTo(new ScheduledJobResult(1, 0, 0));
    assertThat(attempts(organizationId, SEPTEMBER))
        .extracting(
            attempt -> attempt.get("customer_id"),
            attempt -> attempt.get("environment_id"),
            attempt -> attempt.get("result"))
        .containsExactly(
            tuple(live, liveEnvironmentId, "failed"), tuple(live, liveEnvironmentId, "finalized"));
  }

  @Test
  void 꺼져_있으면_정시가_되어도_실행_기록을_남기지_않는다() {
    int before = jobRunCount();

    invoiceAutoFinalizationService.run();

    assertThat(jobRunCount()).isEqualTo(before);
  }

  private double unfinalized(String environment, String result) {
    invoiceFinalizationMetrics.refresh();
    return meterRegistry
        .get("meterengine.invoice.unfinalized")
        .tag("environment", environment)
        .tag("result", result)
        .gauge()
        .value();
  }

  private int jobRunCount() {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM scheduled_job_run WHERE job_name = 'invoice_auto_finalize'",
        Integer.class);
  }

  private List<Map<String, Object>> invoices(UUID organizationId) {
    return jdbcTemplate
        .queryForList(
            """
            SELECT customer_id, period, supply_amount, cutoff_at, finalized_at
            FROM invoice
            WHERE organization_id = ?
            """,
            organizationId)
        .stream()
        .map(
            row ->
                Map.of(
                    "customer_id", row.get("customer_id"),
                    "period", row.get("period"),
                    "supply_amount", row.get("supply_amount"),
                    "cutoff_at", toOffsetDateTime(row.get("cutoff_at")),
                    "finalized_at", toOffsetDateTime(row.get("finalized_at"))))
        .toList();
  }

  private List<Map<String, Object>> attempts(UUID organizationId, String period) {
    return jdbcTemplate.queryForList(
        """
        SELECT a.customer_id, a.environment_id, a.result, a.reason
        FROM invoice_finalization_attempt a
        JOIN customer c ON c.id = a.customer_id
        WHERE a.organization_id = ? AND a.period = ?
        ORDER BY a.attempted_at, c.name
        """,
        organizationId,
        period);
  }

  private List<Tuple> sweptPeriods(UUID organizationId) {
    return jdbcTemplate
        .queryForList(
            "SELECT environment_id, period FROM invoice_finalization_month"
                + " WHERE organization_id = ?",
            organizationId)
        .stream()
        .map(row -> tuple(row.get("environment_id"), row.get("period")))
        .toList();
  }

  private String reason(UUID organizationId, UUID customerId, String period) {
    return jdbcTemplate.queryForObject(
        """
        SELECT reason
        FROM invoice_finalization_attempt
        WHERE organization_id = ? AND customer_id = ? AND period = ? AND reason IS NOT NULL
        ORDER BY attempted_at
        LIMIT 1
        """,
        String.class,
        organizationId,
        customerId,
        period);
  }

  private static OffsetDateTime toOffsetDateTime(Object timestamp) {
    return OffsetDateTime.ofInstant(
        ((java.sql.Timestamp) timestamp).toInstant(), OCTOBER_1_AT_14.getOffset());
  }

  private UUID environmentId(UUID organizationId, String mode) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM organization_environment WHERE organization_id = ? AND mode = ?",
        UUID.class,
        organizationId,
        mode);
  }

  // 환경 구분 이전 범위에 토큰 사용량 미터와 model이 없는 사용량의 단가를 가진 도입사를 만든다.
  private UUID organizationWithTokenBillableMetric() {
    UUID organizationId =
        jdbcTemplate.queryForObject(
            "INSERT INTO organization (name) VALUES ('자동 확정 도입사') RETURNING id", UUID.class);
    insertBillableMetric(organizationId, null);
    insertPrice(organizationId, "{\"model\": null}", "0.5");
    return organizationId;
  }

  private void insertBillableMetric(UUID organizationId, UUID environmentId) {
    jdbcTemplate.update(
        """
        INSERT INTO billable_metric
          (organization_id, environment_id, environment_scope_id, code, name, event_type,
           aggregation, target_property, dimension_properties)
        VALUES (?, ?, COALESCE(?, '00000000-0000-0000-0000-000000000000'::uuid), 'token-usage',
                '토큰 사용량', 'chat_completion', 'sum', 'token', '{model}')
        """,
        organizationId,
        environmentId,
        environmentId);
  }

  private void insertPrice(UUID organizationId, String dimensionValues, String unitPrice) {
    insertPrice(organizationId, null, dimensionValues, unitPrice, "2000-01");
  }

  private void insertPrice(
      UUID organizationId,
      UUID customerId,
      String dimensionValues,
      String unitPrice,
      String effectiveFrom) {
    insertPrice(organizationId, null, customerId, dimensionValues, unitPrice, effectiveFrom);
  }

  private void insertPrice(
      UUID organizationId,
      UUID environmentId,
      UUID customerId,
      String dimensionValues,
      String unitPrice,
      String effectiveFrom) {
    jdbcTemplate.update(
        """
        INSERT INTO price_rate
          (organization_id, environment_id, customer_id, billable_metric_code, dimension_values,
           unit_price, effective_from)
        VALUES (?, ?, ?, 'token-usage', ?::jsonb, ?, ?)
        """,
        organizationId,
        environmentId,
        customerId,
        dimensionValues,
        new BigDecimal(unitPrice),
        effectiveFrom);
  }

  private UUID insertCustomer(UUID organizationId, String name) {
    return insertCustomer(organizationId, null, name, REGISTERED_AT);
  }

  private UUID insertCustomer(
      UUID organizationId, UUID environmentId, String name, OffsetDateTime createdAt) {
    return jdbcTemplate.queryForObject(
        """
        INSERT INTO customer (organization_id, environment_id, name, created_at)
        VALUES (?, ?, ?, ?)
        RETURNING id
        """,
        UUID.class,
        organizationId,
        environmentId,
        name,
        createdAt);
  }

  private void insertSeptemberEvent(
      UUID organizationId,
      String transactionId,
      UUID customerId,
      String propertiesJson,
      String receivedAt) {
    insertEvent(
        organizationId,
        null,
        transactionId,
        customerId,
        propertiesJson,
        "2026-09-30T10:00:00+09:00",
        receivedAt);
  }

  private void insertEvent(
      UUID organizationId,
      UUID environmentId,
      String transactionId,
      UUID customerId,
      String propertiesJson,
      String occurredAt,
      String receivedAt) {
    TestEvents.withGivenReceivedAt(
        jdbcTemplate,
        () ->
            jdbcTemplate.update(
                """
                INSERT INTO event
                  (organization_id, environment_id, transaction_id, customer_id, type, properties,
                   occurred_at, received_at)
                VALUES (?, ?, ?, ?, 'chat_completion', ?::jsonb, ?, ?)
                """,
                organizationId,
                environmentId,
                transactionId,
                customerId,
                propertiesJson,
                OffsetDateTime.parse(occurredAt),
                OffsetDateTime.parse(receivedAt)));
  }
}
