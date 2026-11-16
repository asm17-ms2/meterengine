package com.meterengine.audit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ListAuditLogsResponse(
    @Schema(description = "이 페이지의 감사 기록") @JsonProperty("audit_logs")
        List<AuditLogResponse> auditLogs,
    @Schema(description = "조건에 맞는 감사 기록 전체 건수") long total,
    @Schema(description = "0부터 세는 페이지 번호") int page,
    @Schema(description = "한 페이지에 담는 개수") int size) {}
