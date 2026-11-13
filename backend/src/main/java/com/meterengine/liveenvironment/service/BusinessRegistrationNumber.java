package com.meterengine.liveenvironment.service;

public final class BusinessRegistrationNumber {

  private static final int LENGTH = 10;
  private static final int[] WEIGHTS = {1, 3, 7, 1, 3, 7, 1, 3, 5};
  private static final int DECIMAL = 10;

  private BusinessRegistrationNumber() {}

  // 열 자리 숫자의 마지막 자리가 앞 아홉 자리로 계산한 검증 숫자와 같은지 본다.
  public static boolean isValid(String value) {
    if (value == null || value.length() != LENGTH) {
      return false;
    }
    int sum = 0;
    for (int index = 0; index < LENGTH; index++) {
      char digit = value.charAt(index);
      if (digit < '0' || digit > '9') {
        return false;
      }
      if (index < WEIGHTS.length) {
        sum += (digit - '0') * WEIGHTS[index];
      }
    }
    sum += (value.charAt(WEIGHTS.length - 1) - '0') * WEIGHTS[WEIGHTS.length - 1] / DECIMAL;
    return (DECIMAL - sum % DECIMAL) % DECIMAL == value.charAt(LENGTH - 1) - '0';
  }
}
