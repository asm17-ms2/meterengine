package com.meterengine.payment.dto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UpdateTossPaymentsCredentialRequestTest {

  private static final String SECRET_KEY = "test_sk_zXLkKEypNArWmo50nX3lmeaxYG5R";

  @Test
  void toString은_시크릿_키의_마지막_네_자만_보인다() {
    String text =
        new UpdateTossPaymentsCredentialRequest("test_ck_D5GePWvyJnrK0W0k6q8gLzN97Eoq", SECRET_KEY)
            .toString();

    assertThat(text).doesNotContain(SECRET_KEY).doesNotContain("test_sk_").contains("****YG5R");
  }
}
