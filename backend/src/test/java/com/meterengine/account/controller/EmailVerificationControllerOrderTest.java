package com.meterengine.account.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.meterengine.account.dto.CreateEmailVerificationRequest;
import com.meterengine.account.service.AuthRequestRateLimitService;
import com.meterengine.account.service.CaptchaService;
import com.meterengine.account.service.EmailVerificationService;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.InvalidRequestException;
import com.meterengine.global.error.TooManyRequestsException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class EmailVerificationControllerOrderTest {
  private final EmailVerificationService service = mock(EmailVerificationService.class);
  private final AuthRequestRateLimitService limit = mock(AuthRequestRateLimitService.class);
  private final CaptchaService captcha = mock(CaptchaService.class);
  private final MockHttpServletRequest servletRequest = new MockHttpServletRequest();
  private final EmailVerificationController controller =
      new EmailVerificationController(service, limit, captcha);
  private final CreateEmailVerificationRequest request =
      new CreateEmailVerificationRequest("user@example.com", "token");

  @Test
  void 다시_보내기는_요청_수_제한_캡차_발송_순서로_처리한다() {
    controller.createEmailVerification(request, servletRequest);
    var order = inOrder(limit, captcha, service);
    order.verify(limit).resend("user@example.com", servletRequest);
    order.verify(captcha).verify("token", CaptchaService.EMAIL_VERIFICATION_ACTION, servletRequest);
    order.verify(service).create("user@example.com");
  }

  @Test
  void 제한된_다시_보내기는_캡차와_발송을_부르지_않는다() {
    doThrow(new TooManyRequestsException(ErrorCode.RATE_LIMIT_EXCEEDED, 10))
        .when(limit)
        .resend("user@example.com", servletRequest);
    assertThatThrownBy(() -> controller.createEmailVerification(request, servletRequest))
        .isInstanceOf(TooManyRequestsException.class);
    verifyNoInteractions(captcha, service);
  }

  @Test
  void 캡차를_통과하지_못한_다시_보내기는_발송을_부르지_않는다() {
    doThrow(new InvalidRequestException(ErrorCode.CAPTCHA_FAILED))
        .when(captcha)
        .verify("token", CaptchaService.EMAIL_VERIFICATION_ACTION, servletRequest);
    assertThatThrownBy(() -> controller.createEmailVerification(request, servletRequest))
        .isInstanceOf(InvalidRequestException.class);
    verifyNoInteractions(service);
  }
}
