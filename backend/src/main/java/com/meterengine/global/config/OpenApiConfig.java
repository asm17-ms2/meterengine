package com.meterengine.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class OpenApiConfig {

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

                    오류 응답은 code, message, errors[] 형식이다. 화면 문구는 code로 고른다. message는
                    code마다 하나인 한국어 문구이고, errors는 틀린 필드를 짚을 수 있는 400에만 실린다.
                    """))
        .servers(List.of(new Server().url("/").description("문서를 서빙한 호스트")));
  }
}
