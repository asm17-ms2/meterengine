package com.meterengine.global.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EnvironmentRoutePolicyTest {
  @Test
  void 공통_경로는_정확한_segment와_쿼리_앞부분만_판단한다() {
    for (var path :
        new String[] {
          "/v1/sessions/current",
          "/v1/organization/members",
          "/v1/organization/members/abc/scopes",
          "/v1/toss-payments-webhooks/abc",
          "/v1/operations/audit-logs?q=anything",
          "/v1/organization-invitations/a%2Fb#fragment"
        }) {
      assertThat(EnvironmentRoutePolicy.classify(path).environmentRequired()).as(path).isFalse();
      assertThat(EnvironmentRoutePolicy.classify(path).entryScope()).isEqualTo("shared");
    }
  }

  @Test
  void 비슷한_경로와_정규화하지_않은_경로는_업무_기본값이다() {
    for (var path :
        new String[] {
          "/memberships",
          "/v1/organization/memberships",
          "/v1/organization/supplier",
          "/v1/organization/supplier/extra",
          "/v1/operations/extra",
          "/v1/organization/members/",
          "/v1//organization/members",
          "/v1/Organization/members",
          "/v1/organization/members/abc/role/",
          "/v1/audit-logs",
          "/v1/unknown"
        }) {
      assertThat(EnvironmentRoutePolicy.classify(path).environmentRequired()).as(path).isTrue();
      assertThat(EnvironmentRoutePolicy.classify(path).entryScope()).isEqualTo("business");
    }
  }

  @Test
  void 키_관리의_환경_요구와_shared_진입은_독립적이다() {
    for (var path : new String[] {"/v1/api-keys", "/v1/api-keys/abc", "/v1/api-keys/abc/scopes"}) {
      assertThat(EnvironmentRoutePolicy.classify(path).environmentRequired()).isTrue();
      assertThat(EnvironmentRoutePolicy.classify(path).entryScope()).isEqualTo("shared");
    }
  }

  @Test
  void 경로_정본은_contracts_resource에서_로드하고_이전_root_복사본은_없다() throws Exception {
    try (var resource =
        EnvironmentRoutePolicy.class.getResourceAsStream("/contracts/environment-routes.json")) {
      assertThat(resource).isNotNull();
      assertThat(
              tools.jackson.databind.json.JsonMapper.builder()
                  .build()
                  .readTree(resource)
                  .path("routes")
                  .size())
          .isEqualTo(30);
    }
    assertThat(EnvironmentRoutePolicy.class.getResource("/environment-routes.json")).isNull();
  }
}
