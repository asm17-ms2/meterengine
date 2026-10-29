package com.meterengine.payment.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BillingKeyServiceTest {

  private static final String SECRET_KEY = "test_sk_zXLkKEypNArWmo50nX3lmeaxYG5R";

  @Test
  void 발급_준비의_toString은_시크릿_키의_마지막_네_자만_보인다() {
    String text = new BillingKeyService.Issuance(null, SECRET_KEY).toString();

    assertThat(text).doesNotContain(SECRET_KEY).contains("****YG5R");
  }

  @Test
  void 교체_결과의_toString은_교체된_빌링키의_마지막_네_자만_보인다() {
    String replacedBillingKey = "Z_t5vOvQxrj4499PeiJcjen28-V2RyqgYTwN44Rdzk0=";

    String text = new BillingKeyService.Replacement(null, replacedBillingKey).toString();

    assertThat(text).doesNotContain(replacedBillingKey).contains("****zk0=");
  }

  @Test
  void 아홉_자보다_짧은_비밀값은_마지막_네_자도_보이지_않는다() {
    String text = new BillingKeyService.Issuance(null, "short123").toString();

    assertThat(text).doesNotContain("123").contains("secretKey=****]");
  }
}
