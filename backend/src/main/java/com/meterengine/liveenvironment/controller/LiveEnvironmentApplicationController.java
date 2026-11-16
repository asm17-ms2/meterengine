package com.meterengine.liveenvironment.controller;

import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.security.AuthenticatedPrincipal;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.EnvironmentReady;
import com.meterengine.global.security.MemberAdminAccess;
import com.meterengine.global.security.MemberReadAccess;
import com.meterengine.global.security.OperatorAccess;
import com.meterengine.global.validation.StorableText;
import com.meterengine.liveenvironment.dto.CreateLiveEnvironmentApplicationRequest;
import com.meterengine.liveenvironment.dto.ListLiveEnvironmentApplicationsResponse;
import com.meterengine.liveenvironment.dto.LiveEnvironmentApplicationResponse;
import com.meterengine.liveenvironment.dto.UpdateLiveEnvironmentApplicationStatusRequest;
import com.meterengine.liveenvironment.entity.LiveEnvironmentApplication;
import com.meterengine.liveenvironment.service.LiveEnvironmentApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@EnvironmentReady
public class LiveEnvironmentApplicationController {

  private final LiveEnvironmentApplicationService liveEnvironmentApplicationService;

  LiveEnvironmentApplicationController(
      LiveEnvironmentApplicationService liveEnvironmentApplicationService) {
    this.liveEnvironmentApplicationService = liveEnvironmentApplicationService;
  }

  @PostMapping("/v1/organization/live-environment-applications")
  @ResponseStatus(HttpStatus.CREATED)
  @MemberAdminAccess
  @Operation(
      summary = "라이브 환경 이용 신청",
      description =
          """
          조직의 라이브 환경을 열어 달라고 신청합니다. MeterEngine이 승인하면 라이브 환경이 열립니다.
          라이브 환경이 잠긴 조직이 부르므로 X-MeterEngine-Environment를 test로 보냅니다. 로그인 세션만 부를 수 있습니다.
          신청할 때 바로 확인하는 것은 사업자등록번호의 검증 숫자, 국세청 사업자 상태, 토스페이먼츠 라이브 키입니다.
          등록되지 않은 번호와 폐업 사업자는 거절합니다. 휴업 사업자는 받고 business_status를 suspended로 남깁니다.
          국세청 조회 결과를 받지 못하면 신청은 받고 business_status를 unchecked로 남깁니다.
          토스페이먼츠 키는 확인만 하고 신청에 보관하며, 승인되면 라이브 환경의 토스페이먼츠 키로 저장합니다. 승인 전에는 쓰이지 않습니다.
          created_at은 KST(+09:00)로 나옵니다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "받은 신청입니다. status는 pending입니다."),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=validation_error: 필수 값이 비었거나, 길이를 넘었거나, 문자열에 NUL 문자나 짝이 없는 UTF-16 서로게이트가 있습니다.
            code=invalid_business_registration_number: 사업자등록번호가 열 자리 숫자가 아니거나 검증 숫자가 틀렸거나, 국세청에 등록되지 않았거나, 폐업한 사업자입니다. 이유는 errors의 business_registration_number에 있습니다.
            code=invalid_toss_payments_credential: 키가 live_ck_, live_sk_로 시작하지 않거나 토스페이먼츠가 시크릿 키를 거절했습니다. 어느 필드인지는 errors의 toss_payments_client_key, toss_payments_secret_key에 있습니다.
            """),
    @ApiResponse(
        responseCode = "403",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=live_environment_locked: X-MeterEngine-Environment를 test로 보내지 않았습니다."),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=live_environment_already_open: 이미 라이브 환경이 열린 조직입니다.
            code=live_environment_application_in_progress: 검토 중인 신청이 있습니다.
            """),
    @ApiResponse(
        responseCode = "429",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            """
            code=rate_limit_exceeded: 조직의 신청 시도가 30분에 10번을 넘었습니다. 국세청과 토스페이먼츠 확인에서 거절된 시도도 셉니다.
            Retry-After 헤더의 초만큼 기다린 뒤 다시 보냅니다.
            """,
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
        description =
            "code=toss_payments_unavailable: 토스페이먼츠 응답으로 키가 유효한지 확인하지 못했습니다. 신청은 받지 않았습니다.")
  })
  public LiveEnvironmentApplicationResponse createLiveEnvironmentApplication(
      @CurrentOrganizationId UUID organizationId,
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @Valid @RequestBody CreateLiveEnvironmentApplicationRequest request) {
    return liveEnvironmentApplicationService.create(organizationId, principal, request);
  }

  @GetMapping("/v1/organization/live-environment-applications")
  @MemberReadAccess
  @Operation(
      summary = "조직의 라이브 환경 이용 신청 목록 조회",
      description =
          """
          조직이 낸 신청을 최근 것부터 돌려줍니다. 거절된 신청의 사유는 rejection_reason에 있습니다.
          로그인 세션만 부를 수 있습니다. 페이지를 나누지 않습니다.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "신청 목록입니다. 없으면 live_environment_applications가 빈 배열입니다.")
  })
  public ListLiveEnvironmentApplicationsResponse listOrganizationLiveEnvironmentApplications(
      @CurrentOrganizationId UUID organizationId) {
    return liveEnvironmentApplicationService.list(organizationId);
  }

  @GetMapping("/v1/live-environment-applications")
  @OperatorAccess
  @Operation(
      summary = "라이브 환경 이용 신청 목록 조회",
      description =
          """
          모든 조직의 신청을 최근 것부터 돌려줍니다. status를 주면 그 상태의 신청만 돌려줍니다.
          승인한 신청은 승인 뒤 첫 실결제 응답의 상점아이디를 observed_toss_payments_merchant_id에 담습니다. 신청한 값과 다르면 운영자가 확인합니다.
          MeterEngine 운영자 로그인 세션만 부를 수 있습니다. 페이지를 나누지 않습니다.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "신청 목록입니다. 없으면 live_environment_applications가 빈 배열입니다."),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=validation_error: status가 pending, approved, rejected가 아닙니다.")
  })
  public ListLiveEnvironmentApplicationsResponse listLiveEnvironmentApplications(
      @Parameter(
              description = "상태로 좁힙니다. 생략하면 모든 상태입니다.",
              schema =
                  @Schema(
                      allowableValues = {
                        LiveEnvironmentApplication.PENDING,
                        LiveEnvironmentApplication.APPROVED,
                        LiveEnvironmentApplication.REJECTED
                      }))
          @RequestParam(required = false)
          @Pattern(regexp = LiveEnvironmentApplication.STATUS_PATTERN) @StorableText
          String status) {
    return liveEnvironmentApplicationService.list(status);
  }

  @PutMapping("/v1/live-environment-applications/{id}/status")
  @OperatorAccess
  @Operation(
      summary = "라이브 환경 이용 신청 검토",
      description =
          """
          검토 중인 신청을 승인하거나 거절하고 바뀐 신청을 돌려줍니다.
          승인하면 신청한 조직의 라이브 환경이 열려 그 조직이 라이브 환경과 라이브 키를 쓸 수 있습니다. 신청할 때 받은 토스페이먼츠 키도 이때 라이브 환경의 토스페이먼츠 키로 저장합니다.
          거절은 rejection_reason이 있어야 하고, 신청한 조직이 그 사유를 읽습니다. 거절된 조직은 다시 신청할 수 있습니다.
          MeterEngine 운영자 로그인 세션만 부를 수 있습니다.
          """)
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "검토를 마친 신청"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            "code=validation_error: status가 approved나 rejected가 아니거나, 거절인데 rejection_reason이 비었거나, 승인인데"
                + " rejection_reason을 보냈거나, 경로의 UUID 형식이 틀렸습니다."),
    @ApiResponse(
        responseCode = "404",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=live_environment_application_not_found: 그런 신청이 없습니다."),
    @ApiResponse(
        responseCode = "409",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = "code=live_environment_application_already_reviewed: 이미 승인하거나 거절한 신청입니다.")
  })
  public LiveEnvironmentApplicationResponse updateLiveEnvironmentApplicationStatus(
      @AuthenticationPrincipal AuthenticatedPrincipal principal,
      @Parameter(description = "검토할 신청의 ID.") @PathVariable UUID id,
      @Valid @RequestBody UpdateLiveEnvironmentApplicationStatusRequest request) {
    return liveEnvironmentApplicationService.update(principal, id, request);
  }
}
