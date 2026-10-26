package com.meterengine.global.error;

import org.springframework.http.HttpStatus;

public class TooManyRequestsException extends BusinessException {

  public TooManyRequestsException(ErrorCode errorCode) {
    super(errorCode, HttpStatus.TOO_MANY_REQUESTS);
  }
}
