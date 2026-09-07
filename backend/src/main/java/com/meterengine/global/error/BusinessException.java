package com.meterengine.global.error;

import com.meterengine.global.error.ErrorResponse.FieldError;
import java.util.List;
import org.springframework.http.HttpStatus;

public abstract class BusinessException extends RuntimeException {

  private final ErrorCode errorCode;

  protected BusinessException(ErrorCode errorCode, HttpStatus status) {
    super(errorCode.getMessage());
    if (errorCode.getStatus() != status) {
      throw new IllegalArgumentException(
          errorCode + " is " + errorCode.getStatus() + ", not " + status);
    }
    this.errorCode = errorCode;
  }

  public ErrorCode getErrorCode() {
    return errorCode;
  }

  public List<FieldError> getErrors() {
    return List.of();
  }
}
