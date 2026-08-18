package com.meterengine;

import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;
import java.util.List;

/**
 * 오류 응답 본문의 문서 전용 표현 (MS2-140, MS2-150 7단계에서 하나로 합침).
 *
 * <p><b>왜 ProblemDetail을 스키마로 물리지 않나.</b> {@code org.springframework.http.ProblemDetail}은 확장 멤버를
 * {@code Map<String, Object> properties} 한 필드에 담고, 직렬화할 때 Jackson이 그 맵을 최상위로 펼친다 ({@code
 * ProblemDetailJacksonMixin}이 게터에 {@code @JsonAnyGetter}를 붙인다). springdoc은 그 사정을 모른 채 자바 필드 구조만
 * 읽으므로, 그 클래스를 물리면 <b>실제 응답에는 없는 {@code properties} 객체가 스키마에 생기고, 실제로 나가는 확장 멤버는 하나도 안 나온다.</b>
 * 생성물이 계약과 어긋나 FE가 문서대로 읽으면 없는 필드를 보게 된다 (PR #31 리뷰 지적).
 *
 * <p>그래서 나가는 모양 그대로를 필드로 선언한 문서 전용 레코드를 둔다. 이 타입은 어디서도 만들어지거나 반환되지 않는다. 응답을 실제로 만드는 것은 여전히 {@code
 * ProblemDetail}이고, 이 레코드는 {@code @Schema(implementation = ...)}이 가리키는 대상일 뿐이다.
 *
 * <p><b>[MS2-150 7단계] 오류 스키마가 이것 하나다.</b> 예전에는 {@code code}가 붙는 {@code CodedProblemResponse}와 안 붙는
 * 이 타입 둘로 갈라져 있었고, {@code /v1/usage}와 {@code /v1/invoice}가 후자를 가리켰다. 4단계가 {@link
 * FrameworkExceptionHandler}로 프레임워크 4xx 전부에 {@code code}를 붙이면서 code 없는 4xx가 사라져 변종이 필요 없어졌다. 네
 * 오퍼레이션이 전부 이 스키마를 가리킨다.
 *
 * <p><b>{@code type}은 선언하지 않는다 (7단계 결정).</b> RFC 9457의 정식 멤버이고 {@code ProblemDetail}에도 필드가 있지만 응답에
 * 나가지 않는다. 값이 기본값 {@code about:blank}면 Spring이 직렬화에서 빼고, 우리는 {@code setType}을 부르는 곳이 0곳이다. 4xx
 * 13경로(코드 8종, 상태 400/404/405/406/415, 두 핸들러, 세 엔드포인트)의 최상위 키를 받아 세어 0건을 확인했다. 나가지 않는 필드를 문서에 두면
 * 인수기준 4("문서에만 있는 필드가 0개")와 6(키 집합 양방향 대조)이 설계상 반드시 실패하고, 통과시키려면 그 두 검사를 허용 목록 방식으로 낮춰야 한다. 문서와 응답이
 * 갈리는 것을 잡는 유일한 장치를 무디게 만드는 거래라 받지 않았다. 문제 유형마다 URI를 부여하는 RFC 본래 방식을 도입하면 그때 되돌린다.
 *
 * <p><b>{@code required}는 {@code status} 하나다 (7단계 결정).</b> 유일하게 구조로 보장되는 값이라서다. {@code
 * ProblemDetail.status}는 {@code int} 원시형이라 null이 될 수 없고 직렬화에서 빠질 수 없다. 나머지 넷({@code title}, {@code
 * detail}, {@code instance}, {@code code})은 실측 13경로에 전부 실렸지만 <b>측정과 보장은 다르다.</b> 앞의 셋은 {@code
 * ProblemDetail}에서 nullable 필드이고, {@code code}는 {@link FrameworkExceptionHandler}가 모르는 예외에 일부러 안
 * 붙인다(그 자리에 {@code null} 분기가 있다). 지금 도달 가능한 4xx를 다 열거했다고 해서 앞으로 생길 4xx까지 약속할 수는 없다. 확인하지 않은 것을 계약으로
 * 약속하지 않는다는 것이 이 문서의 기존 입장이고 그대로 둔다.
 *
 * <p>{@code errors}는 실릴 조건이 따로 있다. {@code code=validation_error}일 때만 붙는다.
 *
 * <p><b>패키지를 새로 파지 않았다.</b> 여러 도메인({@code event}, {@code metric}, {@code invoice})이 함께 쓰는 타입이라 어느
 * 한쪽에 둘 수 없고, 이것만으로 공용 패키지를 파면 그 배치가 관례가 된다. {@code OpenApiConfig}가 루트에 있는 것과 같은 자리에 둔다 (MS2-149가
 * 정한 도메인 패키지 넷 중 어디에도 속하지 않는다).
 *
 * @param title 유형의 짧은 요약. 같은 상태면 같은 문구다
 * @param status HTTP 상태 코드. 응답 상태 줄과 같은 값이다
 * @param detail 이 요청에 한정된 설명. 영어이고 로그와 개발자용이다. 화면에 그대로 띄우지 않는다 (B-2)
 * @param instance 문제가 난 요청의 경로
 * @param code 오류 종류를 고르는 기계 판독용 값. FE 공통 오류 컴포넌트가 이 값으로 문구를 고른다
 * @param errors 형식 검증에 걸린 필드 목록. {@code code=validation_error}일 때만 실린다
 */
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
              ErrorCodes.UNKNOWN_ORGANIZATION
            })
        String code,
    @Schema(
            description =
                """
                형식 검증에 걸린 필드 목록. code=validation_error일 때만 실리고 다른 code에는 아예 없다.
                필드마다 도입사가 보낸 이름(field)과 사유(message)가 들어 있다.
                """)
        List<ProblemFieldError> errors) {}
