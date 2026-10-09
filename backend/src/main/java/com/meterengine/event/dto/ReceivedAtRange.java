package com.meterengine.event.dto;

import java.time.OffsetDateTime;

// 서버가 이벤트를 받은 시각이 startAt 이상 cutoffAt 미만인 구간이다.
public record ReceivedAtRange(OffsetDateTime startAt, OffsetDateTime cutoffAt) {

  public static ReceivedAtRange unbounded() {
    return new ReceivedAtRange(OffsetDateTime.MIN, OffsetDateTime.MAX);
  }
}
