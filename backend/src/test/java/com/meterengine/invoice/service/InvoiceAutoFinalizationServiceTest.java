package com.meterengine.invoice.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class InvoiceAutoFinalizationServiceTest {

  @Test
  void 예약_실행은_매일_4시_KST에_한_번_돈다() throws NoSuchMethodException {
    Scheduled scheduled =
        InvoiceAutoFinalizationService.class.getMethod("run").getAnnotation(Scheduled.class);

    assertThat(scheduled.cron()).isEqualTo("0 0 4 * * *");
    assertThat(scheduled.zone()).isEqualTo("Asia/Seoul");
  }

  @Test
  void 자동_확정_시각은_다음_달_2일_4시_KST다() {
    assertThat(InvoiceAutoFinalizationService.autoFinalizeAt(YearMonth.of(2026, 8)))
        .isEqualTo(OffsetDateTime.parse("2026-09-02T04:00:00+09:00"));
    assertThat(InvoiceAutoFinalizationService.autoFinalizeAt(YearMonth.of(2026, 12)))
        .isEqualTo(OffsetDateTime.parse("2027-01-02T04:00:00+09:00"));
  }

  @Test
  void 다음_실행_시각은_지금보다_뒤인_가장_가까운_4시_KST다() {
    assertThat(nextRunAt("2026-09-01T10:00:00+09:00"))
        .isEqualTo(OffsetDateTime.parse("2026-09-02T04:00:00+09:00"));
    assertThat(nextRunAt("2026-09-02T03:59:59+09:00"))
        .isEqualTo(OffsetDateTime.parse("2026-09-02T04:00:00+09:00"));
    assertThat(nextRunAt("2026-09-02T04:00:00+09:00"))
        .isEqualTo(OffsetDateTime.parse("2026-09-03T04:00:00+09:00"));
    assertThat(nextRunAt("2026-09-02T04:05:00+09:00"))
        .isEqualTo(OffsetDateTime.parse("2026-09-03T04:00:00+09:00"));
  }

  @Test
  void 다음_실행_시각은_달과_해가_바뀌어도_다음_날_4시_KST다() {
    assertThat(nextRunAt("2026-09-30T23:00:00+09:00"))
        .isEqualTo(OffsetDateTime.parse("2026-10-01T04:00:00+09:00"));
    assertThat(nextRunAt("2026-12-31T05:00:00+09:00"))
        .isEqualTo(OffsetDateTime.parse("2027-01-01T04:00:00+09:00"));
  }

  @Test
  void 다음_실행_시각은_UTC로_받아도_KST_날짜로_정한다() {
    assertThat(nextRunAt("2026-09-01T18:30:00Z"))
        .isEqualTo(OffsetDateTime.parse("2026-09-02T04:00:00+09:00"));
    assertThat(nextRunAt("2026-09-01T19:30:00Z"))
        .isEqualTo(OffsetDateTime.parse("2026-09-03T04:00:00+09:00"));
  }

  private static OffsetDateTime nextRunAt(String now) {
    return InvoiceAutoFinalizationService.nextRunAt(OffsetDateTime.parse(now));
  }
}
