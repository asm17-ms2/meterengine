package com.meterengine.global.validation;

import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ErrorResponse.FieldError;
import com.meterengine.global.error.InvalidRequestException;
import java.time.OffsetDateTime;
import java.util.List;

public enum ArchiveStatus {
  ACTIVE,
  ARCHIVED,
  ALL;

  public static ArchiveStatus parse(String value) {
    return switch (value == null ? "active" : value) {
      case "active" -> ACTIVE;
      case "archived" -> ARCHIVED;
      case "all" -> ALL;
      default ->
          throw new InvalidRequestException(
              ErrorCode.VALIDATION_ERROR,
              List.of(new FieldError("status", "active, archived, all 중 하나여야 합니다")));
    };
  }

  public boolean includes(OffsetDateTime archivedAt) {
    return this == ALL || (this == ARCHIVED) == (archivedAt != null);
  }
}
