package com.meterengine.query.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record OptionResponse(
    @Schema(description = "선택지의 값입니다. 고객 선택지는 고객 ID, 미터 선택지는 미터 코드입니다.") String value,
    @Schema(description = "선택지에 보일 이름입니다. 고객 선택지는 고객 이름, 미터 선택지는 미터 이름입니다.") String label,
    @Schema(description = "보관한 고객이나 미터면 true입니다.") boolean archived) {}
