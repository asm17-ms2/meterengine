package com.meterengine.notification.service;

import com.meterengine.event.dto.UnbilledEventsResponse;
import com.meterengine.event.service.UnbilledEventService;
import com.meterengine.metric.service.BillableMetricUsageService;
import com.meterengine.pricing.dto.CustomerCharge;
import com.meterengine.pricing.service.ChargeService;
import com.meterengine.webhook.entity.WebhookEventType;
import com.meterengine.webhook.service.WebhookEventService;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UnbilledUsageReportService {
  private final UnbilledEventService unbilledEventService;
  private final BillableMetricUsageService billableMetricUsageService;
  private final ChargeService chargeService;
  private final WebhookEventService webhookEventService;

  UnbilledUsageReportService(
      UnbilledEventService unbilledEventService,
      BillableMetricUsageService billableMetricUsageService,
      ChargeService chargeService,
      WebhookEventService webhookEventService) {
    this.unbilledEventService = unbilledEventService;
    this.billableMetricUsageService = billableMetricUsageService;
    this.chargeService = chargeService;
    this.webhookEventService = webhookEventService;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean report(UUID organizationId) {
    UnbilledEventsResponse summary = unbilledEventService.get(organizationId);
    YearMonth month = YearMonth.parse(summary.month());
    List<CustomerCharge> charges =
        chargeService
            .calculate(
                organizationId, month, billableMetricUsageService.aggregate(organizationId, month))
            .values()
            .stream()
            .toList();
    List<Map<String, Object>> negatives =
        charges.stream()
            .flatMap(
                charge ->
                    charge.negativeUsages().stream()
                        .map(
                            usage -> {
                              Map<String, Object> fieldsByName = new LinkedHashMap<>();
                              fieldsByName.put("customer_id", charge.customerId());
                              fieldsByName.put("billable_metric_code", usage.billableMetricCode());
                              fieldsByName.put("target_property", usage.targetProperty());
                              fieldsByName.put("dimension_values", usage.dimensionValues());
                              fieldsByName.put("quantity", usage.quantity());
                              return fieldsByName;
                            }))
            .toList();
    if (summary.isEmpty() && negatives.isEmpty()) {
      return false;
    }
    Map<String, Object> fieldsByName = new LinkedHashMap<>();
    fieldsByName.put("month", summary.month());
    fieldsByName.put("cutoff_at", summary.cutoffAt());
    fieldsByName.put("unmatched_type", summary.unmatchedType());
    fieldsByName.put("non_numeric_value", summary.nonNumericValue());
    fieldsByName.put("future_timestamp", summary.futureTimestamp());
    fieldsByName.put("negative_quantity", summary.negativeQuantity());
    fieldsByName.put("negative_usages", negatives);
    webhookEventService.publish(
        organizationId, WebhookEventType.UNBILLED_USAGE_DETECTED, fieldsByName);
    return true;
  }
}
