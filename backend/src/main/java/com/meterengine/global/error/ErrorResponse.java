package com.meterengine.global.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "오류 응답입니다. code로 오류 종류를 구분하고, message는 code마다 하나인 한국어 문구입니다.")
public record ErrorResponse(
    @Schema(
            description = "오류 종류입니다. 오류별 처리는 이 값으로 분기합니다.",
            requiredMode = Schema.RequiredMode.REQUIRED)
        ErrorCode code,
    @Schema(
            description = "code마다 하나인 한국어 문구입니다. 예고 없이 바뀔 수 있으니 분기에 쓰지 않습니다.",
            example = "일시적인 오류로 요청을 처리하지 못했습니다. 잠시 뒤 다시 시도해 주세요.",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String message,
    @Schema(description = "틀린 필드 목록입니다. 400 응답에만 실립니다.") @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<FieldError> errors) {

  public static ErrorResponse from(ErrorCode errorCode) {
    return new ErrorResponse(errorCode, errorCode.getMessage(), List.of());
  }

  public static ErrorResponse of(ErrorCode errorCode, List<FieldError> errors) {
    return new ErrorResponse(errorCode, errorCode.getMessage(), List.copyOf(errors));
  }

  @Schema(description = "틀린 필드 하나")
  public record FieldError(
      @Schema(description = "틀린 필드의 이름입니다. 요청에 쓴 이름과 같습니다.", example = "event_type") String field,
      @Schema(description = "이 필드의 값이 거절된 이유", example = "공백일 수 없습니다") String message) {}
}
