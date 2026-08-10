package com.meterengine.usage;

import java.util.List;
import java.util.Map;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 수집 오류를 RFC 9457 problem+json으로 매핑한다 (MS2-130).
 *
 * <p>여기 쓰는 {@link ProblemDetail}은 Spring이 자기 예외에 쓰는 바로 그 클래스다. 형식을 흉내 낸 것이 아니라 같은 타입이라 프레임워크 오류와
 * 필드가 같고, 우리 것만 확장 멤버 code가 붙는다. 그 짝이 되는 프레임워크 쪽 설정은 application.properties의
 * spring.mvc.problemdetails.enabled다.
 *
 * <p><b>ResponseEntityExceptionHandler를 상속하면 안 된다.</b> 자동 설정이
 * {@code @ConditionalOnMissingBean(ResponseEntityExceptionHandler.class)}라 상속하는 순간 기본 처리가 통째로 물러난다.
 * 대신 {@code @Order}가 필요하다. 아래 검증 핸들러가 프레임워크 예외를 가로채는데, 자동 설정 핸들러가 {@code @Order(0)}이라 순서를 주지 않으면 기본
 * 처리가 이긴다.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class UsageEventExceptionHandler {

  @ExceptionHandler(UnknownCustomerException.class)
  ProblemDetail handleUnknownCustomer(UnknownCustomerException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    problem.setTitle("Unknown customer");
    problem.setProperty("code", "customer_not_found");
    return problem;
  }

  /**
   * DB가 저장을 거부한 이벤트를 400으로 돌려준다.
   *
   * <p>여기 오는 요청은 형식 검증과 고객 판정을 이미 통과했다. 남는 것은 DB가 담을 수 없는 값(예: properties 안의 NUL 문자)이거나 고객 판정과
   * INSERT 사이에 고객이 사라진 경우이고, 둘 다 같은 요청을 다시 보내도 성공하지 않는다. 없으면 500이 나가는데 5xx는 재시도해도 된다는 신호라 수집 클라이언트가
   * 저장되지도 않을 이벤트를 영원히 재전송한다.
   *
   * <p>중복 키는 여기 오지 않는다. UsageEventIngestService가 먼저 잡아 200으로 답한다.
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  ProblemDetail handleRejectedByDatabase(DataIntegrityViolationException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "the event could not be stored as sent; retrying the same payload will not succeed");
    problem.setTitle("Invalid event");
    problem.setProperty("code", "invalid_event");
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
    problem.setProperty("code", "validation_error");
    problem.setProperty("errors", errors);
    return problem;
  }
}
