package com.meterengine.pricing.service;

import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.metric.repository.BillableMetricRepository;
import com.meterengine.pricing.dto.BillableMetricPricePriceRate;
import com.meterengine.pricing.dto.BillableMetricPriceResponse;
import com.meterengine.pricing.dto.ListBillableMetricPricesResponse;
import com.meterengine.pricing.entity.PriceRate;
import com.meterengine.pricing.repository.PriceRateRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class BillableMetricPriceService {

  private static final TypeReference<Map<String, Object>> DIMENSION_VALUES_TYPE =
      new TypeReference<>() {};

  private final BillableMetricRepository billableMetricRepository;
  private final PriceRateRepository priceRateRepository;
  private final JsonMapper jsonMapper;

  BillableMetricPriceService(
      BillableMetricRepository billableMetricRepository,
      PriceRateRepository priceRateRepository,
      JsonMapper jsonMapper) {
    this.billableMetricRepository = billableMetricRepository;
    this.priceRateRepository = priceRateRepository;
    this.jsonMapper = jsonMapper;
  }

  @Transactional(readOnly = true)
  public ListBillableMetricPricesResponse list(UUID organizationId) {
    Map<String, List<BillableMetricPricePriceRate>> priceRatesByBillableMetricCode =
        priceRateRepository
            .findByOrganizationIdOrderByBillableMetricCodeAscDimensionValuesAsc(organizationId)
            .stream()
            .collect(
                Collectors.groupingBy(
                    PriceRate::getBillableMetricCode,
                    Collectors.mapping(this::toBillableMetricPricePriceRate, Collectors.toList())));

    return new ListBillableMetricPricesResponse(
        billableMetricRepository.findByOrganizationIdOrderByCodeAsc(organizationId).stream()
            .map(
                billableMetric ->
                    toBillableMetricPriceResponse(billableMetric, priceRatesByBillableMetricCode))
            .toList());
  }

  private static BillableMetricPriceResponse toBillableMetricPriceResponse(
      BillableMetric billableMetric,
      Map<String, List<BillableMetricPricePriceRate>> priceRatesByBillableMetricCode) {
    return BillableMetricPriceResponse.of(
        billableMetric.getCode(),
        billableMetric.getDimensionProperties(),
        priceRatesByBillableMetricCode.getOrDefault(billableMetric.getCode(), List.of()));
  }

  private BillableMetricPricePriceRate toBillableMetricPricePriceRate(PriceRate priceRate) {
    return new BillableMetricPricePriceRate(
        jsonMapper.readValue(priceRate.getDimensionValues(), DIMENSION_VALUES_TYPE),
        priceRate.getUnitPrice());
  }
}
