package com.meterengine.global.error;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.meterengine.global.error.ErrorResponse.FieldError;
import java.lang.reflect.Field;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
class GlobalExceptionHandler {

  private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private static final String UNRESOLVED_FIELD = "unknown";

  private final MessageSource messageSource;

  GlobalExceptionHandler(MessageSource messageSource) {
    this.messageSource = messageSource;
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
      MethodArgumentNotValidException exception) {
    Object target = exception.getBindingResult().getTarget();
    List<FieldError> errors =
        exception.getBindingResult().getFieldErrors().stream()
            .map(
                error ->
                    new FieldError(
                        wireName(target, error.getField()), messageOr(error.getDefaultMessage())))
            .toList();
    return respond(ErrorCode.VALIDATION_ERROR, errors);
  }

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
                                    parameterWireName(result.getMethodParameter()),
                                    messageOr(error.getDefaultMessage()))))
            .toList();
    return respond(ErrorCode.VALIDATION_ERROR, errors);
  }

  @ExceptionHandler(MissingRequestHeaderException.class)
  ResponseEntity<ErrorResponse> handleMissingRequestHeaderException(
      MissingRequestHeaderException exception) {
    return respond(
        ErrorCode.VALIDATION_ERROR,
        List.of(new FieldError(exception.getHeaderName(), message("problem.field.required"))));
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatchException(
      MethodArgumentTypeMismatchException exception) {
    return respond(
        ErrorCode.VALIDATION_ERROR,
        List.of(new FieldError(exception.getName(), cannotBeParsed(exception.getRequiredType()))));
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
      HttpMessageNotReadableException exception) {
    return respond(ErrorCode.MALFORMED_REQUEST_BODY);
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  ResponseEntity<ErrorResponse> handleHttpMediaTypeNotSupportedException(
      HttpMediaTypeNotSupportedException exception) {
    return respond(ErrorCode.REQUEST_TYPE_NOT_SUPPORTED);
  }

  @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
  ResponseEntity<ErrorResponse> handleHttpMediaTypeNotAcceptableException(
      HttpMediaTypeNotAcceptableException exception) {
    return respond(ErrorCode.RESPONSE_TYPE_NOT_ACCEPTABLE);
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupportedException(
      HttpRequestMethodNotSupportedException exception) {
    return respond(ErrorCode.METHOD_NOT_ALLOWED);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ErrorResponse> handleNoResourceFoundException(NoResourceFoundException exception) {
    return respond(ErrorCode.ENDPOINT_NOT_FOUND);
  }

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

  private static String wireName(Object target, String javaField) {
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

  private static String parameterWireName(MethodParameter parameter) {
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

  private String messageOr(String message) {
    return message == null ? message("problem.field.invalid") : message;
  }

  private String cannotBeParsed(Class<?> requiredType) {
    return requiredType == null
        ? message("problem.field.unparseable")
        : message("problem.field.type-mismatch", requiredType.getSimpleName());
  }

  private String message(String code, Object... arguments) {
    return messageSource.getMessage(code, arguments, code, LocaleContextHolder.getLocale());
  }
}
