package com.meterengine.payment.controller;

import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.security.AdminAccess;
import com.meterengine.global.security.CurrentOrganizationId;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
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
          저장한 토스페이먼츠 키를 mode 오름차순으로 돌려준다. 저장한 모드만 나온다.
          시크릿 키 원문은 돌려주지 않고 마지막 네 자만 secret_key_last_four로 준다.
          verified_at과 updated_at은 KST(+09:00)로 나온다.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "키 목록. 저장한 키가 없으면 toss_payments_credentials가 빈 배열이다"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=validation_error: 요청 값이 올바르지 않다")
  })
  public ListTossPaymentsCredentialsResponse listTossPaymentsCredentials(
      @CurrentOrganizationId UUID organizationId,
      @org.springframework.web.bind.annotation.RequestParam(defaultValue = "active") @StorableText
          String status) {
    return tossPaymentsCredentialService.list(organizationId, status);
  }

  @PutMapping("/{mode}")
  @AdminAccess
  @Operation(
      summary = "토스페이먼츠 키 저장",
      description =
          """
          경로의 mode에 쓸 토스페이먼츠 클라이언트 키와 시크릿 키를 저장한다. 그 모드에 키가 있으면 덮어쓴다.
          test 모드는 test_ck_와 test_sk_로, live 모드는 live_ck_와 live_sk_로 시작하는 키만 받는다.
          결제위젯 키(test_gck_, test_gsk_ 등)는 받지 않는다.
          저장 전에 시크릿 키로 토스페이먼츠 결제 조회를 한 번 불러 키가 유효한지 확인한다.
          시크릿 키는 암호화해 저장하고, 응답에는 마지막 네 자만 싣는다.
          verified_at과 updated_at은 KST(+09:00)로 나온다.
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
            code=validation_error: 경로의 mode가 test나 live가 아니거나, client_key나 secret_key가 비었거나, 문자열에 NUL 문자나 짝이 없는 UTF-16 서로게이트가 있거나.
            code=invalid_toss_payments_credential: 키가 mode에 맞는 접두사로 시작하지 않거나, 토스페이먼츠가 시크릿 키를 거절했다. 어느 필드가 거절됐는지는 errors에 있다.
            """),
    @ApiResponse(
        responseCode = "503",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=toss_payments_unavailable: 토스페이먼츠 응답으로 키가 유효한지 확인하지 못했다. 키는 저장하지 않았다")
  })
  public TossPaymentsCredentialResponse updateTossPaymentsCredential(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(
              description = "키를 쓸 결제 모드. test 또는 live.",
              schema = @Schema(allowableValues = {"test", "live"}))
          @PathVariable
          @Pattern(regexp = "test|live") @StorableText
          String mode,
      @Valid @RequestBody UpdateTossPaymentsCredentialRequest request) {
    return tossPaymentsCredentialService.update(organizationId, mode, request);
  }

  @org.springframework.web.bind.annotation.DeleteMapping("/{mode}")
  @AdminAccess
  @org.springframework.web.bind.annotation.ResponseStatus(
      org.springframework.http.HttpStatus.NO_CONTENT)
  @Operation(
      summary = "토스페이먼츠 키 보관",
      description = "새 결제와 카드 등록을 중단한다. 진행 중 결제 확인과 취소에는 보존한 키를 사용한다. 다시 사용하려면 키를 재등록한다.")
  public void deleteTossPaymentsCredential(
      @CurrentOrganizationId UUID organizationId,
      @PathVariable @Pattern(regexp = "test|live") @StorableText String mode) {
    tossPaymentsCredentialService.delete(organizationId, mode);
  }
}
