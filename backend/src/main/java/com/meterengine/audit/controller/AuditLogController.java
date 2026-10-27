package com.meterengine.audit.controller;

import com.meterengine.audit.dto.ListAuditLogsResponse;
import com.meterengine.audit.service.AuditLogQueryService;
import com.meterengine.global.security.CurrentOrganizationId;
import com.meterengine.global.security.EnvironmentReady;
import com.meterengine.global.security.OperatorAccess;
import com.meterengine.global.security.ReadAccess;
import com.meterengine.global.validation.StorableText;
import io.swagger.v3.oas.annotations.Operation;
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
  private final AuditLogQueryService auditLogQueryService;

  public AuditLogController(AuditLogQueryService auditLogQueryService) {
    this.auditLogQueryService = auditLogQueryService;
  }

  @GetMapping("/v1/audit-logs")
  @EnvironmentReady
  @ReadAccess
  @Operation(summary = "현재 도입사 감사 기록 조회")
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
      @RequestParam(required = false) @Size(max = 40) @StorableText String from,
      @RequestParam(required = false) @Size(max = 40) @StorableText String to,
      @RequestParam(defaultValue = "occurred_at_desc")
          @Pattern(regexp = "occurred_at_asc|occurred_at_desc") @StorableText
          String sort) {
    return auditLogQueryService.list(
        organizationId, page, size, action, actorType, q, from, to, sort);
  }

  @GetMapping("/v1/operations/audit-logs")
  @OperatorAccess
  @Operation(summary = "플랫폼 감사 기록 조회")
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
      @RequestParam(required = false) @Size(max = 40) @StorableText String from,
      @RequestParam(required = false) @Size(max = 40) @StorableText String to,
      @RequestParam(defaultValue = "occurred_at_desc")
          @Pattern(regexp = "occurred_at_asc|occurred_at_desc") @StorableText
          String sort) {
    return auditLogQueryService.list(null, page, size, action, actorType, q, from, to, sort);
  }
}
