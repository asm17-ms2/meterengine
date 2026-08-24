package com.meterengine;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 문서의 메타 정보 (MS2-140).
 *
 * <p>springdoc은 경로와 스키마를 컨트롤러에서 자동으로 만들지만 {@code info}는 만들지 못한다. 채우지 않으면 title이 {@code "OpenAPI
 * definition"}, version이 {@code "v0"}인 채로 프론트엔드에 나간다. 설정 프로퍼티로는 채울 수 없어서(springdoc 3.0.3이 제공하는
 * 프로퍼티에 {@code info.*}가 없다) 빈으로 준다.
 *
 * <p><b>패키지를 새로 파지 않았다.</b> MS2-149 재구조화에서 도메인 패키지는 customer, event, invoice, metric 넷으로 정했는데, 이
 * 클래스는 어느 도메인 소속도 아니라 루트에 남긴다 (MS2-141은 취소됨). 설정 클래스가 더 생기면 그때 모을 자리를 정한다. 어디에 두든
 * {@code @SpringBootApplication}의 스캔 범위 안이라 동작은 같다.
 */
@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

  /**
   * {@code version}은 프로젝트 버전이 아니라 API 버전이다.
   *
   * <p>경로가 {@code /v1/**}이라 {@code v1}로 고정한다. 프로젝트 버전({@code 0.0.1-SNAPSHOT})을 넣으면 API가 그대로인데도 버전을
   * 올릴 때마다 생성물이 바뀌어, 계약 변경만 보이게 하려는 diff에 잡음이 섞인다.
   */
  private static final String API_VERSION = "v1";

  @Bean
  OpenAPI meterEngineOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("MeterEngine API")
                .version(API_VERSION)
                .description(
                    """
                    사용량 기반 과금 플랫폼의 API. 사용량 이벤트를 수집하고, 고객별 월 사용량과 청구 예정액을 조회한다.

                    도입사는 X-Organization-Id 헤더로 받는다. 이 헤더는 임시물이며 MS2-126이 Bearer API 키 인증을
                    붙이면서 인증 주체에서 꺼내는 형태로 바뀐다.

                    오류 응답은 RFC 9457 problem+json이고 4xx에는 code 확장 멤버가 붙는다. 화면 문구는 이 값으로
                    고른다. title과 detail은 영어이고 로그와 개발자용이라 그대로 띄우지 않는다. 5xx는 본문 형식을
                    약속하지 않으므로 클라이언트는 상태 코드만 본다.
                    """))
        // 서버 URL을 고정한다. 비워 두면 springdoc이 문서를 요청한 URL에서 만들어 내는데, 그러면 생성물이 문서를 뽑은
        // 환경(호스트와 포트)에 따라 달라진다. 상대 경로는 문서를 서빙한 호스트를 그대로 가리키므로 로컬과 배포 어느
        // 쪽에서도 맞는다. Scalar도 문서 URL의 origin을 앞에 붙여 정규화한다.
        //
        // [주의] server.servlet.context-path를 주거나 리버스 프록시 뒤 /api 같은 접두사에 붙이면 이 고정값이 그
        // 접두사를 날려 Scalar의 요청이 빗나간다. springdoc 기본 동작은 forward 헤더를 반영해 맞는 값을 낸다.
        // 접두사를 도입할 때 이 줄을 같이 본다.
        .servers(List.of(new Server().url("/").description("문서를 서빙한 호스트")));
  }
}
