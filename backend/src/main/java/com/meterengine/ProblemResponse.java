package com.meterengine;

import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;
import java.util.List;

@Schema(description = "RFC 9457 problem+json 오류 응답. code 확장 멤버가 최상위에 실린다.")
public record ProblemResponse(
    @Schema(description = "상태의 짧은 요약. 화면 문구로 쓰지 않는다.", example = "Bad Request") String title,
    @Schema(
            description = "HTTP 상태 코드. 응답 상태 줄과 같다. 이 스키마에서 유일하게 항상 실리는 필드다.",
            example = "400",
            requiredMode = Schema.RequiredMode.REQUIRED)
        Integer status,
    @Schema(
            description =
                """
                이 요청에 한정된 설명. 영어이고 로그와 개발자용이다. 화면에 그대로 띄우지 않는다.
                도입사에게 보여줄 문구는 code로 고르고, 필드별 사유는 errors[].message를 쓴다.
                """,
            example = "the request could not be accepted as sent")
        String detail,
    @Schema(description = "문제가 난 요청의 경로.", example = "/v1/events") URI instance,
    @Schema(
            description =
                """
                오류 종류를 고르는 기계 판독용 값. 화면 문구는 이 값으로 고른다.
                지금 도달 가능한 4xx에는 모두 실리지만 required가 아니다. 서버가 분류하지 못한 4xx에는
                일부러 붙이지 않으므로, 클라이언트는 값이 없을 때의 기본 문구를 갖고 있어야 한다.
                """,
            example = ErrorCodes.VALIDATION_ERROR,
            allowableValues = {
              ErrorCodes.VALIDATION_ERROR,
              ErrorCodes.UNKNOWN_CUSTOMER_REFERENCE,
              ErrorCodes.INVALID_EVENT,
              ErrorCodes.MALFORMED_REQUEST_BODY,
              ErrorCodes.REQUEST_TYPE_NOT_SUPPORTED,
              ErrorCodes.RESPONSE_TYPE_NOT_ACCEPTABLE,
              ErrorCodes.METHOD_NOT_ALLOWED,
              ErrorCodes.ENDPOINT_NOT_FOUND,
              ErrorCodes.CUSTOMER_NOT_FOUND,
              ErrorCodes.CUSTOMER_HAS_EVENTS,
              ErrorCodes.UNKNOWN_ORGANIZATION,
              ErrorCodes.METRIC_NOT_FOUND,
              ErrorCodes.PRICE_POLICY_ALREADY_EXISTS,
              ErrorCodes.INVALID_PRICE_POLICY,
              ErrorCodes.METRIC_ALREADY_EXISTS,
              ErrorCodes.INVALID_BILLABLE_METRIC
            })
        String code,
    @Schema(
            description =
                """
                형식 검증에 걸린 필드 목록. code=validation_error일 때만 실리고 다른 code에는 아예 없다.
                필드마다 도입사가 보낸 이름(field)과 사유(message)가 들어 있다.
                """)
        List<ProblemFieldError> errors) {}
