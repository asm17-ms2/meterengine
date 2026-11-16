package com.meterengine.payment.controller;

import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.security.AuthenticatedPrincipal;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.EnvironmentReady;
import com.meterengine.global.security.MemberAdminAccess;
import com.meterengine.global.security.ReadAccess;
import com.meterengine.global.validation.StorableText;
import com.meterengine.payment.dto.ListTossPaymentsCredentialsResponse;
import com.meterengine.payment.dto.TossPaymentsCredentialResponse;
import com.meterengine.payment.dto.UpdateTossPaymentsCredentialRequest;
import com.meterengine.payment.service.TossPaymentsCredentialService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@EnvironmentReady
@RequestMapping("/v1/toss-payments-credentials")
public class TossPaymentsCredentialController {

  private final TossPaymentsCredentialService tossPaymentsCredentialService;

  TossPaymentsCredentialController(TossPaymentsCredentialService tossPaymentsCredentialService) {
    this.tossPaymentsCredentialService = tossPaymentsCredentialService;
  }

  @GetMapping
  @ReadAccess
  @Operation(
      summary = "토스페이먼츠 키 목록 조회",
      description =
          """
          현재 업무 환경에 저장한 토스페이먼츠 키를 mode 오름차순으로 돌려줍니다. 키를 저장한 결제 모드만 나옵니다.
          시크릿 키 원문은 돌려주지 않고 마지막 네 자만 secret_key_last_four로 줍니다.
          verified_at과 updated_at은 KST(+09:00)로 나옵니다.
          status가 archived나 all이면 교체되거나 보관된 이전 키를 현재 행 뒤에 교체되거나 보관된 시각 내림차순으로 붙이고, archived_at에 그 시각을 담습니다. 이전 키는 새 결제에 다시 쓰이지 않습니다.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "키 목록입니다. 저장한 키가 없으면 toss_payments_credentials가 빈 배열입니다."),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=validation_error: 요청 값이 올바르지 않습니다.")
  })
  public ListTossPaymentsCredentialsResponse listTossPaymentsCredentials(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(
              description = "보관 상태입니다. 생략하면 active입니다.",
              schema =
                  @Schema(
                      allowableValues = {"active", "archived", "all"},
                      defaultValue = "active"))
          @org.springframework.web.bind.annotation.RequestParam(defaultValue = "active")
          @StorableText
          String status) {
    return tossPaymentsCredentialService.list(organizationId, status);
  }

  @PutMapping("/{mode}")
  @MemberAdminAccess
  @Operation(
      summary = "토스페이먼츠 키 저장",
      description =
          """
          현재 업무 환경에 토스페이먼츠 클라이언트 키와 시크릿 키를 저장합니다.
          테스트 환경과 라이브 환경에서는 경로의 mode가 현재 업무 환경과 같아야 합니다. 그 결제 모드에 키가 있으면 덮어씁니다.
          mode가 test면 test_ck_와 test_sk_로, live면 live_ck_와 live_sk_로 시작하는 키만 받습니다.
          결제위젯 키(test_gck_, test_gsk_ 등)는 받지 않습니다.
          로그인 세션만 부를 수 있고 API 키로는 부를 수 없습니다.
          저장하기 전에 시크릿 키로 토스페이먼츠 결제 조회를 한 번 불러 키가 유효한지 확인합니다.
          시크릿 키는 암호화해 저장하고, 응답에는 마지막 네 자만 싣습니다.
          verified_at과 updated_at은 KST(+09:00)로 나옵니다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "저장한 키"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=validation_error: 경로의 mode가 test나 live가 아니거나, client_key나 secret_key가 비었거나, 문자열에 NUL 문자나 짝이 없는 UTF-16 서로게이트가 있습니다.
            code=invalid_toss_payments_credential: 키가 mode에 맞는 접두사로 시작하지 않거나, 시크릿 키에 콜론, 공백, ASCII 밖 문자가 있거나, 토스페이먼츠가 시크릿 키를 거절했습니다. 어느 필드가 거절됐는지는 errors에 있습니다.
            """),
    @ApiResponse(
        responseCode = "503",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            "code=toss_payments_unavailable: 토스페이먼츠 응답으로 키가 유효한지 확인하지 못했습니다. 키는 저장하지 않았습니다.")
  })
  public TossPaymentsCredentialResponse updateTossPaymentsCredential(
      @CurrentOrganizationId UUID organizationId,
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @Parameter(
              description = "키를 쓸 결제 모드(test 또는 live)",
              schema = @Schema(allowableValues = {"test", "live"}))
          @PathVariable
          @Pattern(regexp = "test|live") @StorableText
          String mode,
      @Valid @RequestBody UpdateTossPaymentsCredentialRequest request) {
    return tossPaymentsCredentialService.update(organizationId, mode, request, principal.mode());
  }

  @org.springframework.web.bind.annotation.DeleteMapping("/{mode}")
  @MemberAdminAccess
  @org.springframework.web.bind.annotation.ResponseStatus(
      org.springframework.http.HttpStatus.NO_CONTENT)
  @Operation(
      summary = "토스페이먼츠 키 보관",
      description =
          "경로의 결제 모드에 저장한 토스페이먼츠 키를 보관하고 새 결제와 카드 등록을 멈춥니다."
              + " 이미 요청한 결제의 결과 확인과 결제 취소에는 보관한 키를 계속 씁니다. 새 결제에 다시 쓰려면 키를 다시 저장합니다."
              + " 로그인 세션만 부를 수 있고 API 키로는 부를 수 없습니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "보관했습니다. 본문 없음"),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=toss_payments_credential_not_found: 경로의 mode에 저장한 키가 없습니다.")
  })
  public void deleteTossPaymentsCredential(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = "보관할 키의 결제 모드(test 또는 live)")
          @PathVariable
          @Pattern(regexp = "test|live") @StorableText
          String mode) {
    tossPaymentsCredentialService.delete(organizationId, mode);
  }
}
