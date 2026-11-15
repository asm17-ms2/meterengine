package com.meterengine.global;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.TestcontainersConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@Transactional
class SeedDataTest {

  private static final String SEED_ORGANIZATION_ID = "d7cee55d-8c82-4afc-b996-6749d8b26a4e";

  private static final String SEED_SCRIPT = "db/local/R__local_seed.sql";

  private static final String LEGACY_SEED_PLACEHOLDER = "db/migration/R__seed.sql";

  private static final int CUSTOMERS = 2 + 3;
  private static final int BILLABLE_METRICS = 1 + 3 + 2;
  private static final List<String> MODELS =
      List.of(
          "claude-fable-5",
          "claude-fable-5-1",
          "claude-haiku-4-5",
          "claude-haiku-4-5-20251001",
          "claude-haiku-5-5",
          "claude-opus-5",
          "claude-opus-5-5",
          "claude-sonnet-5",
          "claude-sonnet-5-5");
  private static final int MODEL_PRICED_BILLABLE_METRICS = 2;
  private static final int PRICE_RATES =
      BILLABLE_METRICS
          - MODEL_PRICED_BILLABLE_METRICS
          + MODEL_PRICED_BILLABLE_METRICS * MODELS.size();

  // 시험 DB에 남는 다른 조직 행과 섞이지 않게 시드 조직의 테스트 환경만 센다.
  private static final String IN_TEST_ENVIRONMENT =
      " WHERE organization_id = '"
          + SEED_ORGANIZATION_ID
          + "'::uuid AND environment_id = (SELECT id FROM organization_environment"
          + " WHERE organization_id = '"
          + SEED_ORGANIZATION_ID
          + "'::uuid AND mode = 'test')";

  private static final String IN_LEGACY =
      " WHERE organization_id = '" + SEED_ORGANIZATION_ID + "'::uuid AND environment_id IS NULL";

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void 서버_설정의_마이그레이션은_시드를_넣지_않는다() {
    assertThat(new ClassPathResource(LEGACY_SEED_PLACEHOLDER).exists()).isTrue();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM organization WHERE id = ?::uuid",
                Integer.class,
                SEED_ORGANIZATION_ID))
        .isZero();
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT script FROM flyway_schema_history WHERE version IS NULL", String.class))
        .containsExactly("R__seed.sql");
  }

  @Test
  void 시드는_고객과_미터와_가격을_모두_테스트_환경에_넣는다() {
    runSeed();

    assertThat(countInTestEnvironment("customer")).isEqualTo(CUSTOMERS);
    assertThat(countInTestEnvironment("billable_metric")).isEqualTo(BILLABLE_METRICS);
    assertThat(countInTestEnvironment("price_rate")).isEqualTo(PRICE_RATES);
    assertThat(countInLegacy("customer")).isZero();
    assertThat(countInLegacy("billable_metric")).isZero();
    assertThat(countInLegacy("price_rate")).isZero();
  }

  @Test
  void 시드를_두_번_실행해도_행이_늘지_않는다() {
    runSeed();
    runSeed();

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM organization WHERE id = ?::uuid",
                Integer.class,
                SEED_ORGANIZATION_ID))
        .isEqualTo(1);
    assertThat(countInTestEnvironment("customer")).isEqualTo(CUSTOMERS);
    assertThat(countInTestEnvironment("billable_metric")).isEqualTo(BILLABLE_METRICS);
    assertThat(countInTestEnvironment("price_rate")).isEqualTo(PRICE_RATES);
  }

  @Test
  void 값이_바뀐_상태에서_시드를_다시_실행하면_파일_기준으로_되돌아온다() {
    runSeed();
    jdbcTemplate.update("UPDATE billable_metric SET name = '손으로 바꾼 이름'" + IN_TEST_ENVIRONMENT);
    jdbcTemplate.update("UPDATE price_rate SET unit_price = 999" + IN_TEST_ENVIRONMENT);

    runSeed();

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT name FROM billable_metric"
                    + IN_TEST_ENVIRONMENT
                    + " AND code = 'token-usage'",
                String.class))
        .isEqualTo("토큰 사용량");
    assertThat(unitPrice("token-usage", "{}")).isEqualByComparingTo("0.007");
    assertThat(countInTestEnvironment("billable_metric")).isEqualTo(BILLABLE_METRICS);
    assertThat(countInTestEnvironment("price_rate")).isEqualTo(PRICE_RATES);
  }

  @Test
  void 백분위로_바꾼_시드_미터도_시드를_다시_실행하면_백분위가_비워진_합계로_돌아온다() {
    runSeed();
    jdbcTemplate.update(
        "UPDATE billable_metric SET aggregation = 'percentile', percentile = 95"
            + IN_TEST_ENVIRONMENT
            + " AND code = 'token-usage'");

    runSeed();

    assertThat(
            jdbcTemplate.queryForMap(
                "SELECT aggregation, percentile FROM billable_metric"
                    + IN_TEST_ENVIRONMENT
                    + " AND code = 'token-usage'"))
        .containsEntry("aggregation", "sum")
        .containsEntry("percentile", null);
  }

  @Test
  void 입력과_출력_토큰은_model별_단가이고_나머지_미터는_무차원_단가_1행이다() {
    runSeed();

    assertThat(
            jdbcTemplate.queryForList(
                "SELECT code || ' ' || dimension_properties::text FROM billable_metric"
                    + IN_TEST_ENVIRONMENT,
                String.class))
        .containsExactlyInAnyOrder(
            "token-usage {}",
            "input-tokens {model}",
            "output-tokens {model}",
            "network-egress {}",
            "cache-read-tokens {}",
            "cache-creation-tokens {}");
    assertThat(
            jdbcTemplate.queryForList(
                "SELECT billable_metric_code FROM price_rate"
                    + IN_TEST_ENVIRONMENT
                    + " AND dimension_values = '{}'",
                String.class))
        .containsExactlyInAnyOrder(
            "token-usage", "network-egress", "cache-read-tokens", "cache-creation-tokens");
    assertThat(modelsPricedFor("input-tokens")).isEqualTo(MODELS);
    assertThat(modelsPricedFor("output-tokens")).isEqualTo(MODELS);

    assertThat(unitPrice("token-usage", "{}")).isEqualByComparingTo("0.007");
    assertThat(unitPrice("input-tokens", "{\"model\": \"claude-opus-5-5\"}"))
        .isEqualByComparingTo("0.00636");
    assertThat(unitPrice("output-tokens", "{\"model\": \"claude-opus-5-5\"}"))
        .isEqualByComparingTo("0.0318");
  }

  @Test
  void 시드_재실행은_무차원_단가를_보관하고_model_단가를_활성으로_둔다() {
    runSeed();
    jdbcTemplate.update(
        "DELETE FROM price_rate"
            + IN_TEST_ENVIRONMENT
            + " AND billable_metric_code IN ('input-tokens', 'output-tokens')");
    jdbcTemplate.update(
        "UPDATE billable_metric SET dimension_properties = '{}'"
            + IN_TEST_ENVIRONMENT
            + " AND code IN ('input-tokens', 'output-tokens')");
    jdbcTemplate.update(
        "INSERT INTO price_rate (organization_id, environment_id, billable_metric_code,"
            + " dimension_values, unit_price) SELECT organization_id, id, code, '{}', price"
            + " FROM organization_environment CROSS JOIN (VALUES ('input-tokens', 0.007),"
            + " ('output-tokens', 0.035)) AS seed(code, price)"
            + " WHERE organization_id = ?::uuid AND mode = 'test'",
        SEED_ORGANIZATION_ID);

    runSeed();

    assertThat(
            jdbcTemplate.queryForList(
                "SELECT dimension_properties::text FROM billable_metric"
                    + IN_TEST_ENVIRONMENT
                    + " AND code IN ('input-tokens', 'output-tokens')",
                String.class))
        .containsOnly("{model}");
    assertThat(modelsPricedFor("input-tokens")).isEqualTo(MODELS);
    assertThat(modelsPricedFor("output-tokens")).isEqualTo(MODELS);
    assertThat(countInTestEnvironment("price_rate")).isEqualTo(PRICE_RATES + 2);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM price_rate"
                    + IN_TEST_ENVIRONMENT
                    + " AND billable_metric_code IN ('input-tokens', 'output-tokens')"
                    + " AND dimension_values = '{}' AND archived_at IS NOT NULL",
                Integer.class))
        .isEqualTo(2);
  }

  @Test
  void 보관한_시드_단가는_시드를_다시_실행해도_살아나지_않는다() {
    runSeed();
    jdbcTemplate.update(
        "UPDATE price_rate SET archived_at = now(), archived_from = '2026-12'"
            + IN_TEST_ENVIRONMENT
            + " AND billable_metric_code = 'token-usage'");

    runSeed();

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM price_rate"
                    + IN_TEST_ENVIRONMENT
                    + " AND billable_metric_code = 'token-usage' AND archived_at IS NULL",
                Integer.class))
        .isZero();
    assertThat(countInTestEnvironment("price_rate")).isEqualTo(PRICE_RATES);
  }

  @Test
  void legacy에_예전_시드가_있어도_시드가_실패하지_않고_legacy_행은_그대로_둔다() {
    jdbcTemplate.update(
        "INSERT INTO organization (id, name) VALUES (?::uuid, '데모 도입사')", SEED_ORGANIZATION_ID);
    jdbcTemplate.update(
        "INSERT INTO customer (id, organization_id, name) VALUES"
            + " ('a728e7b6-d82b-4f3c-a960-a66a02794c1d', ?::uuid, '예전 이름')",
        SEED_ORGANIZATION_ID);
    jdbcTemplate.update(
        "INSERT INTO billable_metric (organization_id, code, name, event_type, aggregation,"
            + " target_property, dimension_properties) VALUES (?::uuid, 'token-usage',"
            + " '예전 미터', 'chat_completion', 'sum', 'token', '{}')",
        SEED_ORGANIZATION_ID);
    jdbcTemplate.update(
        "INSERT INTO price_rate (organization_id, billable_metric_code, dimension_values,"
            + " unit_price) VALUES (?::uuid, 'token-usage', '{}', 0.5)",
        SEED_ORGANIZATION_ID);

    runSeed();

    assertThat(countInLegacy("customer")).isEqualTo(1);
    assertThat(countInLegacy("billable_metric")).isEqualTo(1);
    assertThat(countInLegacy("price_rate")).isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT name FROM customer WHERE id = 'a728e7b6-d82b-4f3c-a960-a66a02794c1d'",
                String.class))
        .isEqualTo("예전 이름");
    assertThat(countInTestEnvironment("customer")).isEqualTo(CUSTOMERS - 1);
    assertThat(countInTestEnvironment("billable_metric")).isEqualTo(BILLABLE_METRICS);
    assertThat(countInTestEnvironment("price_rate")).isEqualTo(PRICE_RATES);
  }

  @Test
  void 시드_미터는_token을_SUM으로_집계하도록_설정된다() {
    runSeed();

    var billableMetric =
        jdbcTemplate.queryForMap(
            "SELECT event_type, aggregation, target_property FROM billable_metric"
                + IN_TEST_ENVIRONMENT
                + " AND code = 'token-usage'");

    assertThat(billableMetric.get("event_type")).isEqualTo("chat_completion");
    assertThat(billableMetric.get("aggregation")).isEqualTo("sum");
    assertThat(billableMetric.get("target_property")).isEqualTo("token");
  }

  @Test
  void 데모_확장_시드가_sample_events가_쓰는_고객과_미터를_넣는다() {
    runSeed();

    assertThat(
            jdbcTemplate.queryForList(
                "SELECT id::text FROM customer" + IN_TEST_ENVIRONMENT, String.class))
        .contains(
            "35bc8d12-9d38-57ab-bc9b-bbd35d779a26",
            "008cd6a7-6ff9-505d-9421-747e7d2d62aa",
            "8c525322-2712-5b5f-aa1a-435a7ff9fe97");

    assertThat(
            jdbcTemplate.queryForList(
                "SELECT code FROM billable_metric"
                    + IN_TEST_ENVIRONMENT
                    + " AND event_type = 'llm_request'",
                String.class))
        .containsExactlyInAnyOrder(
            "input-tokens", "output-tokens", "cache-read-tokens", "cache-creation-tokens");
  }

  @Test
  void 캐시_미터가_브리지가_보내는_키를_잰다() {
    runSeed();

    var rows =
        jdbcTemplate.queryForList(
            "SELECT code, target_property, unit_price FROM billable_metric m"
                + " JOIN price_rate r ON r.organization_id = m.organization_id"
                + " AND r.environment_scope_id = m.environment_scope_id"
                + " AND r.billable_metric_code = m.code"
                + " WHERE m.code IN ('cache-read-tokens', 'cache-creation-tokens')"
                + " ORDER BY code");

    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).get("target_property")).isEqualTo("cache_creation_tokens");
    assertThat((BigDecimal) rows.get(0).get("unit_price")).isEqualByComparingTo("0.00875");
    assertThat(rows.get(1).get("target_property")).isEqualTo("cache_read_tokens");
    assertThat((BigDecimal) rows.get(1).get("unit_price")).isEqualByComparingTo("0.0007");
  }

  private void runSeed() {
    jdbcTemplate.execute(readScript(SEED_SCRIPT));
  }

  private List<String> modelsPricedFor(String billableMetricCode) {
    return jdbcTemplate.queryForList(
        "SELECT dimension_values ->> 'model' FROM price_rate"
            + IN_TEST_ENVIRONMENT
            + " AND billable_metric_code = ? AND archived_at IS NULL ORDER BY 1",
        String.class,
        billableMetricCode);
  }

  private BigDecimal unitPrice(String billableMetricCode, String dimensionValues) {
    return jdbcTemplate.queryForObject(
        "SELECT unit_price FROM price_rate"
            + IN_TEST_ENVIRONMENT
            + " AND billable_metric_code = ? AND dimension_values = ?::jsonb",
        BigDecimal.class,
        billableMetricCode,
        dimensionValues);
  }

  private Integer countInTestEnvironment(String table) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM " + table + IN_TEST_ENVIRONMENT, Integer.class);
  }

  private Integer countInLegacy(String table) {
    return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table + IN_LEGACY, Integer.class);
  }

  private String readScript(String path) {
    try (InputStream in = new ClassPathResource(path).getInputStream()) {
      return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("시드 스크립트를 읽지 못했다", e);
    }
  }
}
