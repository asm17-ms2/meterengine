package com.meterengine.liveenvironment.service;

// 국세청 조회를 끈 환경(로컬, 시험, 인증키를 넣기 전 운영)에서 쓴다. 모든 신청이 "자동 확인 못 함"으로 들어온다.
public class UncheckedBusinessRegistrationLookup implements BusinessRegistrationLookup {

  @Override
  public BusinessRegistrationStatus find(String businessRegistrationNumber) {
    return BusinessRegistrationStatus.unavailable();
  }
}
