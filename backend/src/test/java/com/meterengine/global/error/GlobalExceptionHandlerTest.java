package com.meterengine.global.error;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;

class GlobalExceptionHandlerTest {

  private final List<ErrorCode> notifiedErrorCodes = new ArrayList<>();
  private final GlobalExceptionHandler globalExceptionHandler =
      new GlobalExceptionHandler(
          List.of((request, errorCode) -> notifiedErrorCodes.add(errorCode)));
  private final MockHttpServletRequest request = new MockHttpServletRequest("POST", "/v1/events");

  @Test
  void 본문_검증_실패를_validation_error로_알린다() throws NoSuchMethodException {
    MethodArgumentNotValidException exception =
        new MethodArgumentNotValidException(
            new MethodParameter(Object.class.getMethod("equals", Object.class), 0),
            new BeanPropertyBindingResult(new Object(), "request"));

    globalExceptionHandler.handleMethodArgumentNotValidException(exception, request);

    assertThat(notifiedErrorCodes).containsExactly(ErrorCode.VALIDATION_ERROR);
  }

  @Test
  void 읽을_수_없는_본문을_malformed_request_body로_알린다() {
    HttpMessageNotReadableException exception =
        new HttpMessageNotReadableException("깨진 본문", new MockHttpInputMessage(new byte[0]));

    globalExceptionHandler.handleHttpMessageNotReadableException(exception, request);

    assertThat(notifiedErrorCodes).containsExactly(ErrorCode.MALFORMED_REQUEST_BODY);
  }

  @Test
  void 알림을_받는_쪽이_실패해도_400_오류_응답을_낸다() {
    GlobalExceptionHandler handlerWithFailingListener =
        new GlobalExceptionHandler(
            List.of(
                (rejectedRequest, errorCode) -> {
                  throw new RuntimeException("지표 기록 실패");
                }));
    HttpMessageNotReadableException exception =
        new HttpMessageNotReadableException("깨진 본문", new MockHttpInputMessage(new byte[0]));

    ResponseEntity<ErrorResponse> response =
        handlerWithFailingListener.handleHttpMessageNotReadableException(exception, request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isEqualTo(ErrorResponse.from(ErrorCode.MALFORMED_REQUEST_BODY));
  }
}
