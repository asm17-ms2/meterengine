package com.meterengine.payment.client;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TossPaymentsErrorTest {

  @Test
  void 한_줄_문구는_줄바꿈과_캐리지_리턴을_공백으로_바꾼다() {
    assertThat(
            new TossPaymentsError("INVALID_CARD_EXPIRATION", "카드 정보를 다시 확인해주세요.\n(유효기간)")
                .singleLineMessage())
        .isEqualTo("카드 정보를 다시 확인해주세요. (유효기간)");
    assertThat(new TossPaymentsError("CODE", "첫째 줄\r\n둘째 줄\r셋째 줄").singleLineMessage())
        .isEqualTo("첫째 줄  둘째 줄 셋째 줄");
  }

  @Test
  void 한_줄_문구는_줄바꿈이_없으면_그대로이고_null은_null이다() {
    assertThat(new TossPaymentsError("CODE", "거절되었습니다.").singleLineMessage()).isEqualTo("거절되었습니다.");
    assertThat(new TossPaymentsError("CODE", null).singleLineMessage()).isNull();
  }
}
