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
  private static final String SEED_ORGANIZATION_ID = "00000000-0000-0000-0000-000000000001";

  @Autowired private JdbcTemplate jdbc;

  @Test
  void 시드는_도입사_1개와_고객_2명과_미터_1개를_넣는다() {
    assertThat(rowCount("organization")).isEqualTo(1);
    assertThat(rowCount("customer")).isEqualTo(2);
    assertThat(rowCount("billable_metric")).isEqualTo(1);
  }

  @Test
  void 시드를_두_번_실행해도_행이_늘지_않는다() {
    jdbc.execute(readSeedScript());

    assertThat(rowCount("organization")).isEqualTo(1);
    assertThat(rowCount("customer")).isEqualTo(2);
    assertThat(rowCount("billable_metric")).isEqualTo(1);
  }

  /**
   * R__은 파일이 곧 DB 상태라는 뜻이므로, 재실행이 값을 파일 기준으로 되돌려야 한다. DO NOTHING이면 이 테스트가 깨진다. 파일을 고쳐도 기존 행이 남아 에러
   * 없이 무시되기 때문이다.
   */
  @Test
  void 값이_바뀐_상태에서_시드를_다시_실행하면_파일_기준으로_되돌아온다() {
    jdbc.update("UPDATE billable_metric SET unit_price = 999, name = '손으로 바꾼 이름'");

    jdbc.execute(readSeedScript());

    var metric = jdbc.queryForMap("SELECT name, unit_price FROM billable_metric");
    assertThat(metric.get("name")).isEqualTo("토큰 사용량");
    assertThat((BigDecimal) metric.get("unit_price")).isEqualByComparingTo("0.5");
    assertThat(rowCount("billable_metric")).isEqualTo(1);
  }

  @Test
  void 시드_도입사_ID로_조회하면_고객_2명이_모두_나온다() {
    Integer customers =
        jdbc.queryForObject(
            "SELECT count(*) FROM customer WHERE organization_id = ?::uuid",
            Integer.class,
            SEED_ORGANIZATION_ID);

    assertThat(customers).isEqualTo(2);
  }

  @Test
  void 시드_미터는_token을_SUM으로_집계하도록_설정된다() {
    var metric =
        jdbc.queryForMap(
            "SELECT event_type, aggregation, target_property, unit_price FROM billable_metric");

    assertThat(metric.get("event_type")).isEqualTo("chat_completion");
    assertThat(metric.get("aggregation")).isEqualTo("SUM");
    assertThat(metric.get("target_property")).isEqualTo("token");
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
