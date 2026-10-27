package com.meterengine.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

public record UnbilledEventsResponse(
    String month,
    @JsonProperty("cutoff_at") OffsetDateTime cutoffAt,
    @JsonProperty("unmatched_type") UnmatchedType unmatchedType,
    @JsonProperty("non_numeric_value") MetricCounts nonNumericValue,
    @JsonProperty("future_timestamp") FutureTimestamp futureTimestamp,
    @JsonProperty("negative_quantity") MetricCounts negativeQuantity) {

  public record UnmatchedType(
      @JsonProperty("event_count") long eventCount, List<TypeCount> types) {}

  public record TypeCount(String type, @JsonProperty("event_count") long eventCount) {}

  public record MetricCounts(
      @JsonProperty("event_count") long eventCount,
      @JsonProperty("billable_metrics") List<MetricCount> billableMetrics) {}

  public record MetricCount(
      @JsonProperty("billable_metric_code") String billableMetricCode,
      @JsonProperty("event_count") long eventCount) {}

  public record FutureTimestamp(
      @JsonProperty("event_count") long eventCount,
      @JsonProperty("earliest_occurred_at") OffsetDateTime earliestOccurredAt,
      @JsonProperty("latest_occurred_at") OffsetDateTime latestOccurredAt) {}

  @JsonIgnore
  @Schema(hidden = true)
  public boolean isEmpty() {
    return unmatchedType.eventCount() == 0
        && nonNumericValue.eventCount() == 0
        && futureTimestamp.eventCount() == 0
        && negativeQuantity.eventCount() == 0;
  }
}
