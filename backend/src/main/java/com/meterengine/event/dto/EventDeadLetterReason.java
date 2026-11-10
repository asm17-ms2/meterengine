package com.meterengine.event.dto;

// 다시 읽어도 저장할 수 없어 DLQ로 보내는 이유. DLQ 레코드의 failure_reason 헤더에 code를 싣는다.
public enum EventDeadLetterReason {
  INVALID_MESSAGE("invalid_message"),
  UNKNOWN_SCHEMA_VERSION("unknown_schema_version"),
  CONSTRAINT_VIOLATION("constraint_violation");

  private final String code;

  EventDeadLetterReason(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }
}
