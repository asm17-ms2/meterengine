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
  INVALID_TOSS_PAYMENTS_CREDENTIAL(HttpStatus.BAD_REQUEST, "토스페이먼츠 키로 성립하지 않습니다"),
  PAYMENT_METHOD_REJECTED(HttpStatus.BAD_REQUEST, "카드 인증이 거절되었습니다"),
  TOSS_PAYMENTS_TEST_CODE_IN_LIVE_MODE(HttpStatus.BAD_REQUEST, "실제 모드에서는 토스페이먼츠 테스트 코드를 쓸 수 없습니다"),

  // 401 Unauthorized
  AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "인증 정보가 없습니다"),
  INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "인증 정보가 올바르지 않습니다"),

  // 403 Forbidden
  PERMISSION_DENIED(HttpStatus.FORBIDDEN, "이 요청을 할 권한이 없습니다"),
  ORGANIZATION_NOT_ACTIVE(HttpStatus.FORBIDDEN, "활성 상태가 아닌 도입사입니다"),
  ENVIRONMENT_MISMATCH(HttpStatus.FORBIDDEN, "API 키의 환경과 요청 환경이 다릅니다"),

  // 404 Not Found
  CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "고객을 찾을 수 없습니다"),
  EVENT_NOT_FOUND(HttpStatus.NOT_FOUND, "이벤트를 찾을 수 없습니다"),
  BILLABLE_METRIC_NOT_FOUND(HttpStatus.NOT_FOUND, "미터를 찾을 수 없습니다"),
  PRICE_RATE_NOT_FOUND(HttpStatus.NOT_FOUND, "단가를 찾을 수 없습니다"),
  INVOICE_NOT_FOUND(HttpStatus.NOT_FOUND, "인보이스를 찾을 수 없습니다"),
  INVOICE_VIEW_LINK_NOT_FOUND(HttpStatus.NOT_FOUND, "인보이스 조회 링크를 찾을 수 없습니다"),
  ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND, "요청한 경로가 없습니다"),
  API_KEY_NOT_FOUND(HttpStatus.NOT_FOUND, "API 키를 찾을 수 없습니다"),
  API_KEY_UNAVAILABLE(HttpStatus.CONFLICT, "폐기되거나 만료된 API 키의 권한은 변경할 수 없습니다"),
  ORGANIZATION_NOT_FOUND(HttpStatus.NOT_FOUND, "도입사를 찾을 수 없습니다"),
  USER_ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "사용자 계정을 찾을 수 없습니다"),
  PAYMENT_METHOD_NOT_FOUND(HttpStatus.NOT_FOUND, "등록된 카드가 없습니다"),
  PAYMENT_METHOD_REGISTRATION_NOT_FOUND(HttpStatus.NOT_FOUND, "카드 등록 링크를 찾을 수 없습니다"),
  PAYMENT_ATTEMPT_NOT_FOUND(HttpStatus.NOT_FOUND, "결제 시도를 찾을 수 없습니다"),

  // 405 Method Not Allowed
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "이 경로에서 허용하지 않는 메서드입니다"),

  // 406 Not Acceptable
  RESPONSE_TYPE_NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "Accept 헤더에 맞는 응답 형식이 없습니다"),

  // 409 Conflict
  RESOURCE_VERSION_CONFLICT(HttpStatus.CONFLICT, "다른 요청이 변경한 항목입니다. 다시 조회한 뒤 수정하세요"),
  IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "같은 재시도 키로 다른 요청을 보낼 수 없습니다"),
  CUSTOMER_ARCHIVED(HttpStatus.CONFLICT, "보관한 고객입니다. 복원한 뒤 사용하세요"),
  RESOURCE_ARCHIVED(HttpStatus.CONFLICT, "보관한 항목입니다"),
  ENVIRONMENT_NOT_READY(HttpStatus.CONFLICT, "선택한 환경에서 아직 지원하지 않는 기능입니다. 기존 데이터는 기존 범위에서 확인하세요"),
  ARCHIVE_RESTORATION_CONFLICT(
      HttpStatus.CONFLICT, "현재 버전과 충돌하거나 보관 기간이 시작되어 복원할 수 없습니다. 새 버전을 등록하세요"),
  CUSTOMER_HAS_EVENTS(HttpStatus.CONFLICT, "수집된 이벤트가 있어 고객을 삭제할 수 없습니다"),
  CUSTOMER_HAS_PAYMENT_ATTEMPTS(HttpStatus.CONFLICT, "결제 기록이 있어 고객을 삭제할 수 없습니다"),
  BILLABLE_METRIC_HAS_EVENTS(
      HttpStatus.CONFLICT, "집계된 이벤트가 있어 event_type과 target_property를 바꾸거나 미터를 삭제할 수 없습니다"),
  BILLABLE_METRIC_HAS_PRICE_RATE(HttpStatus.CONFLICT, "단가가 붙어 있어 미터를 삭제할 수 없습니다"),
  BILLABLE_METRIC_ALREADY_EXISTS(HttpStatus.CONFLICT, "같은 code의 미터가 이미 있습니다"),
  BILLABLE_METRIC_EVENT_TYPE_TARGET_PROPERTY_ALREADY_EXISTS(
      HttpStatus.CONFLICT, "event_type과 target_property가 같은 미터가 이미 있습니다"),
  INVOICE_ALREADY_PAID(HttpStatus.CONFLICT, "이미 결제했거나 결제 중인 인보이스입니다"),
  PAYMENT_RESULT_UNCONFIRMED(HttpStatus.CONFLICT, "이전 결제 결과를 확인한 뒤 다시 요청해 주세요"),
  PAYMENT_RETRY_UNAVAILABLE(HttpStatus.CONFLICT, "현재 상태에서는 결제를 다시 시도할 수 없습니다"),
  PAYMENT_RETRY_STALE(HttpStatus.CONFLICT, "결제 상태가 바뀌었습니다. 최신 상태를 조회한 뒤 다시 확인해 주세요"),
  INVOICE_AMOUNT_ZERO(HttpStatus.CONFLICT, "청구 금액이 0원인 인보이스는 결제할 수 없습니다"),
  EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 가입한 이메일입니다"),
  ORGANIZATION_STATUS_TRANSITION_INVALID(HttpStatus.CONFLICT, "도입사를 이 상태로 바꿀 수 없습니다"),
  ORGANIZATION_MEMBER_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 도입사에 소속된 사용자입니다"),
  TOSS_PAYMENTS_CREDENTIAL_MISSING(HttpStatus.CONFLICT, "결제 모드에 맞는 토스페이먼츠 키가 없습니다"),
  PAYMENT_METHOD_MISSING(HttpStatus.CONFLICT, "결제 모드에 맞는 등록 카드가 없습니다"),
  PAYMENT_MODE_MISMATCH(HttpStatus.CONFLICT, "API 키의 모드와 결제 모드가 다릅니다"),
  PAYMENT_ATTEMPT_NOT_CANCELABLE(HttpStatus.CONFLICT, "완료된 결제만 취소할 수 있습니다"),
  PAYMENT_ATTEMPT_FULLY_CANCELED(HttpStatus.CONFLICT, "이미 전액 취소한 결제입니다"),
  PAYMENT_CANCELLATION_AMOUNT_EXCEEDED(HttpStatus.CONFLICT, "취소 금액이 남은 결제 금액보다 큽니다"),
  PAYMENT_CANCELLATION_IN_PROGRESS(HttpStatus.CONFLICT, "진행 중인 취소가 있습니다"),
  PAYMENT_CANCELLATION_IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "같은 멱등 키로 다른 취소 요청을 보낼 수 없습니다"),
  WEBHOOK_DELIVERY_NOT_RETRYABLE(HttpStatus.CONFLICT, "다시 전송할 수 없는 웹훅입니다"),

  PRICE_RATE_HISTORY_IMMUTABLE(HttpStatus.CONFLICT, "지난달 이전 단가 버전은 바꿀 수 없습니다"),
  PRICE_PLAN_HISTORY_IMMUTABLE(HttpStatus.CONFLICT, "지난달 이전 요금제 버전은 바꿀 수 없습니다"),
  PRICE_PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "요금제를 찾을 수 없습니다"),
  CUSTOMER_HAS_PRICING(HttpStatus.CONFLICT, "고객별 단가나 요금제가 있어 고객을 삭제할 수 없습니다"),
  INVALID_WEBHOOK_URL(HttpStatus.BAD_REQUEST, "올바른 웹훅 URL을 입력하세요"),
  INVALID_WEBHOOK_EVENT_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 웹훅 이벤트 종류입니다"),
  WEBHOOK_ENDPOINT_NOT_FOUND(HttpStatus.NOT_FOUND, "웹훅 엔드포인트를 찾을 수 없습니다"),
  WEBHOOK_ENDPOINT_DISABLED(HttpStatus.CONFLICT, "비활성 상태인 웹훅 엔드포인트입니다"),
  WEBHOOK_ENDPOINT_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "웹훅 엔드포인트 등록 한도를 넘었습니다"),

  CONSENT_REQUIRED(HttpStatus.FORBIDDEN, "서비스 이용 안내와 개인정보 처리 안내에 본인이 동의해야 합니다"),
  INVITATION_NOT_FOUND(HttpStatus.NOT_FOUND, "초대 링크를 찾을 수 없습니다"),
  INVITATION_UNAVAILABLE(HttpStatus.CONFLICT, "만료되었거나 이미 사용 또는 취소된 초대입니다"),
  LAST_OWNER_REQUIRED(HttpStatus.CONFLICT, "마지막 대표 관리자는 제거하거나 권한을 낮출 수 없습니다"),
  OWNER_SCOPES_REQUIRED(HttpStatus.CONFLICT, "대표관리자는 관리 권한이 필요합니다"),
  LAST_OPERATOR_REQUIRED(HttpStatus.CONFLICT, "마지막 운영자는 권한을 낮출 수 없습니다"),

  // 413 Content Too Large
  UPLOAD_SIZE_EXCEEDED(HttpStatus.CONTENT_TOO_LARGE, "업로드 크기가 상한을 넘었습니다"),

  // 415 Unsupported Media Type
  REQUEST_TYPE_NOT_SUPPORTED(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 요청 Content-Type입니다"),

  // 429 Too Many Requests
  RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS, "요청 한도를 넘었습니다"),

  // 500 Internal Server Error
  INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류입니다"),

  // 503 Service Unavailable
  REQUEST_TIMED_OUT(HttpStatus.SERVICE_UNAVAILABLE, "요청 처리 시간이 초과되었습니다"),
  TOSS_PAYMENTS_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "토스페이먼츠 응답을 확인하지 못했습니다");

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
