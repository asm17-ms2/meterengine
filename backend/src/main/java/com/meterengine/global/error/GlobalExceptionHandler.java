package com.meterengine.global.error;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.global.error.ErrorResponse.FieldError;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingMatrixVariableException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.UnsatisfiedServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
class GlobalExceptionHandler {

  private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private static final String UNRESOLVED_FIELD = "unknown";

  private static final String REQUIRED_MESSAGE = "필수 항목입니다";

  private static final String TYPE_MISMATCH_MESSAGE = "%s 형식이어야 합니다";

  private static final String UNPARSEABLE_MESSAGE = "형식을 해석할 수 없습니다";

  private static final String INVALID_MESSAGE = "올바르지 않습니다";

  private static final String UNSATISFIED_PARAMETER_MESSAGE = "%s 조건에 맞지 않습니다";

  // 도메인 예외
  @ExceptionHandler(BusinessException.class)
  ResponseEntity<ErrorResponse> handleBusinessException(BusinessException exception) {
    return respond(exception.getErrorCode(), exception.getErrors());
  }

  // 400 요청 본문 검증 실패
  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
      MethodArgumentNotValidException exception) {
    Object target = exception.getBindingResult().getTarget();
    List<FieldError> errors =
        exception.getBindingResult().getFieldErrors().stream()
            .map(
                error ->
                    new FieldError(
                        requestFieldName(target, error.getField()),
                        messageOrDefault(error.getDefaultMessage())))
            .toList();
    return respond(ErrorCode.VALIDATION_ERROR, errors);
  }

  // 400 쿼리 파라미터와 헤더 검증 실패
  @ExceptionHandler(HandlerMethodValidationException.class)
  ResponseEntity<ErrorResponse> handleHandlerMethodValidationException(
      HandlerMethodValidationException exception) {
    List<FieldError> errors =
        exception.getParameterValidationResults().stream()
            .flatMap(
                result ->
                    result.getResolvableErrors().stream()
                        .map(
                            error ->
                                new FieldError(
                                    requestParameterName(result.getMethodParameter()),
                                    messageOrDefault(error.getDefaultMessage()))))
            .toList();
    return respond(ErrorCode.VALIDATION_ERROR, errors);
  }

  // 400 필수 헤더 누락
  @ExceptionHandler(MissingRequestHeaderException.class)
  ResponseEntity<ErrorResponse> handleMissingRequestHeaderException(
      MissingRequestHeaderException exception) {
    return respondMissingValue(exception.getHeaderName());
  }

  // 400 필수 쿼리 파라미터 누락
  @ExceptionHandler(MissingServletRequestParameterException.class)
  ResponseEntity<ErrorResponse> handleMissingServletRequestParameterException(
      MissingServletRequestParameterException exception) {
    return respondMissingValue(exception.getParameterName());
  }

  // 400 필수 멀티파트 파트 누락
  @ExceptionHandler(MissingServletRequestPartException.class)
  ResponseEntity<ErrorResponse> handleMissingServletRequestPartException(
      MissingServletRequestPartException exception) {
    return respondMissingValue(exception.getRequestPartName());
  }

  // 400 필수 쿠키 누락
  @ExceptionHandler(MissingRequestCookieException.class)
  ResponseEntity<ErrorResponse> handleMissingRequestCookieException(
      MissingRequestCookieException exception) {
    return respondMissingValue(exception.getCookieName());
  }

  // 400 필수 매트릭스 변수 누락
  @ExceptionHandler(MissingMatrixVariableException.class)
  ResponseEntity<ErrorResponse> handleMissingMatrixVariableException(
      MissingMatrixVariableException exception) {
    return respondMissingValue(exception.getVariableName());
  }

  // 400 만족되지 않은 params 조건
  @ExceptionHandler(UnsatisfiedServletRequestParameterException.class)
  ResponseEntity<ErrorResponse> handleUnsatisfiedServletRequestParameterException(
      UnsatisfiedServletRequestParameterException exception) {
    List<FieldError> errors =
        Arrays.stream(exception.getParamConditions())
            .map(
                condition ->
                    new FieldError(
                        parameterConditionName(condition),
                        UNSATISFIED_PARAMETER_MESSAGE.formatted(condition)))
            .toList();
    return respond(ErrorCode.VALIDATION_ERROR, errors);
  }

  // 400 경로 변수와 쿼리 파라미터 형식 불일치
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(
      MethodArgumentTypeMismatchException exception) {
    return respond(
        ErrorCode.VALIDATION_ERROR,
        List.of(
            new FieldError(exception.getName(), typeMismatchMessage(exception.getRequiredType()))));
  }

  // 400 읽을 수 없는 요청 본문
  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
      HttpMessageNotReadableException exception) {
    return respond(ErrorCode.MALFORMED_REQUEST_BODY);
  }

  // 415 지원하지 않는 요청 Content-Type
  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ErrorResponse> handleHttpMediaTypeNotSupportedException(
      HttpMediaTypeNotSupportedException exception) {
    return respond(ErrorCode.REQUEST_TYPE_NOT_SUPPORTED);
  }

  // 413 업로드 크기 상한 초과
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ErrorResponse> handleMaxUploadSizeExceededException(
      MaxUploadSizeExceededException exception) {
    return respond(ErrorCode.UPLOAD_SIZE_EXCEEDED);
  }

  // 406 Accept에 맞는 응답 형식 없음
  @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
  ResponseEntity<ErrorResponse> handleHttpMediaTypeNotAcceptableException(
      HttpMediaTypeNotAcceptableException exception) {
    return respond(ErrorCode.RESPONSE_TYPE_NOT_ACCEPTABLE);
  }

  // 405 허용하지 않는 메서드
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupportedException(
      HttpRequestMethodNotSupportedException exception) {
    return respond(ErrorCode.METHOD_NOT_ALLOWED);
  }

  // 404 없는 경로
  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ErrorResponse> handleNoResourceFoundException(NoResourceFoundException exception) {
    return respond(ErrorCode.ENDPOINT_NOT_FOUND);
  }

  // 503 비동기 처리 시간 초과
  @ExceptionHandler(AsyncRequestTimeoutException.class)
  ResponseEntity<ErrorResponse> handleAsyncRequestTimeoutException(
      AsyncRequestTimeoutException exception) {
    return respond(ErrorCode.REQUEST_TIMED_OUT);
  }

  // 500 나열되지 않은 예외
  @ExceptionHandler(Exception.class)
  ResponseEntity<ErrorResponse> handleException(Exception exception) {
    logger.error("unhandled exception", exception);
    return respond(ErrorCode.INTERNAL_SERVER_ERROR);
  }

  private static ResponseEntity<ErrorResponse> respond(ErrorCode errorCode) {
    return ResponseEntity.status(errorCode.getStatus())
        .contentType(MediaType.APPLICATION_JSON)
        .body(ErrorResponse.from(errorCode));
  }

  private static ResponseEntity<ErrorResponse> respond(
      ErrorCode errorCode, List<FieldError> errors) {
    return ResponseEntity.status(errorCode.getStatus())
        .contentType(MediaType.APPLICATION_JSON)
        .body(ErrorResponse.of(errorCode, errors));
  }

  private static ResponseEntity<ErrorResponse> respondMissingValue(String field) {
    return respond(ErrorCode.VALIDATION_ERROR, List.of(new FieldError(field, REQUIRED_MESSAGE)));
  }

  private static String parameterConditionName(String condition) {
    String name = condition.startsWith("!") ? condition.substring(1) : condition;
    int separatorIndex = name.length();
    for (int index = 0; index < name.length(); index++) {
      char character = name.charAt(index);
      if (character == '=' || character == '!') {
        separatorIndex = index;
        break;
      }
    }
    return name.substring(0, separatorIndex);
  }

  private static String requestFieldName(Object target, String javaField) {
    if (target == null) {
      return javaField;
    }
    try {
      Field field = target.getClass().getDeclaredField(javaField);
      JsonProperty annotation = field.getAnnotation(JsonProperty.class);
      return annotation == null || annotation.value().isEmpty() ? javaField : annotation.value();
    } catch (NoSuchFieldException | SecurityException ignored) {
      return javaField;
    }
  }

  private static String requestParameterName(MethodParameter parameter) {
    RequestParam requestParam = parameter.getParameterAnnotation(RequestParam.class);
    if (requestParam != null) {
      String explicit = requestParam.name().isEmpty() ? requestParam.value() : requestParam.name();
      if (!explicit.isEmpty()) {
        return explicit;
      }
    }
    RequestHeader requestHeader = parameter.getParameterAnnotation(RequestHeader.class);
    if (requestHeader != null) {
      String explicit =
          requestHeader.name().isEmpty() ? requestHeader.value() : requestHeader.name();
      if (!explicit.isEmpty()) {
        return explicit;
      }
    }
    String name = parameter.getParameterName();
    return name == null ? UNRESOLVED_FIELD : name;
  }

  private static String messageOrDefault(String message) {
    return message == null ? INVALID_MESSAGE : message;
  }

  private static String typeMismatchMessage(Class<?> requiredType) {
    return requiredType == null
        ? UNPARSEABLE_MESSAGE
        : TYPE_MISMATCH_MESSAGE.formatted(requiredType.getSimpleName());
  }
}
