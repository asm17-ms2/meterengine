package com.meterengine.audit.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record ListAuditLogsResponse(
    @JsonProperty("audit_logs") List<AuditLogResponse> auditLogs, long total, int page, int size) {}
