package com.meterengine.global.error;

import org.springframework.http.HttpStatus;

public class ConflictException extends BusinessException {

  public ConflictException(ErrorCode errorCode) {
    super(errorCode, HttpStatus.CONFLICT);
  }
}
