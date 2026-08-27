package com.meterengine.pricing.controller;

import com.meterengine.ErrorCodes;
import com.meterengine.ProblemMembers;
import com.meterengine.metric.exception.MetricNotFoundException;
import com.meterengine.pricing.exception.InvalidPricePolicyException;
import com.meterengine.pricing.exception.PricePolicyAlreadyExistsException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = PricePolicyController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class PricePolicyExceptionHandler {

  @ExceptionHandler(MetricNotFoundException.class)
  ProblemDetail handleMetricNotFound(MetricNotFoundException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    problem.setTitle("Metric not found");
    problem.setProperty(ProblemMembers.CODE, ErrorCodes.METRIC_NOT_FOUND);
    return problem;
  }

  @ExceptionHandler(PricePolicyAlreadyExistsException.class)
  ProblemDetail handleAlreadyExists(PricePolicyAlreadyExistsException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    problem.setTitle("Price policy already exists");
    problem.setProperty(ProblemMembers.CODE, ErrorCodes.PRICE_POLICY_ALREADY_EXISTS);
    return problem;
  }

  @ExceptionHandler(InvalidPricePolicyException.class)
  ProblemDetail handleInvalid(InvalidPricePolicyException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    problem.setTitle("Invalid price policy");
    problem.setProperty(ProblemMembers.CODE, ErrorCodes.INVALID_PRICE_POLICY);
    return problem;
  }
}
