package com.meterengine.global.error;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;
import org.springframework.http.HttpStatus;

public enum ErrorCode {
  VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다"),
  MALFORMED_REQUEST_BODY(HttpStatus.BAD_REQUEST, "요청 본문을 JSON으로 읽을 수 없습니다"),
  ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 경로가 없습니다"),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "이 경로에서 허용하지 않는 메서드입니다"),
  RESPONSE_TYPE_NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "Accept 헤더에 맞는 응답 형식이 없습니다"),
  REQUEST_TYPE_NOT_SUPPORTED(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 Content-Type입니다"),
  INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류입니다");

  private final HttpStatus status;
  private final String code;
  private final String message;

  ErrorCode(HttpStatus status, String message) {
    this.status = status;
    this.code = name().toLowerCase(Locale.ROOT);
    this.message = message;
  }

  public HttpStatus getStatus() {
    return status;
  }

  @JsonValue
  public String getCode() {
    return code;
  }

  public String getMessage() {
    return message;
  }
}
