package com.meterengine.global.database;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class EnvironmentSideEffectPolicyTest {
  @Test
  void 배포와_전환_값이_없거나_지원하지_않으면_거절한다() {
    assertThatThrownBy(() -> new CutoverPolicy(new MockEnvironment()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> policy("staging", "unknown", ""))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> policy("unknown", "active", ""))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void 읽기전용은_모든_PG와_웹훅을_거절한다() {
    var policy = policy("local", "read_only", "");
    assertThatThrownBy(() -> policy.requirePayment("test_sk_fixture"))
        .isInstanceOf(IllegalStateException.class);
    assertThat(policy.allowsWebhook("https://example.com/")).isFalse();
  }

  @Test
  void 스테이징은_LIVE_PG와_기본_빈_수신목록을_차단한다() {
    var policy = policy("staging", "active", "");
    assertThatThrownBy(() -> policy.requirePayment("live_sk_fixture"))
        .isInstanceOf(IllegalStateException.class);
    policy.requirePayment("test_sk_fixture");
    assertThat(policy.allowsWebhook("https://example.com/")).isFalse();
  }

  @Test
  void URI의_정확한_원시경로와_쿼리만_허용한다() {
    var policy =
        policy("staging", "active", " https://EXAMPLE.com/A%2fb?q=X ,https://example.com/empty? ");
    assertThat(policy.allowsWebhook("HTTPS://example.com:443/A%2fb?q=X")).isTrue();
    for (String url :
        new String[] {
          "https://example.com/A%2Fb?q=X",
          "https://example.com/a%2fb?q=X",
          "https://example.com/A%2fb?q=x",
          "https://example.com:444/A%2fb?q=X",
          "https://example.com/empty",
          "https://example.com/empty?#x",
          "https://user@example.com/A%2fb?q=X"
        }) {
      assertThat(policy.allowsWebhook(url)).as(url).isFalse();
    }
    assertThat(policy.allowsWebhook("https://example.com/empty?")).isTrue();
  }

  @Test
  void 잘못된_허용목록과_중복과_빈항목은_기동을_거절한다() {
    for (String urls :
        new String[] {
          "http://example.com",
          "https://example.com,",
          ",https://example.com",
          "https://example.com,https://EXAMPLE.com:443/",
          "https://user@example.com/",
          "https://example.com/#x",
          "https://example.com:0/"
        }) {
      assertThatThrownBy(() -> policy("staging", "active", urls))
          .as(urls)
          .isInstanceOf(IllegalArgumentException.class);
    }
  }

  private EnvironmentSideEffectPolicy policy(String deployment, String mode, String urls) {
    var environment =
        new MockEnvironment()
            .withProperty("METERENGINE_DEPLOYMENT_ENVIRONMENT", deployment)
            .withProperty("METERENGINE_CUTOVER_MODE", mode)
            .withProperty("METERENGINE_STAGING_WEBHOOK_ALLOWED_URLS", urls);
    return new EnvironmentSideEffectPolicy(new CutoverPolicy(environment), environment);
  }
}
