package com.meterengine.query.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

public record SearchPageResponse<T>(
    @Schema(description = "이 페이지의 항목") List<T> items,
    @Schema(description = "조건에 맞는 전체 건수") long total,
    @Schema(description = "조건을 적용하기 전의 전체 건수입니다. 무엇을 세는지는 경로 설명에 있습니다.")
        @JsonProperty("total_unfiltered")
        long totalUnfiltered,
    @Schema(description = "1부터 세는 페이지 번호") int page,
    @Schema(description = "한 페이지에 담는 개수") int size,
    @Schema(description = "요약 값입니다. 페이지와 관계없이 계산하며, 담는 키와 뜻은 경로 설명에 있습니다.")
        Map<String, Object> summary) {}
