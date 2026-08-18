package com.meterengine.event.controller;

import com.meterengine.ErrorCodes;
import com.meterengine.ProblemMembers;
import com.meterengine.event.exception.UnknownCustomerException;
import java.util.List;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/**
 * 수집 오류를 RFC 9457 problem+json으로 매핑한다 (MS2-130).
 *
 * <p>여기 쓰는 {@link ProblemDetail}은 Spring이 자기 예외에 쓰는 바로 그 클래스다. 형식을 흉내 낸 것이 아니라 같은 타입이라 프레임워크 오류와
 * 필드가 같고, 우리 것만 확장 멤버 code가 붙는다. 그 짝이 되는 프레임워크 쪽 설정은 application.properties의
 * spring.mvc.problemdetails.enabled다.
 *
 * <p><b>이 컨트롤러에만 건다.</b> 셀렉터를 주지 않으면 advice는 전역이다. 그러면 나중에 고객 등록 API가 생겼을 때 거기서 난
 * DataIntegrityViolationException까지 아래 핸들러로 와서 "이벤트를 저장할 수 없다"는 엉뚱한 응답이 나간다. 예외 타입을 좁혀서 막을 수는 없다.
 * jsonb가 거부한 값(22P05)과 FK 위반(23503)이 둘 다 같은 DataIntegrityViolationException이라 어느 도메인에서 왔는지 구분되지 않기
 * 때문이다. 범위를 묶어 두면 다른 컨트롤러는 프레임워크 기본 ProblemDetail을 받고, 필요한 도메인이 자기 advice를 따로 붙이면 된다.
 *
 * <p><b>ResponseEntityExceptionHandler를 상속하면 안 된다.</b> 자동 설정이
 * {@code @ConditionalOnMissingBean(ResponseEntityExceptionHandler.class)}라 상속하는 순간 기본 처리가 통째로 물러난다.
 * 대신 {@code @Order}가 필요하다. 범위를 좁혀도 이 컨트롤러에는 자동 설정 핸들러가 함께 걸린다. 아래 검증 핸들러가 프레임워크 예외를 가로채는데, 자동 설정
 * 핸들러가 {@code @Order(0)}이라 순서를 주지 않으면 기본 처리가 이긴다.
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

  /**
   * 쿼리 파라미터의 제약 위반을 400으로 돌려준다 (MS2-131).
   *
   * <p>{@code @Min}, {@code @Max}가 붙은 쿼리 파라미터는 {@code @Valid @RequestBody}와 다른 경로로 검증된다. 아래 {@link
   * #handleValidationFailure} 핸들러가 잡는 {@code MethodArgumentNotValidException}이 아니라 {@code
   * HandlerMethodValidationException}이 나므로, 이 핸들러가 없으면 {@code size=101}이 400이 아니라 500으로 샌다.
   *
   * <p><b>컨트롤러에 {@code @Validated}를 붙이면 안 된다.</b> 그러면 AOP 프록시가 먼저 걸려 {@code
   * ConstraintViolationException}이 나고, 이 핸들러를 비껴가 500이 된다(실측). Spring 6.1부터 컨트롤러 메서드 검증이 내장이라 애너테이션
   * 없이도 제약이 걸리고, 그 경로가 이 예외를 던진다.
   */
  @ExceptionHandler(HandlerMethodValidationException.class)
  ProblemDetail handleParameterConstraintFailure(HandlerMethodValidationException exception) {
    List<Map<String, String>> errors =
        exception.getParameterValidationResults().stream()
            .flatMap(
                result ->
                    result.getResolvableErrors().stream()
                        .map(
                            error ->
                                Map.of(
                                    "field",
                                    result.getMethodParameter().getParameterName() == null
                                        ? "unknown"
                                        : result.getMethodParameter().getParameterName(),
                                    "message",
                                    error.getDefaultMessage() == null
                                        ? "invalid"
                                        : error.getDefaultMessage())))
            .toList();

    return validationProblem(errors);
  }

  /**
   * 필수 헤더가 없을 때 400으로 돌려준다 (MS2-131).
   *
   * <p>{@code X-Organization-Id}를 빼먹은 요청이 여기 온다. 프레임워크도 400으로 내주지만 {@code code}가 없어, FE 공통 오류 컴포넌트가
   * 이 경우만 문구를 못 고른다. 가장 흔한 400이라 오히려 빠뜨리기 쉬운 자리다.
   *
   * <p>MS2-126이 Bearer 인증을 붙이면 이 경우는 400이 아니라 401이 되고 이 핸들러는 없어진다.
   */
  @ExceptionHandler(MissingRequestHeaderException.class)
  ProblemDetail handleMissingHeader(MissingRequestHeaderException exception) {
    return validationProblem(
        List.of(Map.of("field", exception.getHeaderName(), "message", "is required")));
  }

  /**
   * 쿼리 파라미터를 선언한 타입으로 못 바꿀 때 400으로 돌려준다 (MS2-131).
   *
   * <p>{@code ?customer_id=abc}나 {@code ?month=2026-13}이 여기 온다. 프레임워크도 이 예외를 400으로 내주지만 {@code
   * code}가 없다. 8/12에 오류 문구를 {@code code}로 고르기로 해서, 조회의 400 중 일부만 {@code code}가 빠지면 FE가 그 경우만 분기하지
   * 못한다.
   *
   * <p>값을 응답에 담지 않는다. 도입사가 보낸 값을 그대로 돌려주면 반사형 노출이 되고, 무엇이 잘못됐는지는 필드 이름과 기대 타입이면 충분하다.
   *
   * <p><b>수집 API(POST)에도 함께 걸린다.</b> advice가 컨트롤러 단위라, {@code X-Organization-Id}가 UUID가 아닌 POST 요청도
   * 이제 {@code code=validation_error}를 받는다. MS2-130 때는 {@code code} 없는 프레임워크 기본형이었다. 두 메서드가 같은 헤더를
   * 같은 타입으로 받으므로 형식이 같아지는 편이 맞다.
   */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ProblemDetail handleParameterTypeMismatch(MethodArgumentTypeMismatchException exception) {
    Class<?> required = exception.getRequiredType();
    return validationProblem(
        List.of(
            Map.of(
                "field",
                exception.getName(),
                "message",
                required == null
                    ? "cannot be parsed"
                    : "cannot be parsed as %s".formatted(required.getSimpleName()))));
  }

  private ProblemDetail validationProblem(List<Map<String, String>> errors) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "the request could not be accepted as sent");
    problem.setTitle("Bad Request");
    problem.setProperty(ProblemMembers.CODE, ErrorCodes.VALIDATION_ERROR);
    problem.setProperty(ProblemMembers.ERRORS, errors);
    return problem;
  }

  /**
   * 형식 검증 실패에 어느 필드가 왜 걸렸는지 붙인다. 기본 처리는 필수 필드 다섯 개 중 무엇이 빠져도 detail이 "Invalid request content."로
   * 같아서, 도입사가 400을 받고도 무엇을 고칠지 알 수 없다.
   *
   * <p>필드명은 와이어 이름(event_type)이 아니라 record 컴포넌트 이름(eventType)으로 나온다. 검증이 Java 필드 위에서 돌기 때문이고, 이번
   * 슬라이스는 두 이름이 기계적으로 대응해서 그대로 둔다.
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail handleValidationFailure(MethodArgumentNotValidException exception) {
    List<Map<String, String>> errors =
        exception.getBindingResult().getFieldErrors().stream()
            .map(
                (FieldError error) ->
                    Map.of(
                        "field",
                        error.getField(),
                        "message",
                        error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage()))
            .toList();

    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Invalid request content.");
    problem.setTitle("Bad Request");
    problem.setProperty(ProblemMembers.CODE, ErrorCodes.VALIDATION_ERROR);
    problem.setProperty(ProblemMembers.ERRORS, errors);
    return problem;
  }
}
