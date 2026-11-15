package com.meterengine.global.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meterengine.global.error.ContentTooLargeException;
import jakarta.servlet.FilterChain;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

class RequestBodyLimitFilterTest {

  private static final String LIMITED_PATH = "/v1/events";
  private static final int MAX_BYTES = 8;

  private final RequestBodyLimitFilter filter =
      new RequestBodyLimitFilter(
          List.of(new RequestBodyLimit(LIMITED_PATH, MAX_BYTES)),
          new ErrorResponseWriter(JsonMapper.builder().build()));

  // 본문을 끝까지 읽는 컨트롤러 자리다.
  private final FilterChain readingChain =
      (request, response) -> request.getInputStream().readAllBytes();

  @Test
  void 상한을_넘는_Content_Length는_본문을_읽지_않고_413이다() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(post(LIMITED_PATH, MAX_BYTES + 1), response, chain);

    assertThat(response.getStatus()).isEqualTo(413);
    assertThat(response.getContentAsString()).contains("request_body_too_large");
    assertThat(chain.getRequest()).isNull();
  }

  @Test
  void 상한과_같은_크기는_통과한다() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(post(LIMITED_PATH, MAX_BYTES), response, readingChain);

    assertThat(response.getStatus()).isEqualTo(200);
  }

  @Test
  void Content_Length_없이_온_본문은_읽다가_상한을_넘으면_끊는다() {
    MockHttpServletRequest request =
        new MockHttpServletRequest("POST", LIMITED_PATH) {
          @Override
          public long getContentLengthLong() {
            return -1;
          }
        };
    request.setContent(new byte[MAX_BYTES + 1]);

    assertThatThrownBy(() -> filter.doFilter(request, new MockHttpServletResponse(), readingChain))
        .isInstanceOf(ContentTooLargeException.class);
  }

  @Test
  void 상한을_두지_않은_경로는_크기를_보지_않는다() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(post("/v1/customers", MAX_BYTES + 1), response, readingChain);

    assertThat(response.getStatus()).isEqualTo(200);
  }

  private static MockHttpServletRequest post(String path, int bodyBytes) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
    request.setContent(new byte[bodyBytes]);
    return request;
  }
}
