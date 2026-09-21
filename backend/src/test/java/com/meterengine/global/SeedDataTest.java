package com.meterengine.global;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.TestcontainersConfiguration;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class SeedDataTest {

  private static final String SEED_ORGANIZATION_ID = "d7cee55d-8c82-4afc-b996-6749d8b26a4e";

  private static final int CUSTOMERS = 2 + 3;
  private static final int METRICS = 1 + 3 + 2;
  private static final int PRICE_POLICIES = METRICS;
  private static final int PRICE_RATES = METRICS;

  @Autowired private JdbcTemplate jdbc;

  @Test
  void 시드는_도입사와_고객과_미터와_가격을_넣는다() {
    assertThat(rowCount("organization")).isEqualTo(1);
    assertThat(rowCount("customer")).isEqualTo(CUSTOMERS);
    assertThat(rowCount("billable_metric")).isEqualTo(METRICS);
    assertThat(rowCount("price_policy")).isEqualTo(PRICE_POLICIES);
    assertThat(rowCount("price_rate")).isEqualTo(PRICE_RATES);
  }

  @Test
  void 시드를_두_번_실행해도_행이_늘지_않는다() {
    jdbc.execute(readSeedScript());

    assertThat(rowCount("organization")).isEqualTo(1);
    assertThat(rowCount("customer")).isEqualTo(CUSTOMERS);
    assertThat(rowCount("billable_metric")).isEqualTo(METRICS);
    assertThat(rowCount("price_policy")).isEqualTo(PRICE_POLICIES);
    assertThat(rowCount("price_rate")).isEqualTo(PRICE_RATES);
  }

  @Test
  void 값이_바뀐_상태에서_시드를_다시_실행하면_파일_기준으로_되돌아온다() {
    jdbc.update("UPDATE billable_metric SET name = '손으로 바꾼 이름'");
    jdbc.update("UPDATE price_policy SET dimension_properties = '{model}'");
    jdbc.update("UPDATE price_rate SET unit_price = 999");

    jdbc.execute(readSeedScript());

    assertThat(
            jdbc.queryForObject(
                "SELECT name FROM billable_metric WHERE code = 'token-usage'", String.class))
        .isEqualTo("토큰 사용량");
    assertThat(
            jdbc.queryForObject(
                "SELECT dimension_properties::text FROM price_policy WHERE billable_metric_code ="
                    + " 'token-usage'",
                String.class))
        .isEqualTo("{}");
    assertThat(
            jdbc.queryForObject(
                "SELECT unit_price FROM price_rate WHERE billable_metric_code = 'token-usage'",
                BigDecimal.class))
        .isEqualByComparingTo("0.007");
    assertThat(rowCount("billable_metric")).isEqualTo(METRICS);
    assertThat(rowCount("price_rate")).isEqualTo(PRICE_RATES);
  }

  @Test
  void 시드_가격은_미터마다_무차원_단가_1행이다() {
    assertThat(
            jdbc.queryForList("SELECT dimension_properties::text FROM price_policy", String.class))
        .hasSize(PRICE_POLICIES)
        .containsOnly("{}");
    assertThat(jdbc.queryForList("SELECT dimension_values::text FROM price_rate", String.class))
        .hasSize(PRICE_RATES)
        .containsOnly("{}");

    var rate =
        jdbc.queryForMap(
            "SELECT billable_metric_code, dimension_values::text, unit_price FROM price_rate"
                + " WHERE billable_metric_code = 'token-usage'");

    assertThat(rate.get("billable_metric_code")).isEqualTo("token-usage");
    assertThat(rate.get("dimension_values")).isEqualTo("{}");
    assertThat((BigDecimal) rate.get("unit_price")).isEqualByComparingTo("0.007");
  }

  @Test
  void 시드_도입사_ID로_조회하면_고객이_모두_나온다() {
    Integer customers =
        jdbc.queryForObject(
            "SELECT count(*) FROM customer WHERE organization_id = ?::uuid",
            Integer.class,
            SEED_ORGANIZATION_ID);

    assertThat(customers).isEqualTo(CUSTOMERS);
  }

  @Test
  void 시드_미터는_token을_SUM으로_집계하도록_설정된다() {
    var metric =
        jdbc.queryForMap(
            "SELECT event_type, aggregation, target_property FROM billable_metric"
                + " WHERE code = 'token-usage'");

    assertThat(metric.get("event_type")).isEqualTo("chat_completion");
    assertThat(metric.get("aggregation")).isEqualTo("SUM");
    assertThat(metric.get("target_property")).isEqualTo("token");
  }

  @Test
  void 데모_확장_시드가_sample_events가_쓰는_고객과_미터를_넣는다() {
    assertThat(jdbc.queryForList("SELECT id::text FROM customer", String.class))
        .contains(
            "35bc8d12-9d38-57ab-bc9b-bbd35d779a26",
            "008cd6a7-6ff9-505d-9421-747e7d2d62aa",
            "8c525322-2712-5b5f-aa1a-435a7ff9fe97");

    assertThat(
            jdbc.queryForList(
                "SELECT code FROM billable_metric WHERE event_type = 'llm_request'", String.class))
        .containsExactlyInAnyOrder(
            "input-tokens", "output-tokens", "cache-read-tokens", "cache-creation-tokens");
  }

  @Test
  void 캐시_미터가_브리지가_보내는_키를_잰다() {
    var rows =
        jdbc.queryForList(
            "SELECT code, target_property, unit_price FROM billable_metric m"
                + " JOIN price_rate r ON r.organization_id = m.organization_id"
                + " AND r.billable_metric_code = m.code"
                + " WHERE m.code IN ('cache-read-tokens', 'cache-creation-tokens')"
                + " ORDER BY code");

    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).get("target_property")).isEqualTo("cache_creation_tokens");
    assertThat((BigDecimal) rows.get(0).get("unit_price")).isEqualByComparingTo("0.00875");
    assertThat(rows.get(1).get("target_property")).isEqualTo("cache_read_tokens");
    assertThat((BigDecimal) rows.get(1).get("unit_price")).isEqualByComparingTo("0.0007");
  }

  private Integer rowCount(String table) {
    return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
  }

  private String readSeedScript() {
    try (InputStream in = new ClassPathResource("db/migration/R__seed.sql").getInputStream()) {
      return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("시드 스크립트를 읽지 못했다", e);
    }
  }
}
