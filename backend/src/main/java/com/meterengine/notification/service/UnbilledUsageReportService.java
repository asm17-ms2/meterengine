package com.meterengine.notification.service;

import com.meterengine.event.dto.UnbilledEventsResponse;
import com.meterengine.event.service.UnbilledEventService;
import com.meterengine.metric.service.BillableMetricUsageService;
import com.meterengine.pricing.dto.CustomerCharge;
import com.meterengine.pricing.service.ChargeService;
import com.meterengine.webhook.entity.WebhookEventType;
import com.meterengine.webhook.repository.WebhookEndpointRepository;
import com.meterengine.webhook.service.WebhookEventService;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UnbilledUsageReportService {
  private final UnbilledEventService unbilledEventService;
  private final BillableMetricUsageService billableMetricUsageService;
  private final ChargeService chargeService;
  private final WebhookEventService webhookEventService;
  private final WebhookEndpointRepository webhookEndpointRepository;

  UnbilledUsageReportService(
      UnbilledEventService unbilledEventService,
      BillableMetricUsageService billableMetricUsageService,
      ChargeService chargeService,
      WebhookEventService webhookEventService,
      WebhookEndpointRepository webhookEndpointRepository) {
    this.unbilledEventService = unbilledEventService;
    this.billableMetricUsageService = billableMetricUsageService;
    this.chargeService = chargeService;
    this.webhookEventService = webhookEventService;
    this.webhookEndpointRepository = webhookEndpointRepository;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public boolean report(UUID organizationId) {
    WebhookEventType type = WebhookEventType.UNBILLED_USAGE_DETECTED;
    if (!webhookEndpointRepository.existsByEventType(organizationId, type.getValue())) {
      return false;
    }
    UnbilledEventsResponse current = unbilledEventService.get(organizationId);
    YearMonth previousMonth = YearMonth.parse(current.month()).minusMonths(1);
    List<Map<String, Object>> payloads =
        Stream.of(unbilledEventService.get(organizationId, previousMonth), current)
            .map(summary -> payload(organizationId, summary))
            .flatMap(Optional::stream)
            .toList();
    payloads.forEach(payload -> webhookEventService.publish(organizationId, type, payload));
    return !payloads.isEmpty();
  }

  private Optional<Map<String, Object>> payload(
      UUID organizationId, UnbilledEventsResponse summary) {
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
                            usage ->
                                usage(
                                    charge.customerId(),
                                    usage.billableMetricCode(),
                                    usage.targetProperty(),
                                    usage.dimensionValues(),
                                    usage.quantity())))
            .toList();
    List<Map<String, Object>> unpriced =
        charges.stream()
            .flatMap(
                charge ->
                    charge.unpricedUsages().stream()
                        .map(
                            usage ->
                                usage(
                                    charge.customerId(),
                                    usage.billableMetricCode(),
                                    usage.targetProperty(),
                                    usage.dimensionValues(),
                                    usage.quantity())))
            .toList();
    if (summary.isEmpty() && negatives.isEmpty() && unpriced.isEmpty()) {
      return Optional.empty();
    }
    Map<String, Object> fieldsByName = new LinkedHashMap<>();
    fieldsByName.put("month", summary.month());
    fieldsByName.put("cutoff_at", summary.cutoffAt());
    fieldsByName.put("unmatched_type", summary.unmatchedType());
    fieldsByName.put("non_numeric_value", summary.nonNumericValue());
    fieldsByName.put("future_timestamp", summary.futureTimestamp());
    fieldsByName.put("negative_quantity", summary.negativeQuantity());
    fieldsByName.put("negative_usages", negatives);
    fieldsByName.put("unpriced_usages", unpriced);
    return Optional.of(fieldsByName);
  }

  private static Map<String, Object> usage(
      UUID customerId,
      String billableMetricCode,
      String targetProperty,
      Map<String, Object> dimensionValues,
      BigDecimal quantity) {
    Map<String, Object> fieldsByName = new LinkedHashMap<>();
    fieldsByName.put("customer_id", customerId);
    fieldsByName.put("billable_metric_code", billableMetricCode);
    fieldsByName.put("target_property", targetProperty);
    fieldsByName.put("dimension_values", dimensionValues);
    fieldsByName.put("quantity", quantity);
    return fieldsByName;
  }
}
