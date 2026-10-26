package com.meterengine.global.error;

import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;
import org.springframework.http.HttpStatus;

public enum ErrorCode {
  // 400 Bad Request
  VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다"),
  MALFORMED_REQUEST_BODY(HttpStatus.BAD_REQUEST, "요청 본문을 JSON으로 읽을 수 없습니다"),
  INVALID_BILLABLE_METRIC(HttpStatus.BAD_REQUEST, "집계 미터로 성립하지 않습니다"),
  INVALID_PRICE_RATE(HttpStatus.BAD_REQUEST, "단가로 성립하지 않습니다"),
  EVENT_TOO_OLD(HttpStatus.BAD_REQUEST, "받는 기간이 지난 이벤트입니다"),

  // 401 Unauthorized
  AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "인증 정보가 없습니다"),
  INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "인증 정보가 올바르지 않습니다"),

  // 403 Forbidden
  PERMISSION_DENIED(HttpStatus.FORBIDDEN, "이 요청을 할 권한이 없습니다"),
  ORGANIZATION_NOT_ACTIVE(HttpStatus.FORBIDDEN, "활성 상태가 아닌 도입사입니다"),

  // 404 Not Found
  CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "고객을 찾을 수 없습니다"),
  BILLABLE_METRIC_NOT_FOUND(HttpStatus.NOT_FOUND, "미터를 찾을 수 없습니다"),
  PRICE_RATE_NOT_FOUND(HttpStatus.NOT_FOUND, "단가를 찾을 수 없습니다"),
  INVOICE_NOT_FOUND(HttpStatus.NOT_FOUND, "인보이스를 찾을 수 없습니다"),
  ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 경로가 없습니다"),
  API_KEY_NOT_FOUND(HttpStatus.NOT_FOUND, "API 키를 찾을 수 없습니다"),
  ORGANIZATION_NOT_FOUND(HttpStatus.NOT_FOUND, "도입사를 찾을 수 없습니다"),
  USER_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자 계정을 찾을 수 없습니다"),

  // 405 Method Not Allowed
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "이 경로에서 허용하지 않는 메서드입니다"),

  // 406 Not Acceptable
  RESPONSE_TYPE_NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "Accept 헤더에 맞는 응답 형식이 없습니다"),

  // 409 Conflict
  CUSTOMER_HAS_EVENTS(HttpStatus.CONFLICT, "수집된 이벤트가 있어 고객을 삭제할 수 없습니다"),
  CUSTOMER_HAS_PAYMENT_ATTEMPTS(HttpStatus.CONFLICT, "결제 기록이 있어 고객을 삭제할 수 없습니다"),
  BILLABLE_METRIC_HAS_EVENTS(
      HttpStatus.CONFLICT, "집계된 이벤트가 있어 event_type과 target_property를 바꾸거나 미터를 삭제할 수 없습니다"),
  BILLABLE_METRIC_HAS_PRICE_RATE(HttpStatus.CONFLICT, "단가가 붙어 있어 미터를 삭제할 수 없습니다"),
  BILLABLE_METRIC_ALREADY_EXISTS(HttpStatus.CONFLICT, "같은 code의 미터가 이미 있습니다"),
  BILLABLE_METRIC_EVENT_TYPE_TARGET_PROPERTY_ALREADY_EXISTS(
      HttpStatus.CONFLICT, "event_type과 target_property가 같은 미터가 이미 있습니다"),
  INVOICE_ALREADY_PAID(HttpStatus.CONFLICT, "이미 결제했거나 결제 중인 인보이스입니다"),
  INVOICE_AMOUNT_ZERO(HttpStatus.CONFLICT, "청구 금액이 0원인 인보이스는 결제할 수 없습니다"),
  EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입한 이메일입니다"),
  ORGANIZATION_STATUS_TRANSITION_INVALID(HttpStatus.CONFLICT, "도입사를 이 상태로 바꿀 수 없습니다"),
  ORGANIZATION_MEMBER_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 도입사에 소속된 사용자입니다"),

  // 413 Content Too Large
  UPLOAD_SIZE_EXCEEDED(HttpStatus.CONTENT_TOO_LARGE, "업로드 크기가 상한을 넘었습니다"),

  // 415 Unsupported Media Type
  REQUEST_TYPE_NOT_SUPPORTED(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 Content-Type입니다"),

  // 429 Too Many Requests
  RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "요청 한도를 넘었습니다"),

  // 500 Internal Server Error
  INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류입니다"),

  // 503 Service Unavailable
  REQUEST_TIMED_OUT(HttpStatus.SERVICE_UNAVAILABLE, "요청 처리 시간이 초과되었습니다");

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
