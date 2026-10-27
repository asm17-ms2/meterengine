package com.meterengine.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import java.util.List;

public record EventIssuesResponse(
    String month,
    @JsonProperty("cutoff_at") OffsetDateTime cutoffAt,
    int page,
    int size,
    long total,
    List<EventIssueResponse> issues,
    Summary summary,
    Warnings warnings) {
  public record Summary(
      @JsonProperty("unknown_event_type") long unknownEventType,
      @JsonProperty("invalid_target_property") long invalidTargetProperty,
      @JsonProperty("unpriced_usage") long unpricedUsage) {
    public long total() {
      return Math.addExact(Math.addExact(unknownEventType, invalidTargetProperty), unpricedUsage);
    }
  }

  public record Warnings(
      String scope,
      @JsonProperty("future_event_count") long futureEventCount,
      @JsonProperty("negative_event_metric_count") long negativeEventMetricCount) {}
}
