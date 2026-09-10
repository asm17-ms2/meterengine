package com.meterengine.pricing.service;

import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.metric.repository.BillableMetricRepository;
import com.meterengine.pricing.dto.BillableMetricPriceResponse;
import com.meterengine.pricing.dto.ListBillableMetricPricesResponse;
import com.meterengine.pricing.entity.PricePolicy;
import com.meterengine.pricing.repository.PricePolicyRepository;
import com.meterengine.pricing.repository.PriceRateRepository;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BillableMetricPriceService {

  private final PricePolicyRepository pricePolicyRepository;
  private final BillableMetricRepository billableMetricRepository;
  private final PriceRateRepository priceRateRepository;

  BillableMetricPriceService(
      PricePolicyRepository pricePolicyRepository,
      BillableMetricRepository billableMetricRepository,
      PriceRateRepository priceRateRepository) {
    this.pricePolicyRepository = pricePolicyRepository;
    this.billableMetricRepository = billableMetricRepository;
    this.priceRateRepository = priceRateRepository;
  }

  @Transactional(readOnly = true)
  public ListBillableMetricPricesResponse list(UUID organizationId) {
    Map<String, PricePolicy> pricePolicyByBillableMetricCode =
        pricePolicyRepository.findByOrganizationId(organizationId).stream()
            .collect(Collectors.toMap(PricePolicy::getBillableMetricCode, Function.identity()));
    Map<String, BigDecimal> unitPriceByBillableMetricCode =
        priceRateRepository.findBaseUnitPrices(organizationId);

    return new ListBillableMetricPricesResponse(
        billableMetricRepository.findByOrganizationIdOrderByCodeAsc(organizationId).stream()
            .map(
                billableMetric ->
                    toResponse(
                        billableMetric,
                        pricePolicyByBillableMetricCode,
                        unitPriceByBillableMetricCode))
            .toList());
  }

  private static BillableMetricPriceResponse toResponse(
      BillableMetric billableMetric,
      Map<String, PricePolicy> pricePolicyByBillableMetricCode,
      Map<String, BigDecimal> unitPriceByBillableMetricCode) {
    return BillableMetricPriceResponse.of(
        billableMetric.getCode(),
        pricePolicyByBillableMetricCode.get(billableMetric.getCode()),
        unitPriceByBillableMetricCode.get(billableMetric.getCode()));
  }
}
