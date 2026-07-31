package com.meterengine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * springdoc이 런타임 인트로스펙션으로 만든 OpenAPI 문서를 파일로 떨어뜨린다.
 *
 * <p>검증이 아니라 생성이 목적인 테스트다. 생성물을 레포에 커밋해 두는 이유는 그 파일을 읽으려는 게 아니라, PR diff에 API 표면 변화가 드러나게 하기 위해서다.
 * 커밋하지 않으면 엔드포인트가 새로 열리거나 응답 스키마가 바뀌어도 컨트롤러 코드를 한 줄씩 읽어야만 알 수 있다.
 *
 * <p>최신성은 CI가 강제한다. 빌드 후 생성물이 커밋된 내용과 다르면(= 재생성 결과를 함께 커밋하지 않았으면) `.github/workflows/ci.yml`의
 * backend job이 실패한다. pnpm-lock.yaml과 같은 계약이다.
 *
 * <p>생성물은 구현 스냅샷이지 정본이 아니다. 정본 정책은 docs/api/README.md를 따른다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(
    properties = {
      // 매 빌드마다 키 순서가 흔들리면 의미 없는 diff가 생겨 CI가 무작위로 깨진다.
      "springdoc.writer-with-order-by-keys=true"
    })
@AutoConfigureMockMvc
class OpenApiSnapshotTest {

  /**
   * 기본값은 실행 위치(backend/)를 기준으로 한 상대 경로다. Gradle test 태스크는 작업 디렉터리에 의존하지 않도록 절대 경로를 시스템 속성으로 넘긴다
   * (build.gradle.kts 참조).
   */
  private static final Path DEFAULT_SNAPSHOT_PATH =
      Path.of("..", "docs", "api", "generated", "openapi.yaml");

  @Autowired private MockMvc mockMvc;

  @Test
  void springdoc_생성물을_스냅샷_파일로_갱신한다() throws Exception {
    // webEnvironment는 기본값 MOCK이어야 한다. RANDOM_PORT를 쓰면 springdoc이 servers에 매번 다른 포트를 박아
    // 생성물이 빌드마다 달라진다.
    byte[] generated =
        mockMvc
            .perform(get("/v3/api-docs.yaml"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    String document = new String(generated, StandardCharsets.UTF_8);
    assertThat(document).startsWith("openapi:");

    Path snapshot = snapshotPath();
    Files.createDirectories(snapshot.getParent());
    Files.writeString(snapshot, ensureTrailingNewline(document), StandardCharsets.UTF_8);
  }

  private static Path snapshotPath() {
    String configured = System.getProperty("meterengine.openapi.snapshot");
    return configured == null ? DEFAULT_SNAPSHOT_PATH : Path.of(configured);
  }

  /** 끝에 개행이 없으면 git이 "\ No newline at end of file"을 매번 붙여 diff가 지저분해진다. */
  private static String ensureTrailingNewline(String document) {
    return document.endsWith("\n") ? document : document + "\n";
  }
}
