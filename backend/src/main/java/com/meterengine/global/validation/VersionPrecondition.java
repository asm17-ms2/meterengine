package com.meterengine.global.validation;

import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ErrorResponse.FieldError;
import com.meterengine.global.error.InvalidRequestException;
import java.util.List;

public final class VersionPrecondition {
  private VersionPrecondition() {}

  public static Long parse(String header) {
    if (header == null) return null;
    if (!header.matches("\"[0-9]{1,19}\"")) throw invalid();
    try {
      return Long.valueOf(header.substring(1, header.length() - 1));
    } catch (NumberFormatException exception) {
      throw invalid();
    }
  }

  public static void requireMatch(Long expected, long current) {
    if (expected != null && expected != current)
      throw new ConflictException(ErrorCode.RESOURCE_VERSION_CONFLICT);
  }

  private static InvalidRequestException invalid() {
    return new InvalidRequestException(
        ErrorCode.VALIDATION_ERROR,
        List.of(new FieldError("If-Match", "조회한 version을 큰따옴표로 감싼 값을 보내세요")));
  }
}
