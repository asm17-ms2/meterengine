package com.meterengine.global.error;

import org.springframework.http.HttpStatus;

public class NotFoundException extends BusinessException {

  public NotFoundException(ErrorCode errorCode) {
    super(errorCode, HttpStatus.NOT_FOUND);
  }
}
