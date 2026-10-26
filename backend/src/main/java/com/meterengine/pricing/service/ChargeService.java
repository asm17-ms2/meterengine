package com.meterengine.pricing.service;

import com.meterengine.metric.dto.BillableMetricUsage;
import com.meterengine.metric.dto.CustomerUsage;
import com.meterengine.metric.dto.DimensionUsage;
import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.pricing.dto.CustomerCharge;
import com.meterengine.pricing.dto.DimensionCharge;
import com.meterengine.pricing.dto.NegativeUsage;
import com.meterengine.pricing.dto.UnpricedUsage;
import com.meterengine.pricing.entity.PriceRate;
import com.meterengine.pricing.repository.PriceRateRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ChargeService {

  private static final TypeReference<Map<String, Object>> DIMENSION_VALUES_TYPE =
      new TypeReference<>() {};

  private final PriceRateRepository priceRateRepository;
  private final JsonMapper jsonMapper;

  ChargeService(PriceRateRepository priceRateRepository, JsonMapper jsonMapper) {
    this.priceRateRepository = priceRateRepository;
    this.jsonMapper = jsonMapper;
  }

  @Transactional(readOnly = true)
  public Map<UUID, CustomerCharge> calculate(
      UUID organizationId, YearMonth month, List<BillableMetricUsage> billableMetricUsages) {
    Map<PriceRateKey, BigDecimal> unitPriceByPriceRateKey = unitPriceByPriceRateKey(organizationId);

    Map<UUID, CustomerCharges> customerChargesByCustomerId = new LinkedHashMap<>();
    for (BillableMetricUsage billableMetricUsage : billableMetricUsages) {
      BillableMetric billableMetric = billableMetricUsage.billableMetric();
      for (CustomerUsage customerUsage : billableMetricUsage.customers()) {
        CustomerCharges customerCharges =
            customerChargesByCustomerId.computeIfAbsent(
                customerUsage.customerId(), customerId -> new CustomerCharges());
        for (DimensionUsage dimensionUsage : customerUsage.dimensionUsages()) {
          classify(billableMetric, dimensionUsage, unitPriceByPriceRateKey, customerCharges);
        }
      }
    }

    Map<UUID, CustomerCharge> customerChargeByCustomerId = new LinkedHashMap<>();
    customerChargesByCustomerId.forEach(
        (customerId, customerCharges) ->
            customerChargeByCustomerId.put(
                customerId, toCustomerCharge(customerId, customerCharges)));
    return customerChargeByCustomerId;
  }

  private Map<PriceRateKey, BigDecimal> unitPriceByPriceRateKey(UUID organizationId) {
    return priceRateRepository
        .findByOrganizationIdOrderByBillableMetricCodeAscDimensionValuesAsc(organizationId)
        .stream()
        .collect(Collectors.toMap(this::toPriceRateKey, PriceRate::getUnitPrice));
  }

  private PriceRateKey toPriceRateKey(PriceRate priceRate) {
    return new PriceRateKey(
        priceRate.getBillableMetricCode(),
        jsonMapper.readValue(priceRate.getDimensionValues(), DIMENSION_VALUES_TYPE));
  }

  // 조합 하나를 음수 사용량, 라인, 단가 없는 사용량 중 하나에 싣거나 버린다.
  private static void classify(
      BillableMetric billableMetric,
      DimensionUsage dimensionUsage,
      Map<PriceRateKey, BigDecimal> unitPriceByPriceRateKey,
      CustomerCharges customerCharges) {
    BigDecimal quantity = dimensionUsage.quantity();
    if (quantity.signum() < 0) {
      customerCharges
          .negativeUsages()
          .add(
              new NegativeUsage(
                  billableMetric.getCode(),
                  billableMetric.getTargetProperty(),
                  dimensionUsage.dimensionValues(),
                  quantity));
      return;
    }
    BigDecimal unitPrice =
        unitPriceByPriceRateKey.get(
            new PriceRateKey(billableMetric.getCode(), dimensionUsage.dimensionValues()));
    if (unitPrice != null) {
      customerCharges
          .dimensionCharges()
          .add(
              new DimensionCharge(
                  billableMetric.getCode(),
                  billableMetric.getTargetProperty(),
                  dimensionUsage.dimensionValues(),
                  quantity,
                  unitPrice,
                  charge(quantity, unitPrice)));
    } else if (quantity.signum() != 0) {
      customerCharges
          .unpricedUsages()
          .add(
              new UnpricedUsage(
                  billableMetric.getCode(),
                  billableMetric.getTargetProperty(),
                  dimensionUsage.dimensionValues(),
                  quantity));
    }
  }

  private static CustomerCharge toCustomerCharge(UUID customerId, CustomerCharges customerCharges) {
    long amount =
        customerCharges.dimensionCharges().stream()
            .mapToLong(DimensionCharge::amount)
            .reduce(0L, Math::addExact);
    return new CustomerCharge(
        customerId,
        amount,
        List.copyOf(customerCharges.dimensionCharges()),
        List.copyOf(customerCharges.unpricedUsages()),
        List.copyOf(customerCharges.negativeUsages()));
  }

  private static long charge(BigDecimal quantity, BigDecimal unitPrice) {
    return quantity.multiply(unitPrice).setScale(-1, RoundingMode.DOWN).longValueExact();
  }

  // 고객 한 명의 계산 중간 결과다.
  private record CustomerCharges(
      List<DimensionCharge> dimensionCharges,
      List<UnpricedUsage> unpricedUsages,
      List<NegativeUsage> negativeUsages) {

    private CustomerCharges() {
      this(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }
  }

  // 단가를 찾는 키다.
  private record PriceRateKey(
      String billableMetricCode, Map<String, Object> dimensionValueByDimensionProperty) {}
}
