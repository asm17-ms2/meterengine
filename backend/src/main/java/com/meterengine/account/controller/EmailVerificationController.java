package com.meterengine.account.controller;

import com.meterengine.account.dto.ConfirmEmailVerificationRequest;
import com.meterengine.account.dto.CreateEmailVerificationRequest;
import com.meterengine.account.service.AuthRequestRateLimitService;
import com.meterengine.account.service.CaptchaService;
import com.meterengine.account.service.EmailVerificationService;
import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.security.PublicAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EmailVerificationController {

  private final EmailVerificationService emailVerificationService;
  private final AuthRequestRateLimitService authRequestRateLimitService;
  private final CaptchaService captchaService;

  EmailVerificationController(
      EmailVerificationService emailVerificationService,
      AuthRequestRateLimitService authRequestRateLimitService,
      CaptchaService captchaService) {
    this.emailVerificationService = emailVerificationService;
    this.authRequestRateLimitService = authRequestRateLimitService;
    this.captchaService = captchaService;
  }

  @PostMapping("/v1/email-verifications")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @PublicAccess
  @Operation(
      summary = "인증 메일 다시 보내기",
      description =
          """
          가입한 계정이 있고 아직 인증하지 않았으면 새 인증 메일을 보낸다. 앞서 보낸 링크도 만료 전까지 쓸 수 있다.
          계정이 없거나 이미 인증했거나 서버가 이메일 인증을 껐으면 아무것도 보내지 않는다. 어느 경우든 같은 202를 준다.
          서버가 캡차를 켰으면 요청 수 제한을 본 뒤 turnstile_token을 확인하고 나서 처리한다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "202", description = "접수했다. 보냈는지는 알려 주지 않는다"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=validation_error: email이 비었거나 이메일 형식이 아니거나 254자를 넘거나, turnstile_token이 2048자를 넘는다.
            code=captcha_failed: 서버가 캡차를 켰는데 turnstile_token이 없거나 확인을 통과하지 못했다. 위젯을 다시 풀고 보낸다.
            """),
    @ApiResponse(
        responseCode = "429",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=rate_limit_exceeded: 다시 보내기 한도를 넘었다. Retry-After 헤더의 초만큼 기다린 뒤 재시도한다",
        headers =
            @Header(
                name = "Retry-After",
                description = "다시 보내기 전에 기다릴 초",
                required = true,
                schema = @Schema(type = "integer"))),
    @ApiResponse(
        responseCode = "503",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=captcha_unavailable: 캡차 확인 결과를 받지 못해 요청을 받지 않았다. 잠시 뒤 다시 시도한다")
  })
  public void createEmailVerification(
      @Valid @RequestBody CreateEmailVerificationRequest request,
      HttpServletRequest servletRequest) {
    authRequestRateLimitService.resend(request.email(), servletRequest);
    captchaService.verify(
        request.turnstileToken(), CaptchaService.EMAIL_VERIFICATION_ACTION, servletRequest);
    emailVerificationService.create(request.email());
  }

  @PostMapping("/v1/email-verification-confirmations")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PublicAccess
  @Operation(
      summary = "이메일 인증",
      description =
          """
          인증 메일 링크의 토큰으로 그 계정의 이메일을 인증한다. 토큰은 만료 전에 한 번만 쓸 수 있다.
          인증한 뒤에도 조직이 승인되기 전에는 로그인하면 403(organization_not_active)이다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "인증했다"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=validation_error: token이 비었거나 64자를 넘는다"),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            "code=email_verification_not_found: 토큰이 없거나 만료됐거나 이미 썼다. 어느 쪽인지는 알려 주지 않는다. 인증 메일을 다시 받는다")
  })
  public void confirmEmailVerification(
      @Valid @RequestBody ConfirmEmailVerificationRequest request) {
    emailVerificationService.confirm(request.token());
  }
}
