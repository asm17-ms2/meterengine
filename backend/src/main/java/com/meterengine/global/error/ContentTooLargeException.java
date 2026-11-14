package com.meterengine.global.error;

import org.springframework.http.HttpStatus;

public class ContentTooLargeException extends BusinessException {

  public ContentTooLargeException(ErrorCode errorCode) {
    super(errorCode, HttpStatus.CONTENT_TOO_LARGE);
  }
}
