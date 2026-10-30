package com.meterengine.pricing.service;

import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.global.security.BusinessEnvironmentContext;
import com.meterengine.metric.repository.BillableMetricRepository;
import com.meterengine.metric.service.BillableMetricUsageService;
import com.meterengine.pricing.dto.BillableMetricPricePriceRate;
import com.meterengine.pricing.dto.BillableMetricPriceResponse;
import com.meterengine.pricing.dto.ListBillableMetricPricesResponse;
import com.meterengine.pricing.entity.PriceRate;
import com.meterengine.pricing.repository.PriceRateRepository;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
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
  private final CustomerRepository customerRepository;
  private final JsonMapper jsonMapper;

  BillableMetricPriceService(
      BillableMetricRepository billableMetricRepository,
      PriceRateRepository priceRateRepository,
      CustomerRepository customerRepository,
      JsonMapper jsonMapper) {
    this.billableMetricRepository = billableMetricRepository;
    this.priceRateRepository = priceRateRepository;
    this.customerRepository = customerRepository;
    this.jsonMapper = jsonMapper;
  }

  @Transactional(readOnly = true)
  public ListBillableMetricPricesResponse list(UUID organizationId) {
    return list(organizationId, null, null);
  }

  @Transactional(readOnly = true)
  public ListBillableMetricPricesResponse list(
      UUID organizationId, YearMonth month, UUID customerId) {
    if (customerId != null
        && !(BusinessEnvironmentContext.environmentId() == null
            ? customerRepository.existsByOrganizationIdAndId(organizationId, customerId)
            : customerRepository
                .findScoped(organizationId, BusinessEnvironmentContext.environmentId(), customerId)
                .isPresent())) throw new NotFoundException(ErrorCode.CUSTOMER_NOT_FOUND);
    String period =
        (month == null ? YearMonth.now(BillableMetricUsageService.BILLING_ZONE) : month).toString();
    Map<PriceRateKey, PriceRate> priceRateByPriceRateKey = new LinkedHashMap<>();
    for (PriceRate rate :
        (BusinessEnvironmentContext.environmentId() == null
            ? priceRateRepository
                .findByOrganizationIdOrderByBillableMetricCodeAscDimensionValuesAsc(organizationId)
            : priceRateRepository.listScoped(
                organizationId, BusinessEnvironmentContext.environmentId()))) {
      if (!Objects.equals(rate.getCustomerId(), customerId)
          || (!rate.applies(period) && !rate.stops(period))) continue;
      PriceRateKey key =
          new PriceRateKey(
              rate.getBillableMetricCode(),
              PriceRateDimensions.normalize(
                  jsonMapper.readValue(rate.getDimensionValues(), DIMENSION_VALUES_TYPE)));
      priceRateByPriceRateKey.merge(
          key,
          rate,
          (left, right) ->
              left.getEffectiveFrom().compareTo(right.getEffectiveFrom()) >= 0 ? left : right);
    }
    priceRateByPriceRateKey.values().removeIf(rate -> !rate.applies(period));
    return new ListBillableMetricPricesResponse(
        (BusinessEnvironmentContext.environmentId() == null
                ? billableMetricRepository.findByOrganizationIdOrderByCodeAsc(organizationId)
                : billableMetricRepository.listScoped(
                    organizationId, BusinessEnvironmentContext.environmentId()))
            .stream()
                .map(
                    metric ->
                        BillableMetricPriceResponse.of(
                            metric.getCode(),
                            metric.getDimensionProperties(),
                            priceRateByPriceRateKey.values().stream()
                                .filter(
                                    rate -> rate.getBillableMetricCode().equals(metric.getCode()))
                                .map(this::toResponse)
                                .toList()))
                .toList());
  }

  private BillableMetricPricePriceRate toResponse(PriceRate rate) {
    return new BillableMetricPricePriceRate(
        jsonMapper.readValue(rate.getDimensionValues(), DIMENSION_VALUES_TYPE),
        rate.getUnitPrice(),
        rate.getId(),
        rate.getCustomerId(),
        rate.getEffectiveFrom(),
        rate.getEffectiveUntil(),
        rate.getModel(),
        rate.getTiers(),
        rate.getArchivedAt(),
        rate.getArchivedFrom());
  }

  private record PriceRateKey(
      String billableMetricCode, Map<String, Object> dimensionValueByDimensionProperty) {}
}
