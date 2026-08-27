package com.meterengine.customer.controller;

import com.meterengine.ErrorCodes;
import com.meterengine.ProblemMembers;
import com.meterengine.customer.exception.CustomerHasEventsException;
import com.meterengine.customer.exception.CustomerHasInvoicesException;
import com.meterengine.customer.exception.CustomerNotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 고객 API의 도메인 오류를 RFC 9457 problem+json으로 매핑한다 (MS2-155).
 *
 * <p><b>이 컨트롤러에만 건다.</b> 수집 쪽 advice와 같은 이유다. 아래 {@link DataIntegrityViolationException} 핸들러는 "없는
 * 도입사를 가리켰다"고 답하는데, 전역으로 두면 이벤트 저장이 jsonb 값 때문에 거부됐을 때도 같은 문구가 나간다. 두 경우 다 같은 예외 타입이라 타입으로는 갈라낼 수
 * 없다.
 *
 * <p><b>도메인 예외만 잡는다.</b> 헤더 누락, 경로 UUID 형식 오류, 본문 검증 실패 같은 프레임워크 예외는 {@link
 * com.meterengine.FrameworkExceptionHandler}가 전역으로 처리한다 (MS2-150 4단계). 여기서 다시 잡으면 이 advice가 {@code
 * HIGHEST_PRECEDENCE}라 고객 API만 다른 모양이 된다.
 *
 * <p><b>{@code @Order(HIGHEST_PRECEDENCE)}는 유지한다.</b> 지금은 상속 핸들러와 겹치는 예외가 0개라 순서가 결과를 바꾸지 않지만, 나중에
 * 프레임워크 예외와 겹치는 도메인 예외가 생기면 도메인이 이겨야 한다.
 *
 * <p>{@code code} 값의 정본은 {@link ErrorCodes}이고 멤버 이름은 {@link ProblemMembers}에 있다. 문서에 나가는 오류 스키마는
 * {@link com.meterengine.ProblemResponse}이며, 그쪽 {@code allowableValues}에 아래 핸들러가 쓰는 code가 모두 올라가
 * 있어야 문서와 응답이 갈리지 않는다.
 */
@RestControllerAdvice(assignableTypes = CustomerController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class CustomerExceptionHandler {

  /**
   * 대상 고객을 찾지 못했을 때 404.
   *
   * <p>수집과 조회 API는 같은 상황을 400 {@code unknown_customer_reference}로 답한다. 거기서는 고객이 요청 본문이나 쿼리의 값이고,
   * 여기서는 경로가 가리키는 리소스 자체라 상태 코드가 갈리고, code 하나는 (HTTP 상태, 의미) 하나만 가리킨다는 MS2-150 규칙에 따라 code도 갈린다.
   */
  @ExceptionHandler(CustomerNotFoundException.class)
  ProblemDetail handleNotFound(CustomerNotFoundException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    problem.setTitle("Customer not found");
    problem.setProperty(ProblemMembers.CODE, ErrorCodes.CUSTOMER_NOT_FOUND);
    return problem;
  }

  /** 이벤트가 있어 지울 수 없을 때 409. 요청은 올바르고 리소스의 지금 상태가 그 동작을 허용하지 않는다. */
  @ExceptionHandler(CustomerHasEventsException.class)
  ProblemDetail handleHasEvents(CustomerHasEventsException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    problem.setTitle("Customer has usage events");
    problem.setProperty(ProblemMembers.CODE, ErrorCodes.CUSTOMER_HAS_EVENTS);
    return problem;
  }

  @ExceptionHandler(CustomerHasInvoicesException.class)
  ProblemDetail handleHasInvoices(CustomerHasInvoicesException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    problem.setTitle("Customer has finalized invoices");
    problem.setProperty(ProblemMembers.CODE, ErrorCodes.CUSTOMER_HAS_INVOICES);
    return problem;
  }

  /**
   * DB가 거부한 등록을 400으로 돌려준다.
   *
   * <p>여기 오는 실질적인 경우는 하나다. {@code X-Organization-Id}가 organization 테이블에 없는 UUID여서 FK가 INSERT를 거부한
   * 것. 헤더 값을 검증하는 곳이 없으므로(있어도 그 조회와 INSERT 사이가 다시 경합이다) 이 핸들러가 없으면 오타 하나가 500이 된다. 500은 서버가 잘못했다는
   * 신호라 도입사가 자기 헤더를 의심하지 않는다.
   *
   * <p>MS2-126이 Bearer 인증을 붙이면 인증 단계에서 걸러져 이 경우는 사라진다.
   *
   * <p>삭제의 FK 위반은 여기 오지 않는다. 이벤트나 확정 인보이스가 있는 고객을 지우려 할 때 나는 그 위반은 {@code CustomerService.delete}가
   * {@link CustomerHasEventsException}이나 {@link CustomerHasInvoicesException}으로 바꿔 던져 409가 된다. 여기
   * 오는 것과 같은 예외 타입이라 이 자리에서는 갈라낼 수 없어서, 뜻을 아는 자리에서 미리 바꾼다.
   *
   * <p>고객 이름 중복도 여기 오지 않는다. 유니크 제약 자체가 없어 중복 등록이 정상 동작이다.
   */
  @ExceptionHandler(DataIntegrityViolationException.class)
  ProblemDetail handleRejectedByDatabase(DataIntegrityViolationException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "the customer could not be stored; check that X-Organization-Id is a registered organization");
    problem.setTitle("Unknown organization");
    problem.setProperty(ProblemMembers.CODE, ErrorCodes.UNKNOWN_ORGANIZATION);
    return problem;
  }
}
