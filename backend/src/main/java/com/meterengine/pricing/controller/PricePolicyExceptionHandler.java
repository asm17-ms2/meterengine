package com.meterengine.pricing.controller;

import com.meterengine.ErrorCodes;
import com.meterengine.ProblemMembers;
import com.meterengine.pricing.exception.InvalidPricePolicyException;
import com.meterengine.pricing.exception.MetricNotFoundException;
import com.meterengine.pricing.exception.PricePolicyAlreadyExistsException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 가격 정책 API의 도메인 오류를 RFC 9457 problem+json으로 매핑한다 (MS2-157).
 *
 * <p>이 컨트롤러에만 걸고, 도메인 예외만 잡고, {@code @Order(HIGHEST_PRECEDENCE)}를 유지하는 이유는 전부 {@code
 * CustomerExceptionHandler}와 같다. 프레임워크 예외는 {@link com.meterengine.FrameworkExceptionHandler}의 몫이다.
 *
 * <p>고객 API와 달리 {@code DataIntegrityViolationException} 핸들러가 없다. 미등록 도입사는 미터 존재 확인이 404로 먼저 걸러
 * organization FK 위반에 도달하지 못하고, 정책 PK 경합은 서비스가 409 예외로 바꾼다.
 */
@RestControllerAdvice(assignableTypes = PricePolicyController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class PricePolicyExceptionHandler {

  /** 경로가 가리킨 미터가 없을 때 404. 없는 미터, 다른 도입사 소속, 미등록 도입사를 구별해 답하지 않는다. */
  @ExceptionHandler(MetricNotFoundException.class)
  ProblemDetail handleMetricNotFound(MetricNotFoundException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    problem.setTitle("Metric not found");
    problem.setProperty(ProblemMembers.CODE, ErrorCodes.METRIC_NOT_FOUND);
    return problem;
  }

  /** 정책이 이미 있을 때 409. 요청은 올바르고 리소스의 지금 상태가 그 동작을 허용하지 않는다. */
  @ExceptionHandler(PricePolicyAlreadyExistsException.class)
  ProblemDetail handleAlreadyExists(PricePolicyAlreadyExistsException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    problem.setTitle("Price policy already exists");
    problem.setProperty(ProblemMembers.CODE, ErrorCodes.PRICE_POLICY_ALREADY_EXISTS);
    return problem;
  }

  /** 필드 사이의 관계가 정책으로 성립하지 않을 때 400. 짚을 필드가 하나가 아니라 errors 배열 없이 detail이 사유를 담는다. */
  @ExceptionHandler(InvalidPricePolicyException.class)
  ProblemDetail handleInvalid(InvalidPricePolicyException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    problem.setTitle("Invalid price policy");
    problem.setProperty(ProblemMembers.CODE, ErrorCodes.INVALID_PRICE_POLICY);
    return problem;
  }
}
