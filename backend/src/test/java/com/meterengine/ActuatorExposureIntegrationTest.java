package com.meterengine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.micrometer.metrics.test.autoconfigure.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * actuator 노출 범위를 실제 응답으로 고정한다 (MS2-168).
 *
 * <p>운영 Prometheus가 {@code /actuator/prometheus}를 scrape한다. 이 엔드포인트가 사라져도 Prometheus는 오류를 내지 않고 지표만
 * 조용히 끊기므로, 노출 설정이 깨지는 순간을 배포 뒤가 아니라 빌드에서 잡는다.
 *
 * <p>반대 방향도 본다. 노출 범위는 health와 prometheus 둘뿐이라는 것이 경계 설계다. env, beans 같은 엔드포인트는 설정값과 내부 구조를 드러내므로,
 * 넓히려면 이 테스트를 고치면서 의도를 남겨야 한다.
 *
 * <p><b>MOCK 환경이 아니라 RANDOM_PORT인 이유.</b> Boot 4에서 actuator 웹 엔드포인트는 MockMvc가 보는 컨텍스트에 매핑되지 않아 설정과
 * 무관하게 404가 난다(실측). scrape는 어차피 실제 HTTP로 오므로, 실제 서버를 띄워 같은 경로를 검증한다.
 *
 * <p><b>{@code @AutoConfigureMetrics}가 필요한 이유.</b> Boot는 테스트 컨텍스트에서 지표 export를 기본으로 끈다
 * (spring-boot-micrometer-metrics-test의 MetricsContextCustomizerFactory). 그러면
 * PrometheusMeterRegistry가 안 만들어져 scrape 엔드포인트도 빠지고, 이 테스트가 검증하려는 대상 자체가 사라진다(실측: RANDOM_PORT로도
 * 404).
 */
@Import(TestcontainersConfiguration.class)
@AutoConfigureMetrics
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
class ActuatorExposureIntegrationTest {

  @LocalServerPort private int port;

  private RestTestClient client;

  @BeforeEach
  void setUp() {
    client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
  }

  @Test
  void prometheus_엔드포인트가_지표를_낸다() {
    // jvm_memory_used_bytes로 단언하는 이유: 기동 시점부터 항상 있다. http_server_requests는
    // 첫 HTTP 요청이 기록된 뒤에야 생겨서 실행 순서에 따라 흔들린다.
    client
        .get()
        .uri("/actuator/prometheus")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .value(body -> assertThat(body).contains("jvm_memory_used_bytes"));
  }

  @Test
  void 노출하지_않기로_한_엔드포인트는_닿지_않는다() {
    client.get().uri("/actuator/env").exchange().expectStatus().isNotFound();
  }
}
