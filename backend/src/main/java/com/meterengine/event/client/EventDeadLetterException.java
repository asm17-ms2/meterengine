package com.meterengine.event.client;

public class EventDeadLetterException extends RuntimeException {

  public EventDeadLetterException(String topic, Throwable cause) {
    super("DLQ 토픽 " + topic + "에 발행하지 못했다", cause);
  }
}
