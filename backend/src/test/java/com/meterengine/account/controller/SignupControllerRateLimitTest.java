package com.meterengine.account.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.meterengine.account.dto.CreateSignupRequest;
import com.meterengine.account.service.AuthRequestRateLimitService;
import com.meterengine.account.service.SignupService;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.TooManyRequestsException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class SignupControllerRateLimitTest {
  @Test
  void 제한된_가입은_계정_조회와_비밀번호_해시_서비스를_호출하지_않는다() {
    var service = mock(SignupService.class);
    var limit = mock(AuthRequestRateLimitService.class);
    var servletRequest = new MockHttpServletRequest();
    doThrow(new TooManyRequestsException(ErrorCode.RATE_LIMIT_EXCEEDED, 10))
        .when(limit)
        .signup("user@example.com", servletRequest);
    var controller = new SignupController(service, limit);
    var request = new CreateSignupRequest("도입사", "사용자", "user@example.com", "password", List.of());
    assertThatThrownBy(() -> controller.createSignup(request, servletRequest))
        .isInstanceOf(TooManyRequestsException.class);
    verifyNoInteractions(service);
  }
}
