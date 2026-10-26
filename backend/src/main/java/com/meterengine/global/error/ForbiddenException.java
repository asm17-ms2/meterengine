package com.meterengine.global.error;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends BusinessException {

  public ForbiddenException(ErrorCode errorCode) {
    super(errorCode, HttpStatus.FORBIDDEN);
  }
}
