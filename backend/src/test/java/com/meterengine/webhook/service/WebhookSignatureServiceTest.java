package com.meterengine.webhook.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class WebhookSignatureServiceTest {
  @Test
  void ID와_시각과_원문을_마침표로_연결한_HMAC_SHA256을_base64로_낸다() {
    assertThat(
            new WebhookSignatureService()
                .sign("whsec_c2VjcmV0", "msg_123", "1614265330", "{\"hello\":\"world\"}"))
        .isEqualTo("v1,GAsCO9hdHVSIPjgsdgQ78GRBX6lnHgjMEBIb1nQOjnU=");
  }
}
