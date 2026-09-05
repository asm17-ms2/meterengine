package com.meterengine.global;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.TestcontainersConfiguration;
import com.meterengine.global.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

@Import({
  TestcontainersConfiguration.class,
  GlobalExceptionHandlerIntegrationTest.ThrowingController.class
})
@SpringBootTest
class GlobalExceptionHandlerIntegrationTest {

  @Autowired private WebApplicationContext webApplicationContext;

  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc = MockMvcTester.from(webApplicationContext);
  }

  @Test
  void 모르는_예외는_500과_internal_server_error로_나간다() {
    MvcTestResult result = throwUnknown();

    assertThat(result).hasStatus(500).hasContentTypeCompatibleWith(MediaType.APPLICATION_JSON);
    assertThat(result)
        .bodyJson()
        .extractingPath("$.code")
        .asString()
        .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.getCode());
    assertThat(result)
        .bodyJson()
        .extractingPath("$.message")
        .asString()
        .isEqualTo(ErrorCode.INTERNAL_SERVER_ERROR.getMessage());
  }

  @Test
  void 오류_본문에는_code와_message만_실린다() {
    MvcTestResult result = throwUnknown();

    assertThat(result).bodyJson().extractingPath("$").asMap().containsOnlyKeys("code", "message");
  }

  @Test
  void 예외의_원인은_본문에_실리지_않는다() {
    MvcTestResult result = throwUnknown();

    assertThat(result).bodyText().doesNotContain(ThrowingController.CAUSE);
  }

  private MvcTestResult throwUnknown() {
    return mvc.get().uri(ThrowingController.PATH).exchange();
  }

  @RestController
  static class ThrowingController {

    static final String PATH = "/test/throw";
    static final String CAUSE = "thrown on purpose by the test";

    @GetMapping(PATH)
    void throwUnknown() {
      throw new IllegalStateException(CAUSE);
    }
  }
}
