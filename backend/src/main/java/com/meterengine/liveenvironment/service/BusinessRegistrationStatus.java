package com.meterengine.liveenvironment.service;

// taxType은 국세청이 준 과세유형 명칭이고 사업자 상태를 받았을 때만 있다.
public record BusinessRegistrationStatus(State state, String taxType) {

  public enum State {
    OPERATING,
    SUSPENDED,
    CLOSED,
    UNREGISTERED,
    UNAVAILABLE
  }

  public static BusinessRegistrationStatus unavailable() {
    return new BusinessRegistrationStatus(State.UNAVAILABLE, null);
  }
}
