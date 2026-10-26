package com.meterengine.global.error;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends BusinessException {

  public UnauthorizedException(ErrorCode errorCode) {
    super(errorCode, HttpStatus.UNAUTHORIZED);
  }
}
