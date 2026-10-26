package com.meterengine.global.security;

import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ErrorResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

public class ErrorResponseWriter {

  private static final String BEARER_CHALLENGE = "Bearer";

  private final JsonMapper jsonMapper;

  public ErrorResponseWriter(JsonMapper jsonMapper) {
    this.jsonMapper = jsonMapper;
  }

  // 컨트롤러 밖에서 오류 본문을 GlobalExceptionHandler와 같은 꼴로 쓴다.
  public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {
    response.setStatus(errorCode.getStatus().value());
    if (errorCode.getStatus() == HttpStatus.UNAUTHORIZED) {
      response.setHeader(HttpHeaders.WWW_AUTHENTICATE, BEARER_CHALLENGE);
    }
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getOutputStream().write(jsonMapper.writeValueAsBytes(ErrorResponse.from(errorCode)));
  }
}
