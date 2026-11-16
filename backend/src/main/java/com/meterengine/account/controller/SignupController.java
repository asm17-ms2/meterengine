package com.meterengine.account.controller;

import com.meterengine.account.dto.CreateSignupRequest;
import com.meterengine.account.dto.SignupResponse;
import com.meterengine.account.service.AuthRequestRateLimitService;
import com.meterengine.account.service.CaptchaService;
import com.meterengine.account.service.SignupConsent;
import com.meterengine.account.service.SignupService;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/signups")
public class SignupController {

  private final SignupService signupService;
  private final AuthRequestRateLimitService authRequestRateLimitService;
  private final CaptchaService captchaService;

  SignupController(
      SignupService signupService,
      AuthRequestRateLimitService authRequestRateLimitService,
      CaptchaService captchaService) {
    this.signupService = signupService;
    this.authRequestRateLimitService = authRequestRateLimitService;
    this.captchaService = captchaService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PublicAccess
  @Operation(
      summary = "가입",
      description =
          """
          조직과 그 조직의 대표관리자 계정을 만들고 이용약관 동의와 개인정보 처리방침 확인을 기록합니다.
          조직은 active로 만들어져 바로 로그인해 테스트 환경을 쓸 수 있습니다.
          라이브 환경은 잠겨 있고(live_environment_opened_at이 null) 라이브 환경 이용 신청이 승인되면 열립니다.
          잠긴 동안 라이브 환경이나 X-MeterEngine-Environment가 없는 요청은 403(live_environment_locked)입니다.
          이메일은 소문자로 바꿔 저장합니다.
          응답의 user_account.email_verified는 false이고 가입을 마친 뒤 인증 메일을 보냅니다.
          메일을 보내지 못해도 가입은 201이며, POST /v1/email-verifications로 다시 받습니다.
          인증 전에 로그인하면 403(email_not_verified)입니다.
          요청 수 한도를 확인한 뒤 turnstile_token을 Cloudflare Turnstile로 확인하고 나서 가입을 처리합니다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "만든 조직과 계정"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=validation_error: organization_name이나 name이 비었거나 255자를 넘거나, email이 이메일 형식이 아니거나 254자를 넘거나,
            password가 8자보다 짧거나 72자나 UTF-8 72바이트를 넘거나, consents에 terms_of_service와 privacy_policy가 한 번씩 있지 않거나,
            document_version이 비었거나, 문자열에 NUL 문자나 짝이 없는 UTF-16 서로게이트를 담았습니다.
            code=captcha_failed: turnstile_token이 없거나 확인을 통과하지 못했습니다. 위젯을 다시 풀고 보냅니다.
            """),
    @ApiResponse(
        responseCode = "401",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=invalid_credentials: Authorization 헤더를 실었는데 그 값이 올바르지 않습니다."),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=email_already_exists: 대소문자를 가리지 않고 같은 이메일로 가입한 사용자가 있습니다."),
    @ApiResponse(
        responseCode = "429",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            "code=rate_limit_exceeded: 가입 시도 한도를 넘었습니다. Retry-After 헤더의 초만큼 기다린 뒤 다시 보냅니다.",
        headers =
            @Header(
                name = "Retry-After",
                description = "다시 보내기 전에 기다릴 시간(초)",
                required = true,
                schema = @Schema(type = "integer"))),
    @ApiResponse(
        responseCode = "503",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=captcha_unavailable: 캡차 확인 결과를 받지 못해 가입을 받지 않았습니다. 잠시 뒤 다시 시도합니다.")
  })
  public SignupResponse createSignup(
      @Valid @RequestBody CreateSignupRequest request, HttpServletRequest servletRequest) {
    authRequestRateLimitService.signup(request.email(), servletRequest);
    captchaService.verify(request.turnstileToken(), CaptchaService.SIGNUP_ACTION, servletRequest);
    return SignupResponse.from(
        signupService.create(
            request.organizationName(),
            request.name(),
            request.email(),
            request.password(),
            request.consents().stream()
                .map(consent -> new SignupConsent(consent.document(), consent.documentVersion()))
                .toList()));
  }
}
