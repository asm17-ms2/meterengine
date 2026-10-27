package com.meterengine.global.validation;

import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ErrorResponse.FieldError;
import com.meterengine.global.error.InvalidRequestException;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class UnknownQueryParameterValidator {
  private UnknownQueryParameterValidator() {}

  public static void validate(Map<String, ?> parameters, Set<String> supported) {
    if (!supported.containsAll(parameters.keySet())) {
      throw new InvalidRequestException(
          ErrorCode.VALIDATION_ERROR,
          List.of(new FieldError("query", "이 조회에서 지원하지 않는 조건이 포함되어 있습니다")));
    }
  }
}
