package com.meterengine.pricing.service;

import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.metric.repository.BillableMetricRepository;
import com.meterengine.pricing.dto.BillableMetricPriceResponse;
import com.meterengine.pricing.dto.ListBillableMetricPricesResponse;
import com.meterengine.pricing.repository.PriceRateRepository;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillableMetricPriceService {

  private final BillableMetricRepository billableMetricRepository;
  private final PriceRateRepository priceRateRepository;

  BillableMetricPriceService(
      BillableMetricRepository billableMetricRepository, PriceRateRepository priceRateRepository) {
    this.billableMetricRepository = billableMetricRepository;
    this.priceRateRepository = priceRateRepository;
  }

  @Transactional(readOnly = true)
  public ListBillableMetricPricesResponse list(UUID organizationId) {
    Map<String, BigDecimal> baseUnitPriceByBillableMetricCode =
        priceRateRepository.findBaseUnitPriceByBillableMetricCode(organizationId);

    return new ListBillableMetricPricesResponse(
        billableMetricRepository.findByOrganizationIdOrderByCodeAsc(organizationId).stream()
            .map(
                billableMetric ->
                    toBillableMetricPriceResponse(
                        billableMetric, baseUnitPriceByBillableMetricCode))
            .toList());
  }

  private static BillableMetricPriceResponse toBillableMetricPriceResponse(
      BillableMetric billableMetric, Map<String, BigDecimal> baseUnitPriceByBillableMetricCode) {
    return BillableMetricPriceResponse.of(
        billableMetric.getCode(),
        billableMetric.getDimensionProperties(),
        baseUnitPriceByBillableMetricCode.get(billableMetric.getCode()));
  }
}
