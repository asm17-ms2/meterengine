package com.meterengine.global.error;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;
import org.springframework.http.HttpStatus;

public enum ErrorCode {
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
