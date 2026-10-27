package com.meterengine.global.error;

import org.springframework.http.HttpStatus;

public class TooManyRequestsException extends BusinessException {
  private final Long retryAfterSeconds;

  public TooManyRequestsException(ErrorCode errorCode) {
    super(errorCode, HttpStatus.TOO_MANY_REQUESTS);
    this.retryAfterSeconds = null;
  }

  public TooManyRequestsException(ErrorCode errorCode, long retryAfterSeconds) {
    super(errorCode, HttpStatus.TOO_MANY_REQUESTS);
    if (retryAfterSeconds < 1) throw new IllegalArgumentException("Retry-After must be positive");
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public Long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
