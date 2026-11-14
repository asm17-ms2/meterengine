package com.meterengine.liveenvironment.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BusinessRegistrationNumberTest {

  @Test
  void 검증_숫자가_맞는_열_자리_숫자만_받는다() {
    assertThat(BusinessRegistrationNumber.isValid("1234567891")).isTrue();
    assertThat(BusinessRegistrationNumber.isValid("1248100998")).isTrue();
    assertThat(BusinessRegistrationNumber.isValid("1234567890")).isFalse();
    assertThat(BusinessRegistrationNumber.isValid("1248100999")).isFalse();
  }

  @Test
  void 길이가_다르거나_숫자가_아닌_글자가_있으면_받지_않는다() {
    assertThat(BusinessRegistrationNumber.isValid(null)).isFalse();
    assertThat(BusinessRegistrationNumber.isValid("")).isFalse();
    assertThat(BusinessRegistrationNumber.isValid("123456789")).isFalse();
    assertThat(BusinessRegistrationNumber.isValid("12345678912")).isFalse();
    assertThat(BusinessRegistrationNumber.isValid("123-45-678")).isFalse();
    assertThat(BusinessRegistrationNumber.isValid("１２３４５６７８９１")).isFalse();
  }
}
