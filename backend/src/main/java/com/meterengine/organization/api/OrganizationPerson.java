package com.meterengine.organization.api;

import java.util.UUID;

public record OrganizationPerson(UUID id, String email, String name, boolean consentRequired) {
  // 계정 입력 규칙의 정본. organization은 account를 가져올 수 없어 여기 두고 UserAccount가 가리킨다.
  public static final int NAME_MAX_LENGTH = 255;
  public static final int EMAIL_MAX_LENGTH = 254;
  public static final String EMAIL_PATTERN = ".+@.+\\..+";
  public static final String EMAIL_FORMAT_MESSAGE = "올바른 형식의 이메일 주소여야 합니다";
  public static final int PASSWORD_MIN_LENGTH = 8;
  public static final int PASSWORD_MAX_LENGTH = 72;
}
