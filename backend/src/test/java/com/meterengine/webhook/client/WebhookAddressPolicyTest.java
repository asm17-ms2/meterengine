package com.meterengine.webhook.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import org.junit.jupiter.api.Test;

class WebhookAddressPolicyTest {
  @Test
  void 운영은_루프백과_사설과_메타데이터_주소와_리다이렉트용_자격정보를_차단한다() {
    WebhookAddressPolicy policy = new WebhookAddressPolicy(false);
    for (String url :
        new String[] {
          "http://example.com",
          "https://127.0.0.1",
          "https://[::1]",
          "https://10.0.0.1",
          "https://169.254.169.254",
          "https://[fd00::1]",
          "https://user:password@example.com",
          "https://example.com/#fragment"
        }) {
      assertThatThrownBy(() -> policy.resolve(url)).isInstanceOf(BlockedAddressException.class);
    }
  }

  @Test
  void 전역_공개_IP를_허용하고_로컬_설정만_HTTP와_사설망을_허용한다() throws Exception {
    assertThat(WebhookAddressPolicy.isBlocked(InetAddress.getByName("1.1.1.1"))).isFalse();
    assertThat(
            new WebhookAddressPolicy(true)
                .resolve("http://127.0.0.1:3000/webhook")
                .isLoopbackAddress())
        .isTrue();
  }

  @Test
  void 대역_192_0_0_0_16에서는_특수_용도_대역_두_개만_차단한다() throws Exception {
    assertThat(WebhookAddressPolicy.isBlocked(InetAddress.getByName("192.0.78.9"))).isFalse();
    assertThat(WebhookAddressPolicy.isBlocked(InetAddress.getByName("192.0.1.1"))).isFalse();
    assertThat(WebhookAddressPolicy.isBlocked(InetAddress.getByName("192.0.0.8"))).isTrue();
    assertThat(WebhookAddressPolicy.isBlocked(InetAddress.getByName("192.0.2.1"))).isTrue();
  }
}
