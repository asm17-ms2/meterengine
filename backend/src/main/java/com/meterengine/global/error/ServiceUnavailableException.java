package com.meterengine.global.error;

import org.springframework.http.HttpStatus;

public class ServiceUnavailableException extends BusinessException {

  public ServiceUnavailableException(ErrorCode errorCode) {
    super(errorCode, HttpStatus.SERVICE_UNAVAILABLE);
  }
}
