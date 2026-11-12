package com.meterengine.account.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.meterengine.account.dto.CreateSignupRequest;
import com.meterengine.account.entity.UserAccount;
import com.meterengine.account.service.AuthRequestRateLimitService;
import com.meterengine.account.service.CaptchaService;
import com.meterengine.account.service.Signup;
import com.meterengine.account.service.SignupService;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.InvalidRequestException;
import com.meterengine.global.error.TooManyRequestsException;
import com.meterengine.organization.entity.Organization;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class SignupControllerRateLimitTest {
  private final SignupService service = mock(SignupService.class);
  private final AuthRequestRateLimitService limit = mock(AuthRequestRateLimitService.class);
  private final CaptchaService captcha = mock(CaptchaService.class);
  private final MockHttpServletRequest servletRequest = new MockHttpServletRequest();
  private final SignupController controller = new SignupController(service, limit, captcha);
  private final CreateSignupRequest request =
      new CreateSignupRequest("도입사", "사용자", "user@example.com", "password", List.of(), "token");

  @Test
  void 제한된_가입은_캡차와_계정_조회와_비밀번호_해시_서비스를_호출하지_않는다() {
    doThrow(new TooManyRequestsException(ErrorCode.RATE_LIMIT_EXCEEDED, 10))
        .when(limit)
        .signup("user@example.com", servletRequest);
    assertThatThrownBy(() -> controller.createSignup(request, servletRequest))
        .isInstanceOf(TooManyRequestsException.class);
    verifyNoInteractions(captcha, service);
  }

  @Test
  void 캡차를_통과하지_못한_가입은_가입_서비스를_호출하지_않는다() {
    doThrow(new InvalidRequestException(ErrorCode.CAPTCHA_FAILED))
        .when(captcha)
        .verify("token", CaptchaService.SIGNUP_ACTION, servletRequest);
    assertThatThrownBy(() -> controller.createSignup(request, servletRequest))
        .isInstanceOf(InvalidRequestException.class);
    verifyNoInteractions(service);
  }

  @Test
  void 가입은_요청_수_제한_캡차_가입_순서로_처리한다() {
    when(service.create("도입사", "사용자", "user@example.com", "password", List.of()))
        .thenReturn(
            new Signup(
                new Organization(UUID.randomUUID(), "도입사", Organization.PENDING),
                new UserAccount(
                    UUID.randomUUID(),
                    "user@example.com",
                    "hash",
                    "사용자",
                    UserAccount.MEMBER,
                    OffsetDateTime.now())));
    controller.createSignup(request, servletRequest);
    var order = inOrder(limit, captcha, service);
    order.verify(limit).signup("user@example.com", servletRequest);
    order.verify(captcha).verify("token", CaptchaService.SIGNUP_ACTION, servletRequest);
    order.verify(service).create("도입사", "사용자", "user@example.com", "password", List.of());
  }
}
