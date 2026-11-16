package com.meterengine.organization.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record ListManagedMembersResponse(
    @Schema(description = "조직 사용자 목록입니다. 소속된 순서입니다.") List<ManagedMemberResponse> members) {}
