package com.meterengine.account.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.meterengine.account.dto.CreateSessionRequest;
import com.meterengine.account.service.AuthRequestRateLimitService;
import com.meterengine.account.service.SessionService;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.TooManyRequestsException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class SessionControllerRateLimitTest {
  @Test
  void 제한된_로그인은_비밀번호_검증_서비스를_호출하지_않는다() {
    var service = mock(SessionService.class);
    var limit = mock(AuthRequestRateLimitService.class);
    var servletRequest = new MockHttpServletRequest();
    doThrow(new TooManyRequestsException(ErrorCode.RATE_LIMIT_EXCEEDED, 10))
        .when(limit)
        .login("user@example.com", servletRequest);
    var controller = new SessionController(service, limit);
    assertThatThrownBy(
            () ->
                controller.createSession(
                    new CreateSessionRequest("user@example.com", "password"), servletRequest))
        .isInstanceOf(TooManyRequestsException.class);
    verifyNoInteractions(service);
  }
}
