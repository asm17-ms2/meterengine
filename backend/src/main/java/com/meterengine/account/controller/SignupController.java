package com.meterengine.account.controller;

import com.meterengine.account.dto.CreateSignupRequest;
import com.meterengine.account.dto.SignupResponse;
import com.meterengine.account.service.SignupConsent;
import com.meterengine.account.service.SignupService;
import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.security.PublicAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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

  SignupController(SignupService signupService) {
    this.signupService = signupService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PublicAccess
  @Operation(
      summary = "가입 신청",
      description =
          """
          도입사와 그 도입사에 소속된 member 사용자를 만들고 필수 동의를 기록한다.
          도입사는 pending으로 만들어지고 운영자가 승인해 active가 될 때까지 로그인하면 403(organization_not_active)이다.
          이메일은 소문자로 바꿔 저장한다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "만든 도입사와 사용자"),
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
            document_version이 비었거나, 문자열에 NUL 문자나 짝이 없는 UTF-16 서로게이트를 담았다.
            """),
    @ApiResponse(
        responseCode = "401",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=invalid_credentials: Authorization 헤더를 실었는데 그 값이 올바르지 않다"),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=email_already_exists: 대소문자를 가리지 않고 같은 이메일로 가입한 사용자가 있다")
  })
  public SignupResponse createSignup(@Valid @RequestBody CreateSignupRequest request) {
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
