package com.meterengine.pricing.service;

import com.meterengine.global.security.UsagePriceDiagnostics;
import com.meterengine.metric.service.BillableMetricUsageService;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UsagePriceDiagnosticsService implements UsagePriceDiagnostics {
  private final BillableMetricUsageService billableMetricUsageService;
  private final ChargeService chargeService;

  public UsagePriceDiagnosticsService(
      BillableMetricUsageService billableMetricUsageService, ChargeService chargeService) {
    this.billableMetricUsageService = billableMetricUsageService;
    this.chargeService = chargeService;
  }

  @Override
  @Transactional(readOnly = true)
  public List<UnpricedGroup> unpriced(UUID organizationId, YearMonth month, OffsetDateTime cutoff) {
    return chargeService
        .calculate(
            organizationId,
            month,
            billableMetricUsageService.aggregate(organizationId, month, cutoff))
        .values()
        .stream()
        .flatMap(
            customer ->
                customer.unpricedUsages().stream()
                    .map(
                        usage ->
                            new UnpricedGroup(
                                customer.customerId(),
                                usage.billableMetricCode(),
                                usage.dimensionValues())))
        .toList();
  }
}
