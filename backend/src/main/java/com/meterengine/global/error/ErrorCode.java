package com.meterengine.global.error;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;
import org.springframework.http.HttpStatus;

public enum ErrorCode {
  // 400 Bad Request
  VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다"),
  MALFORMED_REQUEST_BODY(HttpStatus.BAD_REQUEST, "요청 본문을 JSON으로 읽을 수 없습니다"),
  UNKNOWN_ORGANIZATION(HttpStatus.BAD_REQUEST, "등록되지 않은 도입사입니다"),
  INVALID_EVENT(HttpStatus.BAD_REQUEST, "이벤트를 저장할 수 없습니다"),
  INVALID_BILLABLE_METRIC(HttpStatus.BAD_REQUEST, "집계 미터로 성립하지 않습니다"),
  INVALID_PRICE_POLICY(HttpStatus.BAD_REQUEST, "가격 정책으로 성립하지 않습니다"),

  // 404 Not Found
  CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "고객을 찾을 수 없습니다"),
  BILLABLE_METRIC_NOT_FOUND(HttpStatus.NOT_FOUND, "미터를 찾을 수 없습니다"),
  ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 경로가 없습니다"),

  // 405 Method Not Allowed
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "이 경로에서 허용하지 않는 메서드입니다"),

  // 406 Not Acceptable
  RESPONSE_TYPE_NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "Accept 헤더에 맞는 응답 형식이 없습니다"),

  // 409 Conflict
  CUSTOMER_HAS_EVENTS(HttpStatus.CONFLICT, "수집된 이벤트가 있어 고객을 삭제할 수 없습니다"),
  BILLABLE_METRIC_ALREADY_EXISTS(HttpStatus.CONFLICT, "같은 code의 미터가 이미 있습니다"),
  PRICE_POLICY_ALREADY_EXISTS(HttpStatus.CONFLICT, "이 미터에는 가격 정책이 이미 있습니다"),

  // 415 Unsupported Media Type
  REQUEST_TYPE_NOT_SUPPORTED(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 Content-Type입니다"),

  // 500 Internal Server Error
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
