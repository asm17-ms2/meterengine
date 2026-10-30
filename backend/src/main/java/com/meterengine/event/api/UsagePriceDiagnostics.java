package com.meterengine.event.api;

import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface UsagePriceDiagnostics {
  List<UnpricedGroup> unpriced(UUID organizationId, YearMonth month, OffsetDateTime cutoff);

  record UnpricedGroup(
      UUID customerId, String billableMetricCode, Map<String, Object> valueByDimension) {}
}
