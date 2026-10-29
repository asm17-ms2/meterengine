package com.meterengine.metric;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meterengine.TestcontainersConfiguration;
import com.meterengine.event.repository.UnbilledEventRepository;
import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.InvalidRequestException;
import com.meterengine.metric.dto.CreateBillableMetricRequest;
import com.meterengine.metric.dto.CustomerUsage;
import com.meterengine.metric.dto.UpdateBillableMetricRequest;
import com.meterengine.metric.service.BillableMetricService;
import com.meterengine.metric.service.BillableMetricUsageService;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class MetricAggregationIntegrationTest {
  private static final YearMonth MONTH = YearMonth.of(2026, 8);
  private static final OffsetDateTime START = OffsetDateTime.parse("2026-08-01T00:00:00+09:00");
  private static final OffsetDateTime END = OffsetDateTime.parse("2026-09-01T00:00:00+09:00");

  @Autowired private JdbcTemplate jdbc;
  @Autowired private jakarta.persistence.EntityManager entityManager;
  @Autowired private BillableMetricService metrics;
  @Autowired private BillableMetricUsageService usage;
  @Autowired private UnbilledEventRepository diagnostics;
  @Autowired private com.meterengine.invoice.service.DraftInvoiceService draftInvoices;
  @Autowired private com.meterengine.invoice.service.InvoiceFinalizationService finalization;
  @Autowired private com.meterengine.invoice.service.InvoiceService invoices;
  private UUID organization;
  private UUID customer;

  @BeforeEach
  void setUp() {
    organization = UUID.randomUUID();
    customer = UUID.randomUUID();
    jdbc.update("INSERT INTO organization(id, name) VALUES (?, '집계 검증')", organization);
    jdbc.update(
        "INSERT INTO customer(id, organization_id, name) VALUES (?, ?, '검증 고객')",
        customer,
        organization);
  }

  @Test
  void 집계별로_유효한_원본_이벤트만_계산한다() {
    for (String sample :
        List.of(
            "sum,93",
            "count,8",
            "count_distinct,5",
            "min,11",
            "max,40",
            "avg,23.25",
            "first,11",
            "last,40",
            "median,21",
            "percentile,37.15")) {
      setUp();
      String[] values = sample.split(",");
      aggregatesOnlyEligibleEventsForEachMethod(values[0], values[1]);
    }
  }

  private void aggregatesOnlyEligibleEventsForEachMethod(String aggregation, String expected) {
    create(
        "metric",
        aggregation,
        List.of(),
        null,
        null,
        null,
        "percentile".equals(aggregation) ? new BigDecimal("95") : null);
    String[] values = {"11", "21", "21", "40", "\"21\"", "true", "{\"nested\":21}", null};
    for (int index = 0; index < values.length; index++) {
      String property = values[index] == null ? "{}" : "{\"value\":" + values[index] + "}";
      event("tx-" + index, property, START.plusSeconds(index));
    }
    event("before", "{\"value\":999}", START.minusNanos(1000));
    event("after", "{\"value\":999}", END);
    jdbc.update(
        "INSERT INTO event(organization_id, transaction_id, customer_id, type, properties, occurred_at) VALUES (?, 'other-type', ?, 'other', '{\"value\":999}', ?)",
        organization,
        customer,
        START);
    CustomerUsage result = quantity("metric");
    assertThat(result.rawQuantity()).isEqualByComparingTo(expected);
    assertThat(result.quantity()).isEqualByComparingTo(expected);
    assertThat(result.dimensionUsages()).hasSize(1);
    assertThat(result.dimensionUsages().getFirst().rawQuantity()).isEqualByComparingTo(expected);
  }

  @Test
  void 차원별_평균과_고객_전체_평균은_별도로_계산한다() {
    create("avg", "avg", List.of("region"), null, null, null, null);
    event("a", "{\"value\":10,\"region\":1}", START);
    event("b", "{\"value\":20,\"region\":1.0}", START.plusSeconds(1));
    event("c", "{\"value\":90,\"region\":2}", START.plusSeconds(2));
    CustomerUsage result = quantity("avg");
    assertThat(result.quantity()).isEqualByComparingTo("40");
    assertThat(result.dimensionUsages()).hasSize(2);
    assertThat(result.dimensionUsages())
        .extracting(item -> item.quantity().stripTrailingZeros())
        .containsExactly(new BigDecimal("15"), new BigDecimal("9E+1"));
  }

  @Test
  void 차원_사이에_같은_고유값이_있어도_고객_전체는_한_번만_센다() {
    create("distinct", "count_distinct", List.of("region"), null, null, null, null);
    event("a", "{\"value\":1,\"region\":\"a\"}", START);
    event("b", "{\"value\":1.0,\"region\":\"b\"}", START);
    event("c", "{\"value\":\"1\",\"region\":\"b\"}", START);
    assertThat(quantity("distinct").quantity()).isEqualByComparingTo("2");
    assertThat(
            quantity("distinct").dimensionUsages().stream()
                .map(item -> item.quantity())
                .reduce(BigDecimal.ZERO, BigDecimal::add))
        .isEqualByComparingTo("3");
  }

  @Test
  void 동일_발생시각이면_transaction_id로_첫값과_끝값을_정한다() {
    for (String sample : List.of("first,11", "last,99")) {
      setUp();
      String[] values = sample.split(",");
      ordersEqualTimestampsByTransactionId(values[0], values[1]);
    }
  }

  private void ordersEqualTimestampsByTransactionId(String aggregation, String expected) {
    create("ordered", aggregation, List.of(), null, null, null, null);
    event("z", "{\"value\":99}", START);
    event("a", "{\"value\":11}", START);
    event("nonnumeric-earlier", "{\"value\":\"bad\"}", START.minusSeconds(1));
    assertThat(quantity("ordered").quantity()).isEqualByComparingTo(expected);
  }

  @Test
  void 백분위는_double_변환_없이_소수를_보존한다() {
    for (String sample :
        List.of(
            "0,9007199254740993.000000000000000001",
            "50,9007199254740993.000000000000000002",
            "100,9007199254740993.000000000000000003")) {
      setUp();
      String[] values = sample.split(",");
      preservesPercentilePrecisionWithoutFloatingPointConversion(values[0], values[1]);
    }
  }

  private void preservesPercentilePrecisionWithoutFloatingPointConversion(
      String percentile, String expected) {
    create("percentile", "percentile", List.of(), null, null, null, new BigDecimal(percentile));
    event("a", "{\"value\":9007199254740993.000000000000000001}", START);
    event("b", "{\"value\":9007199254740993.000000000000000003}", START);
    assertThat(quantity("percentile").rawQuantity()).isEqualByComparingTo(expected);
  }

  @Test
  void 집계_후_단위와_반올림을_적용하고_원본을_보존한다() {
    for (String sample :
        List.of(
            "ceil,0,3",
            "floor,0,2",
            "round,0,2",
            "ceil,1,2.4",
            "floor,1,2.3",
            "round,1,2.3",
            "none,0,2.325")) {
      setUp();
      String[] values = sample.split(",");
      transformsAfterAggregationAndPreservesRawQuantity(
          values[0], Integer.parseInt(values[1]), values[2]);
    }
  }

  private void transformsAfterAggregationAndPreservesRawQuantity(
      String rounding, int precision, String expected) {
    create("converted", "avg", List.of(), BigDecimal.TEN, rounding, precision, null);
    event("a", "{\"value\":11}", START);
    event("b", "{\"value\":21}", START);
    event("c", "{\"value\":21}", START);
    event("d", "{\"value\":40}", START);
    CustomerUsage result = quantity("converted");
    assertThat(result.rawQuantity()).isEqualByComparingTo("23.25");
    assertThat(result.quantity()).isEqualByComparingTo(expected);
    assertThat(result.dimensionUsages().getFirst().quantity()).isEqualByComparingTo(expected);
  }

  @Test
  void 순환소수_나눗셈은_18자리로_반환한다() {
    create("third", "sum", List.of(), new BigDecimal("3"), "none", 0, null);
    event("a", "{\"value\":1}", START);
    assertThat(quantity("third").quantity()).isEqualByComparingTo("0.333333333333333333");
  }

  @Test
  void 큰_단위로_나눈_작은_양수도_반올림_없음에서_0이_되지_않는다() {
    create("tiny-unit", "sum", List.of(), new BigDecimal("3E18"), "none", 0, null);
    event("a", "{\"value\":1}", START);
    assertThat(quantity("tiny-unit").quantity())
        .isEqualByComparingTo("0.000000000000000000333333333333333333");
  }

  @Test
  void 미세한_원본의_순환소수도_최소_18개_유효숫자를_보존한다() {
    create("tiny-raw", "sum", List.of(), new BigDecimal("3"), "none", 0, null);
    event("a", "{\"value\":1E-30}", START);
    assertThat(quantity("tiny-raw").quantity())
        .isEqualByComparingTo("0.000000000000000000000000000000333333333333333333");
  }

  @Test
  void 사용량이_없으면_모든_집계가_0과_빈_차원을_반환한다() {
    for (String aggregation :
        List.of(
            "sum",
            "count",
            "count_distinct",
            "min",
            "max",
            "avg",
            "first",
            "last",
            "median",
            "percentile")) {
      create(
          aggregation,
          aggregation,
          List.of(),
          null,
          null,
          null,
          "percentile".equals(aggregation) ? BigDecimal.ZERO : null);
      assertThat(quantity(aggregation).quantity()).isEqualByComparingTo("0");
      assertThat(quantity(aggregation).rawQuantity()).isEqualByComparingTo("0");
      assertThat(quantity(aggregation).dimensionUsages()).isEmpty();
    }
  }

  @Test
  void count와_distinct의_정상_스칼라는_숫자_누락이나_음수_진단에_잡히지_않는다() {
    create("count", "count", List.of(), null, null, null, null);
    create("distinct", "count_distinct", List.of(), null, null, null, null);
    event("a", "{\"value\":-1}", START);
    event("b", "{\"value\":\"valid\"}", START);
    event("c", "{\"value\":false}", START);
    assertThat(diagnostics.findNonNumericValues(organization, START, END).eventCount()).isZero();
    assertThat(diagnostics.findNegativeQuantities(organization, START, END).eventCount()).isZero();
    event("d", "{}", START);
    assertThat(diagnostics.findNonNumericValues(organization, START, END).eventCount())
        .isEqualTo(1);
  }

  @Test
  void 이벤트가_있으면_집계와_단위와_차원을_바꾸지_못한다() {
    create("fixed", "count", List.of(), null, null, null, null);
    event("empty", "{}", START);
    List<UpdateBillableMetricRequest> changes =
        List.of(
            new UpdateBillableMetricRequest("이름", "observed", "sum", "value", List.of()),
            new UpdateBillableMetricRequest("이름", "observed", "count", null, List.of("region")),
            new UpdateBillableMetricRequest(
                "이름", "observed", "count", null, List.of(), BigDecimal.TEN, "none", 0, null),
            new UpdateBillableMetricRequest(
                "이름", "observed", "count", null, List.of(), BigDecimal.ONE, "ceil", 0, null));
    for (UpdateBillableMetricRequest change : changes) {
      assertThatThrownBy(() -> metrics.update(organization, "fixed", change))
          .isInstanceOfSatisfying(
              ConflictException.class,
              exception ->
                  assertThat(exception.getErrorCode())
                      .isEqualTo(ErrorCode.BILLABLE_METRIC_HAS_EVENTS));
    }
    assertThat(
            metrics
                .update(
                    organization,
                    "fixed",
                    new UpdateBillableMetricRequest(
                        "바뀐 이름",
                        "observed",
                        "COUNT",
                        null,
                        List.of(),
                        new BigDecimal("1.0"),
                        "NONE",
                        0,
                        null))
                .name())
        .isEqualTo("바뀐 이름");
  }

  @Test
  void 반올림이_없으면_자릿수를_0으로_정규화해_이름만_바꿀_수_있다() {
    var created =
        metrics.create(
            organization,
            new CreateBillableMetricRequest(
                "canonical", "이름", "observed", "SUM", "value", List.of(), null, "NONE", 7, null));
    assertThat(created.aggregation()).isEqualTo("sum");
    assertThat(created.roundingPrecision()).isZero();
    event("present", "{\"value\":1}", START);
    var updated =
        metrics.update(
            organization,
            "canonical",
            new UpdateBillableMetricRequest(
                "새 이름", "observed", "sum", "value", List.of(), BigDecimal.ONE, "none", 0, null));
    assertThat(updated.name()).isEqualTo("새 이름");
  }

  @Test
  void 보관_효력_기간이_겹쳐도_한_번_집계하고_공백_월은_제외한다() {
    create("periods", "sum", List.of(), null, null, null, null);
    jdbc.update(
        "UPDATE billable_metric SET active_from = '2026-10' WHERE organization_id = ? AND code = 'periods'",
        organization);
    jdbc.update(
        "INSERT INTO billable_metric_period(id, organization_id, billable_metric_code, active_from, active_until) VALUES (gen_random_uuid(), ?, 'periods', '2026-07', '2026-09'), (gen_random_uuid(), ?, 'periods', '2026-08', '2026-09')",
        organization,
        organization);
    event("aug-a", "{\"value\":10}", START);
    event("aug-b", "{\"value\":90}", START.plusSeconds(1));
    event("sep-gap", "{\"value\":999}", END);
    entityManager.clear();
    var aggregated =
        usage.aggregate(organization, MONTH).stream()
            .filter(item -> item.billableMetric().getCode().equals("periods"))
            .toList();
    assertThat(aggregated).hasSize(1);
    assertThat(aggregated.getFirst().customers()).hasSize(1);
    assertThat(quantity("periods").quantity()).isEqualByComparingTo("100");
    assertThat(usage.aggregate(organization, YearMonth.of(2026, 9))).isEmpty();
  }

  @Test
  void 잘못된_설정은_필드별로_거절한다() {
    List<CreateBillableMetricRequest> invalid =
        List.of(
            new CreateBillableMetricRequest("a", "a", "observed", "count", "value", List.of()),
            new CreateBillableMetricRequest("b", "b", "observed", "avg", null, List.of()),
            new CreateBillableMetricRequest(
                "c", "c", "observed", "sum", "value", List.of(), BigDecimal.ZERO, null, null, null),
            new CreateBillableMetricRequest(
                "d", "d", "observed", "sum", "value", List.of(), null, "bad", null, null),
            new CreateBillableMetricRequest(
                "e", "e", "observed", "sum", "value", List.of(), null, null, 13, null),
            new CreateBillableMetricRequest(
                "f", "f", "observed", "sum", "value", List.of(), null, null, null, BigDecimal.TEN),
            new CreateBillableMetricRequest("g", "g", "observed", "percentile", "value", List.of()),
            new CreateBillableMetricRequest(
                "h",
                "h",
                "observed",
                "percentile",
                "value",
                List.of(),
                null,
                null,
                null,
                new BigDecimal("101")));
    for (CreateBillableMetricRequest request : invalid)
      assertThatThrownBy(() -> metrics.create(organization, request))
          .isInstanceOfSatisfying(
              InvalidRequestException.class,
              exception -> assertThat(exception.getErrors()).hasSize(1));
  }

  @Test
  void 단위와_평균의_분수는_예정액과_확정_인보이스까지_정확히_이어진다() {
    for (String aggregation : List.of("sum", "avg")) {
      setUp();
      create(
          "fraction",
          aggregation,
          List.of(),
          "sum".equals(aggregation) ? new BigDecimal("3") : BigDecimal.ONE,
          "none",
          0,
          null);
      event("a", "{\"value\":1}", START);
      if ("avg".equals(aggregation)) {
        event("b", "{\"value\":0}", START);
        event("c", "{\"value\":0}", START);
      }
      jdbc.update(
          "INSERT INTO price_rate(organization_id, billable_metric_code, dimension_values, unit_price) VALUES (?, 'fraction', '{}', 300)",
          organization);
      CustomerUsage measured = quantity("fraction");
      assertThat(measured.rawQuantityNumerator()).isEqualByComparingTo("1");
      assertThat(measured.rawQuantityDenominator())
          .isEqualByComparingTo("avg".equals(aggregation) ? "3" : "1");
      var draft =
          draftInvoices.preview(organization, MONTH).customers().getFirst().lines().getFirst();
      assertThat(draft.amount()).isEqualTo(100);
      assertThat(draft.quantityNumerator()).isEqualByComparingTo("1");
      assertThat(draft.quantityDenominator()).isEqualByComparingTo("3");
      finalization.finalize(organization, MONTH);
      entityManager.flush();
      entityManager.clear();
      var finalized = invoices.list(organization, customer, MONTH).invoices().getFirst();
      assertThat(finalized.supplyAmount()).isEqualTo(100);
      assertThat(finalized.lines().getFirst().quantityNumerator()).isEqualByComparingTo("1");
      assertThat(finalized.lines().getFirst().quantityDenominator()).isEqualByComparingTo("3");
      assertThat(
              jdbc.queryForObject(
                  "SELECT quantity_denominator FROM invoice_line WHERE organization_id = ?",
                  BigDecimal.class,
                  organization))
          .isEqualByComparingTo("3");
      if ("sum".equals(aggregation)) {
        jdbc.update(
            "INSERT INTO invoice_line(id, organization_id, invoice_id, billable_metric_code, target_property, dimension_values, quantity, unit_price, amount, line_type, tier_index) VALUES (gen_random_uuid(), ?, ?, 'legacy-fraction', 'value', '{}', 2, 0, 0, 'per_unit', 0)",
            organization,
            finalized.id());
        entityManager.clear();
        var legacy =
            invoices.list(organization, customer, MONTH).invoices().getFirst().lines().stream()
                .filter(line -> line.quantity().compareTo(new BigDecimal("2")) == 0)
                .findFirst()
                .orElseThrow();
        assertThat(legacy.quantityNumerator()).isEqualByComparingTo("2");
        assertThat(legacy.quantityDenominator()).isEqualByComparingTo("1");
      }
    }
  }

  @Test
  void 평균_표시량이_1이어도_올림은_정확한_분수에서_2를_계산한다() {
    create("ceil-avg", "avg", List.of(), BigDecimal.ONE, "ceil", 0, null);
    event("a", "{\"value\":1}", START);
    event("b", "{\"value\":1}", START);
    event("c", "{\"value\":1.000000000000000000000000000001}", START);
    CustomerUsage result = quantity("ceil-avg");
    assertThat(result.rawQuantity()).isEqualByComparingTo("1");
    assertThat(result.quantity()).isEqualByComparingTo("2");
    assertThat(result.dimensionUsages().getFirst().quantity()).isEqualByComparingTo("2");
    jdbc.update(
        "INSERT INTO price_rate(organization_id, billable_metric_code, dimension_values, unit_price) VALUES (?, 'ceil-avg', '{}', 300)",
        organization);
    assertThat(draftInvoices.preview(organization, MONTH).totalAmount()).isEqualTo(600);
  }

  @Test
  void 작은_음수_평균도_표시량과_분자에서_부호를_잃지_않는다() {
    create("negative-avg", "avg", List.of(), null, null, null, null);
    event("a", "{\"value\":-1E-30}", START);
    event("b", "{\"value\":0}", START);
    event("c", "{\"value\":0}", START);
    CustomerUsage result = quantity("negative-avg");
    assertThat(result.rawQuantity().signum()).isEqualTo(-1);
    assertThat(result.rawQuantityNumerator()).isEqualByComparingTo("-1E-30");
    assertThat(draftInvoices.preview(organization, MONTH).customers().getFirst().negativeUsages())
        .hasSize(1);
  }

  private void create(
      String code,
      String aggregation,
      List<String> dimensions,
      BigDecimal unit,
      String rounding,
      Integer precision,
      BigDecimal percentile) {
    metrics.create(
        organization,
        new CreateBillableMetricRequest(
            code,
            code,
            "observed",
            aggregation,
            "count".equals(aggregation) ? null : "value",
            dimensions,
            unit,
            rounding,
            precision,
            percentile));
  }

  private CustomerUsage quantity(String code) {
    return usage.aggregate(organization, MONTH).stream()
        .filter(item -> item.billableMetric().getCode().equals(code))
        .findFirst()
        .orElseThrow()
        .customers()
        .getFirst();
  }

  private void event(String transaction, String properties, OffsetDateTime occurredAt) {
    jdbc.update(
        "INSERT INTO event(organization_id, transaction_id, customer_id, type, properties, occurred_at) VALUES (?, ?, ?, 'observed', ?::jsonb, ?)",
        organization,
        transaction,
        customer,
        properties,
        occurredAt);
  }
}
