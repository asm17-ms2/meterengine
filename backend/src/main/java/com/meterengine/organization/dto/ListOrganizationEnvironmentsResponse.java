package com.meterengine.organization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ListOrganizationEnvironmentsResponse(
    @Schema(description = "조직의 업무 환경 목록입니다. 테스트 환경이 먼저입니다.")
        List<OrganizationEnvironmentResponse> environments) {}
