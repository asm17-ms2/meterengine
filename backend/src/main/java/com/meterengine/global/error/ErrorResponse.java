package com.meterengine.global.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "오류 응답. code로 오류 종류를 구분하고, message는 code마다 하나인 한국어 문구다.")
public record ErrorResponse(
    @Schema(description = "오류 종류. 오류별 처리는 이 값으로 분기한다.", requiredMode = Schema.RequiredMode.REQUIRED)
        ErrorCode code,
    @Schema(
            description = "code마다 하나인 한국어 문구. 예고 없이 바뀔 수 있으니 분기에 쓰지 않는다.",
            example = "서버 내부 오류입니다",
            requiredMode = Schema.RequiredMode.REQUIRED)
        String message,
    @Schema(description = "틀린 필드 목록. 400 응답에만 실린다.") @JsonInclude(JsonInclude.Include.NON_EMPTY)
        List<FieldError> errors) {

  public static ErrorResponse from(ErrorCode errorCode) {
    return new ErrorResponse(errorCode, errorCode.getMessage(), List.of());
  }

  public static ErrorResponse of(ErrorCode errorCode, List<FieldError> errors) {
    return new ErrorResponse(errorCode, errorCode.getMessage(), List.copyOf(errors));
  }

  @Schema(description = "틀린 필드 하나")
  public record FieldError(
      @Schema(description = "틀린 값을 보낸 자리의 이름. 요청에 쓴 이름과 같다.", example = "event_type") String field,
      @Schema(description = "이 자리의 값이 거절된 이유.", example = "공백일 수 없습니다") String message) {}
}
