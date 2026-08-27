package com.meterengine.metric.controller;

import com.meterengine.ErrorCodes;
import com.meterengine.ProblemMembers;
import com.meterengine.metric.exception.InvalidBillableMetricException;
import com.meterengine.metric.exception.MetricAlreadyExistsException;
import com.meterengine.metric.exception.MetricBasisHasEventsException;
import com.meterengine.metric.exception.MetricHasEventsException;
import com.meterengine.metric.exception.MetricHasPricePolicyException;
import com.meterengine.metric.exception.MetricNotFoundException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = BillableMetricController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
class BillableMetricExceptionHandler {

  @ExceptionHandler(MetricAlreadyExistsException.class)
  ProblemDetail handleAlreadyExists(MetricAlreadyExistsException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    problem.setTitle("Metric already exists");
    problem.setProperty(ProblemMembers.CODE, ErrorCodes.METRIC_ALREADY_EXISTS);
    return problem;
  }

  @ExceptionHandler(InvalidBillableMetricException.class)
  ProblemDetail handleInvalid(InvalidBillableMetricException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
    problem.setTitle("Invalid billable metric");
    problem.setProperty(ProblemMembers.CODE, ErrorCodes.INVALID_BILLABLE_METRIC);
    return problem;
  }

  @ExceptionHandler(MetricNotFoundException.class)
  ProblemDetail handleMetricNotFound(MetricNotFoundException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
    problem.setTitle("Metric not found");
    problem.setProperty(ProblemMembers.CODE, ErrorCodes.METRIC_NOT_FOUND);
    return problem;
  }

  @ExceptionHandler(MetricBasisHasEventsException.class)
  ProblemDetail handleBasisHasEvents(MetricBasisHasEventsException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    problem.setTitle("Metric basis has usage events");
    problem.setProperty(ProblemMembers.CODE, ErrorCodes.METRIC_BASIS_HAS_EVENTS);
    return problem;
  }

  @ExceptionHandler(MetricHasEventsException.class)
  ProblemDetail handleHasEvents(MetricHasEventsException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    problem.setTitle("Metric has usage events");
    problem.setProperty(ProblemMembers.CODE, ErrorCodes.METRIC_HAS_EVENTS);
    return problem;
  }

  @ExceptionHandler(MetricHasPricePolicyException.class)
  ProblemDetail handleHasPricePolicy(MetricHasPricePolicyException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    problem.setTitle("Metric has price policy");
    problem.setProperty(ProblemMembers.CODE, ErrorCodes.METRIC_HAS_PRICE_POLICY);
    return problem;
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ProblemDetail handleRejectedByDatabase(DataIntegrityViolationException exception) {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "the metric could not be stored; check that X-Organization-Id is a registered organization");
    problem.setTitle("Unknown organization");
    problem.setProperty(ProblemMembers.CODE, ErrorCodes.UNKNOWN_ORGANIZATION);
    return problem;
  }
}
