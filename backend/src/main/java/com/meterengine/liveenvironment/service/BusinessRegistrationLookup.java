package com.meterengine.liveenvironment.service;

public interface BusinessRegistrationLookup {

  // 국세청에 사업자 상태를 묻는다. 결과를 받지 못하면 예외 없이 UNAVAILABLE을 돌려준다.
  BusinessRegistrationStatus find(String businessRegistrationNumber);
}
