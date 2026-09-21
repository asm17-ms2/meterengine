package com.meterengine.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.ConversionNotSupportedException;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.validation.method.MethodValidationException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

class GlobalExceptionHandlerCoverageTest {

  private static final Set<Class<?>> EXCEPTIONS_LEFT_TO_INTERNAL_SERVER_ERROR =
      Set.of(
          ConversionNotSupportedException.class,
          HttpMessageNotWritableException.class,
          MethodValidationException.class,
          MissingPathVariableException.class,
          AsyncRequestNotUsableException.class,
          ErrorResponseException.class,
          NoHandlerFoundException.class,
          ServletRequestBindingException.class,
          TypeMismatchException.class);

  @Test
  void 나열하지_않은_프레임워크_예외는_500으로_두기로_한_것과_같다() {
    Set<Class<?>> unlistedExceptions = exceptionsHandledBy(ResponseEntityExceptionHandler.class);
    unlistedExceptions.removeAll(exceptionsHandledBy(GlobalExceptionHandler.class));

    assertThat(unlistedExceptions).isEqualTo(EXCEPTIONS_LEFT_TO_INTERNAL_SERVER_ERROR);
  }

  @Test
  void 일부러_500으로_두는_것은_모두_Spring이_받는_예외다() {
    assertThat(exceptionsHandledBy(ResponseEntityExceptionHandler.class))
        .containsAll(EXCEPTIONS_LEFT_TO_INTERNAL_SERVER_ERROR);
  }

  private static Set<Class<?>> exceptionsHandledBy(Class<?> handler) {
    return Arrays.stream(handler.getDeclaredMethods())
        .map(method -> method.getAnnotation(ExceptionHandler.class))
        .filter(Objects::nonNull)
        .flatMap(annotation -> Arrays.stream(annotation.value()))
        .collect(Collectors.toCollection(LinkedHashSet::new));
  }
}
