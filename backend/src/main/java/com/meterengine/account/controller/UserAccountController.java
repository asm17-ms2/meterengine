package com.meterengine.account.controller;

import com.meterengine.account.dto.ListUserAccountsResponse;
import com.meterengine.account.dto.UpdateUserAccountRoleRequest;
import com.meterengine.account.dto.UserAccountResponse;
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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/user-accounts")
public class UserAccountController {

  private final UserAccountService userAccountService;

  UserAccountController(UserAccountService userAccountService) {
    this.userAccountService = userAccountService;
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
