package com.meterengine.audit.controller;

import com.meterengine.audit.dto.ListAuditLogsResponse;
import com.meterengine.audit.service.AuditLogQueryService;
import com.meterengine.global.error.ErrorResponse;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.EnvironmentReady;
import com.meterengine.global.security.MemberReadAccess;
import com.meterengine.global.security.OperatorAccess;
import com.meterengine.global.validation.StorableText;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuditLogController {
  private static final String FROM_DESCRIPTION =
      "이 시각부터(포함)입니다. yyyy-MM-dd는 그날 KST 0시, 오프셋이 있는 ISO 8601 시각은 그 순간입니다. 연도는 0001부터 9999까지입니다.";
  private static final String TO_DESCRIPTION =
      "이 시각까지(미포함)입니다. yyyy-MM-dd는 그날 끝까지 담고(다음 날 KST 0시 미포함), 오프셋이 있는 ISO 8601 시각은 그 순간을 뺍니다. 연도는 0001부터 9999까지입니다.";
  private static final String PAGE_DESCRIPTION = "0부터 세는 페이지 번호";
  private static final String SIZE_DESCRIPTION = "한 페이지에 담을 기록 수(1 이상 100 이하)";
  private static final String ACTION_DESCRIPTION = "찾을 활동 코드(예: api_key_created). 같은 코드만 찾습니다.";
  private static final String ACTOR_TYPE_DESCRIPTION =
      "찾을 실행 주체 종류입니다. user_account(로그인 사용자), api_key(API 키), system(시스템), anonymous(미인증 요청),"
          + " invoice_view_link(청구서 조회 링크) 가운데 하나입니다.";
  private static final String SORT_DESCRIPTION =
      "정렬 순서입니다. occurred_at_desc는 최근 순(기본), occurred_at_asc는 오래된 순입니다.";
  private static final String VALIDATION_DESCRIPTION =
      "code=validation_error: page, size, action, actor_type, sort가 범위나 형식 밖이거나, from과 to가 날짜나 오프셋 있는 시각이 아니거나 연도가 0001부터 9999까지의 범위 밖이거나, from이 to보다 늦거나 같습니다.";
  private final AuditLogQueryService auditLogQueryService;

  public AuditLogController(AuditLogQueryService auditLogQueryService) {
    this.auditLogQueryService = auditLogQueryService;
  }

  @GetMapping("/v1/audit-logs")
  @EnvironmentReady
  @MemberReadAccess
  @Operation(
      summary = "현재 조직 감사 기록 조회",
      description =
          """
          로그인 세션만 부를 수 있습니다. 구성원과 키 관리 이력, 요청 기록을 담고 있어 API 키로는 조회할 수 없습니다.
          현재 업무 환경의 기록과, 로그인, 구성원, 초대, 라이브 환경 이용 신청처럼 조직 공통인 기록을 함께 돌려줍니다.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "감사 기록 목록입니다. 조건에 맞는 것이 없으면 audit_logs가 빈 배열입니다."),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = VALIDATION_DESCRIPTION)
  })
  public ListAuditLogsResponse listOrganizationAuditLogs(
      @CurrentOrganizationId UUID organizationId,
      @Parameter(description = PAGE_DESCRIPTION) @RequestParam(defaultValue = "0") @Min(0) int page,
      @Parameter(description = SIZE_DESCRIPTION)
          @RequestParam(defaultValue = "20")
          @Min(1) @Max(100) int size,
      @Parameter(description = ACTION_DESCRIPTION)
          @RequestParam(required = false)
          @Pattern(regexp = "[a-z]+(_[a-z]+)*")
          @Size(max = 255) @StorableText
          String action,
      @Parameter(description = ACTOR_TYPE_DESCRIPTION)
          @RequestParam(name = "actor_type", required = false)
          @Pattern(regexp = "user_account|api_key|system|anonymous|invoice_view_link") @StorableText
          String actorType,
      @Parameter(
              description =
                  "action, target_type, target_id와 detail의 request_id, route에서 부분 일치로 찾을 문자열입니다. 대소문자를 가리지 않습니다.")
          @RequestParam(required = false)
          @Size(max = 255) @StorableText
          String q,
      @Parameter(description = FROM_DESCRIPTION)
          @RequestParam(required = false)
          @Size(max = 40) @StorableText
          String from,
      @Parameter(description = TO_DESCRIPTION)
          @RequestParam(required = false)
          @Size(max = 40) @StorableText
          String to,
      @Parameter(description = SORT_DESCRIPTION)
          @RequestParam(defaultValue = "occurred_at_desc")
          @Pattern(regexp = "occurred_at_asc|occurred_at_desc") @StorableText
          String sort) {
    return auditLogQueryService.list(
        organizationId, page, size, action, actorType, q, from, to, sort);
  }

  @GetMapping("/v1/operations/audit-logs")
  @OperatorAccess
  @Operation(
      summary = "전체 감사 기록 조회",
      description =
          """
          MeterEngine 운영자 로그인 세션만 부를 수 있습니다. 모든 조직의 기록과 조직 없는 기록을 돌려줍니다.
          q는 action, target_type, target_id, detail의 request_id와 route에 더해 organization_id도 부분 일치로 찾습니다.
          조직 ID를 q로 보내면 그 조직의 기록과 함께, 그 ID를 대상(target_id)으로 남긴 운영자 요청 같은 조직 없는 기록도 나옵니다.
          """)
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "감사 기록 목록입니다. 조건에 맞는 것이 없으면 audit_logs가 빈 배열입니다."),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description = VALIDATION_DESCRIPTION)
  })
  public ListAuditLogsResponse listOperationsAuditLogs(
      @Parameter(description = PAGE_DESCRIPTION) @RequestParam(defaultValue = "0") @Min(0) int page,
      @Parameter(description = SIZE_DESCRIPTION)
          @RequestParam(defaultValue = "20")
          @Min(1) @Max(100) int size,
      @Parameter(description = ACTION_DESCRIPTION)
          @RequestParam(required = false)
          @Pattern(regexp = "[a-z]+(_[a-z]+)*")
          @Size(max = 255) @StorableText
          String action,
      @Parameter(description = ACTOR_TYPE_DESCRIPTION)
          @RequestParam(name = "actor_type", required = false)
          @Pattern(regexp = "user_account|api_key|system|anonymous|invoice_view_link") @StorableText
          String actorType,
      @Parameter(
              description =
                  "action, target_type, target_id, organization_id와 detail의 request_id, route에서 부분 일치로 찾을 문자열입니다."
                      + " 대소문자를 가리지 않습니다.")
          @RequestParam(required = false)
          @Size(max = 255) @StorableText
          String q,
      @Parameter(description = FROM_DESCRIPTION)
          @RequestParam(required = false)
          @Size(max = 40) @StorableText
          String from,
      @Parameter(description = TO_DESCRIPTION)
          @RequestParam(required = false)
          @Size(max = 40) @StorableText
          String to,
      @Parameter(description = SORT_DESCRIPTION)
          @RequestParam(defaultValue = "occurred_at_desc")
          @Pattern(regexp = "occurred_at_asc|occurred_at_desc") @StorableText
          String sort) {
    return auditLogQueryService.list(null, page, size, action, actorType, q, from, to, sort);
  }
}
