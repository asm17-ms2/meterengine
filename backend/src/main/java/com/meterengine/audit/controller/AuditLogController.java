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
      "이 시각부터(포함). yyyy-MM-dd는 그날 KST 0시, 오프셋이 있는 ISO 시각은 그 순간이다. 연도는 0001부터 9999까지다.";
  private static final String TO_DESCRIPTION =
      "이 시각까지(미포함). yyyy-MM-dd는 그날 끝까지 담고(다음 날 KST 0시 미포함), 오프셋이 있는 ISO 시각은 그 순간을 뺀다. 연도는 0001부터 9999까지다.";
  private final AuditLogQueryService auditLogQueryService;

  public AuditLogController(AuditLogQueryService auditLogQueryService) {
    this.auditLogQueryService = auditLogQueryService;
  }

  @GetMapping("/v1/audit-logs")
  @EnvironmentReady
  @MemberReadAccess
  @Operation(
      summary = "현재 도입사 감사 기록 조회",
      description = "로그인 세션 전용이다. 사람과 키 관리 이력, 요청 기록을 담아 API 키로는 조회할 수 없다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "감사 기록 목록. 조건에 맞는 것이 없으면 audit_logs가 빈 배열이다"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            "code=validation_error: page, size, action, actor_type, sort가 범위나 형식 밖이거나, from과 to가 날짜나 오프셋 있는 시각이 아니거나 연도가 0001부터 9999까지의 범위 밖이거나, from이 to보다 늦다")
  })
  public ListAuditLogsResponse listOrganizationAuditLogs(
      @CurrentOrganizationId UUID organizationId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @RequestParam(required = false)
          @Pattern(regexp = "[a-z]+(_[a-z]+)*")
          @Size(max = 255) @StorableText
          String action,
      @RequestParam(name = "actor_type", required = false)
          @Pattern(regexp = "user_account|api_key|system|anonymous|invoice_view_link") @StorableText
          String actorType,
      @RequestParam(required = false) @Size(max = 255) @StorableText String q,
      @Parameter(description = FROM_DESCRIPTION)
          @RequestParam(required = false)
          @Size(max = 40) @StorableText
          String from,
      @Parameter(description = TO_DESCRIPTION)
          @RequestParam(required = false)
          @Size(max = 40) @StorableText
          String to,
      @RequestParam(defaultValue = "occurred_at_desc")
          @Pattern(regexp = "occurred_at_asc|occurred_at_desc") @StorableText
          String sort) {
    return auditLogQueryService.list(
        organizationId, page, size, action, actorType, q, from, to, sort);
  }

  @GetMapping("/v1/operations/audit-logs")
  @OperatorAccess
  @Operation(
      summary = "플랫폼 감사 기록 조회",
      description =
          "q는 action, target_type, target_id, detail의 request_id와 route에 더해 organization_id도 부분 일치로 찾는다. 도입사 id를 q로 보내면 그 도입사의 기록과 함께, 그 id를 대상(target_id)으로 남긴 운영자 요청 같은 도입사 없는 기록도 나온다.")
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "감사 기록 목록. 조건에 맞는 것이 없으면 audit_logs가 빈 배열이다"),
    @ApiResponse(
        responseCode = "400",
        content =
            @Content(
                mediaType = "application/json",
                schema = @Schema(implementation = ErrorResponse.class)),
        description =
            "code=validation_error: page, size, action, actor_type, sort가 범위나 형식 밖이거나, from과 to가 날짜나 오프셋 있는 시각이 아니거나 연도가 0001부터 9999까지의 범위 밖이거나, from이 to보다 늦다")
  })
  public ListAuditLogsResponse listOperationsAuditLogs(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @RequestParam(required = false)
          @Pattern(regexp = "[a-z]+(_[a-z]+)*")
          @Size(max = 255) @StorableText
          String action,
      @RequestParam(name = "actor_type", required = false)
          @Pattern(regexp = "user_account|api_key|system|anonymous|invoice_view_link") @StorableText
          String actorType,
      @RequestParam(required = false) @Size(max = 255) @StorableText String q,
      @Parameter(description = FROM_DESCRIPTION)
          @RequestParam(required = false)
          @Size(max = 40) @StorableText
          String from,
      @Parameter(description = TO_DESCRIPTION)
          @RequestParam(required = false)
          @Size(max = 40) @StorableText
          String to,
      @RequestParam(defaultValue = "occurred_at_desc")
          @Pattern(regexp = "occurred_at_asc|occurred_at_desc") @StorableText
          String sort) {
    return auditLogQueryService.list(null, page, size, action, actorType, q, from, to, sort);
  }
}
