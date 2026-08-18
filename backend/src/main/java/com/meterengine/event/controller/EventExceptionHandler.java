package com.meterengine.event.controller;

import com.meterengine.ErrorCodes;
import com.meterengine.ProblemMembers;
import com.meterengine.event.exception.UnknownCustomerException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 수집 오류를 RFC 9457 problem+json으로 매핑한다 (MS2-130).
 *
 * <p>여기 쓰는 {@link ProblemDetail}은 Spring이 자기 예외에 쓰는 바로 그 클래스다. 형식을 흉내 낸 것이 아니라 같은 타입이라 프레임워크 오류와
 * 필드가 같다. 그 짝이 되는 프레임워크 쪽 설정은 application.properties의 spring.mvc.problemdetails.enabled다.
 *
 * <p><b>[정정 2026-08-17, MS2-150 4단계]</b> 예전에는 이 자리에 "우리 것만 확장 멤버 code가 붙는다"고 적혀 있었다. 도메인 오류와 프레임워크
 * 오류를 가르는 말이었는데 <b>지금은 둘 다 붙는다.</b> {@link com.meterengine.FrameworkExceptionHandler}가 프레임워크 4xx에도
 * code를 붙이기 때문이고, 그래서 7단계가 오류 스키마도 하나로 합쳤다. code가 붙고 안 붙고로 두 갈래를 나누는 서술이 남아 있으면 그 갈래가 아직 있는 줄로 읽힌다.
 *
 * <p><b>code 값은 {@link com.meterengine.ErrorCodes}가 정본이다 (MS2-150).</b> OpenAPI 문서는 이 클래스가 아니라 문서
 * 전용 레코드 {@link com.meterengine.ProblemResponse}에서 나온다. {@code setProperty}로 넣은 확장 멤버를 springdoc이 못
 * 읽어서 갈라놓은 것이라 (MS2-140, PR #31 리뷰), 예전에는 여기만 고치면 생성물이 조용히 낡았다. 이제 양쪽이 같은 상수를 참조하므로 값을 늘리거나 없앨 때
 * {@code ErrorCodes} 한 곳만 고친다.
 *
 * <p><b>이 컨트롤러에만 건다.</b> 셀렉터를 주지 않으면 advice는 전역이다. 그러면 나중에 고객 등록 API가 생겼을 때 거기서 난
 * DataIntegrityViolationException까지 아래 핸들러로 와서 "이벤트를 저장할 수 없다"는 엉뚱한 응답이 나간다. 예외 타입을 좁혀서 막을 수는 없다.
 * jsonb가 거부한 값(22P05)과 FK 위반(23503)이 둘 다 같은 DataIntegrityViolationException이라 어느 도메인에서 왔는지 구분되지 않기
 * 때문이다. 범위를 묶어 두면 다른 컨트롤러는 프레임워크 기본 ProblemDetail을 받고, 필요한 도메인이 자기 advice를 따로 붙이면 된다.
 *
 * <p><b>이제 도메인 예외만 잡는다 (MS2-150 4단계).</b> 프레임워크 예외 네 개({@code MethodArgumentNotValid}, {@code
 * HandlerMethodValidation}, {@code MissingRequestHeader}, {@code MethodArgumentTypeMismatch})는
 * {@link com.meterengine.FrameworkExceptionHandler}로 옮겼다. 그러지 않으면 이 advice가 {@code
 * HIGHEST_PRECEDENCE}라 {@code /v1/events}만 옛 모양으로 남아 엔드포인트 사이 응답 형식이 더 갈렸다.
 *
 * <p>{@code errors} 배열을 만드는 코드도 함께 옮겼다. 프레임워크 기본 처리는 그것을 만들지 않으므로 상속 핸들러가 네 예외 타입에서 다시 뽑는다.
 *
 * <p><b>{@code @Order(HIGHEST_PRECEDENCE)}는 유지한다.</b> 지금은 상속 핸들러와 겹치는 예외가 0개라 순서가 결과를 바꾸지 않지만, 나중에
 * 프레임워크 예외와 겹치는 도메인 예외가 생기면 도메인이 이겨야 한다.
 */
@RestControllerAdvice(assignableTypes = EventController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class EventExceptionHandler {

  @ExceptionHandler(UnknownCustomerException.class)
  ProblemDetail handleUnknownCustomer(UnknownCustomerException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    problem.setTitle("Unknown customer");
    problem.setProperty(ProblemMembers.CODE, ErrorCodes.UNKNOWN_CUSTOMER_REFERENCE);
    return problem;
  }

  /**
   * DB가 저장을 거부한 이벤트를 400으로 돌려준다.
   *
   * <p>여기 오는 요청은 형식 검증과 고객 판정을 이미 통과했다. 남는 것은 DB가 담을 수 없는 값(예: properties 안의 NUL 문자)이거나 고객 판정과
   * INSERT 사이에 고객이 사라진 경우이고, 둘 다 같은 요청을 다시 보내도 성공하지 않는다. 없으면 500이 나가는데 5xx는 재시도해도 된다는 신호라 수집 클라이언트가
   * 저장되지도 않을 이벤트를 영원히 재전송한다.
   *
   * <p>중복 키는 여기 오지 않는다. EventIngestService가 먼저 잡아 200으로 답한다.
   *
   * <p>잡는 예외가 제약 위반 전반을 덮는 넓은 타입이라, 이 문구가 맞으려면 advice가 이 컨트롤러에만 걸려 있어야 한다 (클래스 javadoc 참조).
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  ProblemDetail handleRejectedByDatabase(DataIntegrityViolationException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "the event could not be stored as sent; retrying the same payload will not succeed");
    problem.setTitle("Invalid event");
    problem.setProperty(ProblemMembers.CODE, ErrorCodes.INVALID_EVENT);
    return problem;
  }
}
