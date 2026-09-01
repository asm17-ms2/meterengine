package com.meterengine.customer.controller;

import com.meterengine.ErrorCodes;
import com.meterengine.ProblemMembers;
import com.meterengine.customer.exception.CustomerHasEventsException;
import com.meterengine.customer.exception.CustomerNotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = CustomerController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class CustomerExceptionHandler {

  @ExceptionHandler(CustomerNotFoundException.class)
  ProblemDetail handleNotFound(CustomerNotFoundException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    problem.setTitle("Customer not found");
    problem.setProperty(ProblemMembers.CODE, ErrorCodes.CUSTOMER_NOT_FOUND);
    return problem;
  }

  @ExceptionHandler(CustomerHasEventsException.class)
  ProblemDetail handleHasEvents(CustomerHasEventsException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    problem.setTitle("Customer has usage events");
    problem.setProperty(ProblemMembers.CODE, ErrorCodes.CUSTOMER_HAS_EVENTS);
    return problem;
  }

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
