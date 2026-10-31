package com.meterengine.payment.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.global.error.ErrorCode;
import org.junit.jupiter.api.Test;

class PaymentFailureReasonTest {

  @Test
  void 토스를_부르기_전에_키나_카드가_없어_실패하면_not_requested다() {
    assertThat(PaymentFailureReason.of(ErrorCode.TOSS_PAYMENTS_CREDENTIAL_MISSING.getCode()).tag())
        .isEqualTo("not_requested");
    assertThat(PaymentFailureReason.of(ErrorCode.PAYMENT_METHOD_MISSING.getCode()).tag())
        .isEqualTo("not_requested");
  }

  @Test
  void 카드사가_거절한_코드는_card_declined다() {
    assertThat(PaymentFailureReason.of("REJECT_CARD_PAYMENT").tag()).isEqualTo("card_declined");
    assertThat(PaymentFailureReason.of("INVALID_STOPPED_CARD").tag()).isEqualTo("card_declined");
    assertThat(PaymentFailureReason.of("EXCEED_MAX_DAILY_PAYMENT_COUNT").tag())
        .isEqualTo("card_declined");
  }

  @Test
  void PG_쪽_오류는_pg_error다() {
    assertThat(PaymentFailureReason.of("PROVIDER_ERROR").tag()).isEqualTo("pg_error");
    assertThat(PaymentFailureReason.of("CARD_PROCESSING_ERROR").tag()).isEqualTo("pg_error");
  }

  @Test
  void 승인_응답_없이_조회로_확정한_실패는_unconfirmed다() {
    assertThat(PaymentFailureReason.of("NOT_FOUND_PAYMENT").tag()).isEqualTo("unconfirmed");
    assertThat(PaymentFailureReason.of("ABORTED").tag()).isEqualTo("unconfirmed");
    assertThat(PaymentFailureReason.of("EXPIRED").tag()).isEqualTo("unconfirmed");
  }

  @Test
  void 묶음에_없는_토스_거절_코드와_코드_없는_4xx는_request_error다() {
    assertThat(PaymentFailureReason.of("INVALID_BILL_KEY_REQUEST").tag())
        .isEqualTo("request_error");
    assertThat(PaymentFailureReason.of("UNAUTHORIZED_KEY").tag()).isEqualTo("request_error");
    assertThat(PaymentFailureReason.of("HTTP_400").tag()).isEqualTo("request_error");
  }
}
