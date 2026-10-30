package com.meterengine.payment.client;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class TossPaymentsBillingTest {

  private static final String BILLING_KEY = "Z_t5vOvQxrj4499PeiJcjen28-V2RyqgYTwN44Rdzk0=";

  @Test
  void toString은_빌링키의_마지막_네_자만_보인다() {
    String text =
        new TossPaymentsBilling(
                BILLING_KEY,
                OffsetDateTime.parse("2026-10-26T10:00:00+09:00"),
                new TossPaymentsBilling.Card("61", "43301234****123*"))
            .toString();

    assertThat(text).doesNotContain(BILLING_KEY).contains("****zk0=").contains("43301234****123*");
  }
}
