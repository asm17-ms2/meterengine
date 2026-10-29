package com.meterengine.account.controller;

import com.meterengine.account.dto.CreateUserAccountRequest;
import com.meterengine.account.dto.ListUserAccountsResponse;
import com.meterengine.account.dto.UpdateUserAccountRoleRequest;
import com.meterengine.account.dto.UserAccountResponse;
import com.meterengine.account.service.OperatorAccountService;
import com.meterengine.account.service.UserAccountService;
import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.security.AuthenticatedPrincipal;
import com.meterengine.global.security.OperatorAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/user-accounts")
public class UserAccountController {

  private final UserAccountService userAccountService;
  private final OperatorAccountService operatorAccountService;

  UserAccountController(
      UserAccountService userAccountService, OperatorAccountService operatorAccountService) {
    this.userAccountService = userAccountService;
    this.operatorAccountService = operatorAccountService;
  }

  @PostMapping
  @OperatorAccess
  @ResponseStatus(HttpStatus.CREATED)
  @Operation(
      summary = "플랫폼 운영자 생성",
      description =
          """
          플랫폼 운영자 세션으로 새 운영자 계정을 만든다. 역할은 서버가 operator로 정한다.
          도입사 소속과 대리 동의는 만들지 않는다. 새 계정은 본인이 로그인해 필수 안내에 동의해야 운영할 수 있다.
          이미 가입한 이메일이면 계정과 비밀번호를 바꾸지 않고 409를 돌려준다. 기존 계정의 승격은 역할 변경 경로를 사용한다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "소속 도입사가 없는 새 플랫폼 운영자"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=validation_error: 이메일, 이름이나 초기 비밀번호가 형식 또는 길이 규칙을 벗어났다"),
    @ApiResponse(
        responseCode = "403",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            "code=permission_denied: 플랫폼 운영자 세션이 아니다. code=consent_required: 본인 안내 동의가 필요하다"),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=email_already_exists: 이미 가입한 이메일이다")
  })
  public UserAccountResponse createUserAccount(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @Valid @RequestBody CreateUserAccountRequest request) {
    return UserAccountResponse.from(operatorAccountService.create(principal, request));
  }

  @GetMapping
  @OperatorAccess
  @Operation(
      summary = "사용자 목록 조회",
      description =
          """
          모든 사용자를 만든 순서로 돌려준다. 사용자마다 소속된 도입사를 소속된 순서로 담는다.
          operator 로그인 세션만 부를 수 있다. 페이지를 나누지 않는다.
          """)
  @ApiResponses({@ApiResponse(responseCode = "200", description = "사용자 목록")})
  public ListUserAccountsResponse listUserAccounts() {
    return ListUserAccountsResponse.from(userAccountService.list());
  }

  @PutMapping("/{id}/role")
  @OperatorAccess
  @Operation(
      summary = "사용자 역할 변경",
      description =
          """
          사용자의 역할을 바꾸고 바뀐 사용자를 돌려준다. 바뀐 역할은 그 사용자의 다음 요청부터 적용된다.
          operator 로그인 세션만 부를 수 있다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "바뀐 사용자"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=validation_error: role이 member나 operator가 아니거나, 경로의 UUID 형식이 틀렸다"),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=user_account_not_found: 그런 사용자가 없다")
  })
  public UserAccountResponse updateUserAccountRole(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @Parameter(description = "역할을 바꿀 사용자의 ID.") @PathVariable UUID id,
      @Valid @RequestBody UpdateUserAccountRoleRequest request) {
    return UserAccountResponse.from(userAccountService.update(principal, id, request.role()));
  }
}
