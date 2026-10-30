package com.meterengine.query.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

public record SearchPageResponse<T>(
    List<T> items,
    long total,
    @JsonProperty("total_unfiltered") long totalUnfiltered,
    int page,
    int size,
    Map<String, Object> summary) {}
