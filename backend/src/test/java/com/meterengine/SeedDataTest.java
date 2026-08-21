package com.meterengine;

import static org.assertj.core.api.Assertions.assertThat;

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

/**
 * 시드 스크립트(R__seed.sql)가 MS2-125 인수 조건을 만족하는지 검증한다.
 *
 * <p>Flyway가 컨텍스트 기동 때 시드를 이미 적용했으므로, 멱등은 스크립트를 한 번 더 직접 실행해서 확인한다. Flyway가 두 번 돌리지 않는다는 사실에 기대면
 * 스크립트 자체가 안전한지는 증명되지 않는다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class SeedDataTest {

  /** 요청 헤더로 보낼 도입사 ID. 시드가 고정값으로 넣기 때문에 테스트가 값을 알 수 있다. */
  private static final String SEED_ORGANIZATION_ID = "d7cee55d-8c82-4afc-b996-6749d8b26a4e";

  // 시드가 넣는 행 수. 기본 시드와 데모 확장(MS2-166)을 나눠 적어 무엇이 늘었는지 보이게 한다.
  // 시드에 행을 더하면 여기도 함께 고친다.
  private static final int CUSTOMERS = 2 + 3; // 아크메, 베타 + 이슬비랩스, 도담헬스, 한들물류
  private static final int METRICS = 1 + 3; // token-usage + input/output-tokens, network-egress
  // 미터마다 정책 1개와 단가 1행이 붙는다. 전부 무차원이라 조합이 '{}' 하나뿐이다.
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

  /**
   * R__은 파일이 곧 DB 상태라는 뜻이므로, 재실행이 값을 파일 기준으로 되돌려야 한다. DO NOTHING이면 이 테스트가 깨진다. 파일을 고쳐도 기존 행이 남아 에러
   * 없이 무시되기 때문이다. 단가의 정본은 MS2-158부터 price_rate다.
   */
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
    // 시드의 INSERT는 dimension_properties를 나열하지 않아서, 되돌리기는 ON CONFLICT의
    // EXCLUDED가 생략된 컬럼에 DEFAULT('{}')를 싣는다는 사실에 기댄다. 그 미묘한 지점을 여기서 고정한다.
    assertThat(
            jdbc.queryForObject(
                "SELECT dimension_properties::text FROM price_policy WHERE metric_code ="
                    + " 'token-usage'",
                String.class))
        .isEqualTo("{}");
    assertThat(
            jdbc.queryForObject(
                "SELECT unit_price FROM price_rate WHERE metric_code = 'token-usage'",
                BigDecimal.class))
        .isEqualByComparingTo("0.5");
    assertThat(rowCount("billable_metric")).isEqualTo(METRICS);
    assertThat(rowCount("price_rate")).isEqualTo(PRICE_RATES);
  }

  /**
   * 무차원 시드의 규약이다. 정책은 빈 키 집합, 단가는 미터마다 '{}' 조합 1행 (2026-08-19 팀 합의). 미터가 늘어도 규약은 같으므로 특정 행이 아니라 전부를
   * 본다.
   */
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
            "SELECT metric_code, dimension_values::text, unit_price FROM price_rate"
                + " WHERE metric_code = 'token-usage'");

    assertThat(rate.get("metric_code")).isEqualTo("token-usage");
    assertThat(rate.get("dimension_values")).isEqualTo("{}");
    assertThat((BigDecimal) rate.get("unit_price")).isEqualByComparingTo("0.5");
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

  /**
   * demo/sample-events.csv가 쓰는 데이터다. CSV는 고객 id와 event_type을 파일에 박아 두고 보내므로, 시드에서 하나라도 어긋나면 100건이
   * 전부 거절된다. MS2-166에서 psql 주입(demo/seed-*.sql)을 걷어내고 부트 시드로 옮긴 값들이라 회귀를 여기서 잡는다.
   */
  @Test
  void 데모_확장_시드가_sample_events가_쓰는_고객과_미터를_넣는다() {
    assertThat(jdbc.queryForList("SELECT id::text FROM customer", String.class))
        .contains(
            "35bc8d12-9d38-57ab-bc9b-bbd35d779a26",
            "008cd6a7-6ff9-505d-9421-747e7d2d62aa",
            "8c525322-2712-5b5f-aa1a-435a7ff9fe97");

    // 이벤트 하나가 미터 두 개에 잡히는 구성이 이 데모의 핵심이라 짝으로 고정한다.
    assertThat(
            jdbc.queryForList(
                "SELECT code FROM billable_metric WHERE event_type = 'llm_request'", String.class))
        .containsExactlyInAnyOrder("input-tokens", "output-tokens");
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
