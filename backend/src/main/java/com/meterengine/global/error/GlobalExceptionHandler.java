package com.meterengine.global.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class GlobalExceptionHandler {

  private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorResponse> handleException(Exception exception) {
    logger.error("unhandled exception", exception);
    return respond(ErrorCode.INTERNAL_SERVER_ERROR);
  }

  private static ResponseEntity<ErrorResponse> respond(ErrorCode errorCode) {
    return ResponseEntity.status(errorCode.getStatus()).body(ErrorResponse.from(errorCode));
  }
}
