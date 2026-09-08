package com.meterengine.global.error;

import com.meterengine.global.error.ErrorResponse.FieldError;
import java.util.List;
import org.springframework.http.HttpStatus;

public class InvalidRequestException extends BusinessException {

  private final List<FieldError> errors;

  public InvalidRequestException(ErrorCode errorCode) {
    this(errorCode, List.of());
  }

  public InvalidRequestException(ErrorCode errorCode, List<FieldError> errors) {
    super(errorCode, HttpStatus.BAD_REQUEST);
    this.errors = List.copyOf(errors);
  }

  @Override
  public List<FieldError> getErrors() {
    return errors;
  }
}
