package com.meterengine.pricing.service;

import com.meterengine.global.math.DecimalFraction;
import com.meterengine.metric.dto.BillableMetricUsage;
import com.meterengine.metric.dto.CustomerUsage;
import com.meterengine.metric.dto.DimensionUsage;
import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.pricing.dto.CustomerCharge;
import com.meterengine.pricing.dto.DimensionCharge;
import com.meterengine.pricing.dto.NegativeUsage;
import com.meterengine.pricing.dto.PriceRateTier;
import com.meterengine.pricing.dto.UnpricedUsage;
import com.meterengine.pricing.entity.PricePlan;
import com.meterengine.pricing.entity.PriceRate;
import com.meterengine.pricing.repository.PricePlanRepository;
import com.meterengine.pricing.repository.PriceRateRepository;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class ChargeService {
  private static final TypeReference<Map<String, Object>> DIMENSION_VALUES_TYPE =
      new TypeReference<>() {};
  private static final TypeReference<List<PriceRateTier>> TIERS_TYPE = new TypeReference<>() {};
  private final PriceRateRepository priceRateRepository;
  private final PricePlanRepository pricePlanRepository;
  private final JsonMapper jsonMapper;

  ChargeService(
      PriceRateRepository priceRateRepository,
      PricePlanRepository pricePlanRepository,
      JsonMapper jsonMapper) {
    this.priceRateRepository = priceRateRepository;
    this.pricePlanRepository = pricePlanRepository;
    this.jsonMapper = jsonMapper;
  }

  @Transactional(readOnly = true)
  public Map<UUID, CustomerCharge> calculate(
      UUID organizationId, YearMonth month, List<BillableMetricUsage> usages) {
    String period = month.toString();
    Map<PriceRateKey, PriceRate> priceRateByPriceRateKey = new LinkedHashMap<>();
    for (PriceRate rate :
        priceRateRepository.findByOrganizationIdOrderByBillableMetricCodeAscDimensionValuesAsc(
            organizationId)) {
      if (!active(rate.getEffectiveFrom(), rate.getEffectiveUntil(), period)) continue;
      PriceRateKey key =
          new PriceRateKey(
              rate.getCustomerId(),
              rate.getBillableMetricCode(),
              PriceRateDimensions.normalize(
                  jsonMapper.readValue(rate.getDimensionValues(), DIMENSION_VALUES_TYPE)));
      priceRateByPriceRateKey.merge(
          key,
          rate,
          (left, right) ->
              left.getEffectiveFrom().compareTo(right.getEffectiveFrom()) >= 0 ? left : right);
    }
    Map<UUID, PricePlan> pricePlanByCustomerId = new LinkedHashMap<>();
    for (PricePlan plan :
        pricePlanRepository.findByOrganizationIdOrderByEffectiveFromDesc(organizationId)) {
      if (active(plan.getEffectiveFrom(), plan.getEffectiveUntil(), period))
        pricePlanByCustomerId.putIfAbsent(plan.getCustomerId(), plan);
    }
    Map<UUID, CustomerCharges> customerChargesByCustomerId = new LinkedHashMap<>();
    Set<UUID> usedCustomerIds = new HashSet<>();
    for (BillableMetricUsage usage : usages)
      for (CustomerUsage customer : usage.customers()) {
        CustomerCharges charges =
            customerChargesByCustomerId.computeIfAbsent(
                customer.customerId(), key -> new CustomerCharges());
        for (DimensionUsage dimension : customer.dimensionUsages()) {
          if (quantity(usage.billableMetric(), dimension).signum() > 0)
            usedCustomerIds.add(customer.customerId());
          PriceRate rate =
              priceRateByPriceRateKey.get(
                  new PriceRateKey(
                      customer.customerId(),
                      usage.billableMetric().getCode(),
                      PriceRateDimensions.normalize(dimension.dimensionValues())));
          if (rate == null)
            rate =
                priceRateByPriceRateKey.get(
                    new PriceRateKey(
                        null,
                        usage.billableMetric().getCode(),
                        PriceRateDimensions.normalize(dimension.dimensionValues())));
          classify(usage.billableMetric(), dimension, rate, charges);
        }
      }
    Map<UUID, CustomerCharge> customerChargeByCustomerId = new LinkedHashMap<>();
    customerChargesByCustomerId.forEach(
        (id, charges) -> {
          PricePlan plan = pricePlanByCustomerId.getOrDefault(id, pricePlanByCustomerId.get(null));
          if (plan != null && usedCustomerIds.contains(id)) apply(plan, charges);
          customerChargeByCustomerId.put(
              id,
              new CustomerCharge(
                  id,
                  total(charges),
                  List.copyOf(charges.dimensionCharges()),
                  List.copyOf(charges.unpricedUsages()),
                  List.copyOf(charges.negativeUsages())));
        });
    return customerChargeByCustomerId;
  }

  private void classify(
      BillableMetric metric, DimensionUsage usage, PriceRate rate, CustomerCharges charges) {
    DecimalFraction quantity = quantity(metric, usage);
    if (usage.rawQuantityNumerator().signum() < 0) {
      charges
          .negativeUsages()
          .add(
              new NegativeUsage(
                  metric.getCode(),
                  metric.getTargetProperty(),
                  usage.dimensionValues(),
                  usage.rawQuantity()));
      return;
    }
    if (rate == null) {
      if (quantity.signum() != 0)
        charges
            .unpricedUsages()
            .add(
                new UnpricedUsage(
                    metric.getCode(),
                    metric.getTargetProperty(),
                    usage.dimensionValues(),
                    usage.quantity()));
      return;
    }
    if ("per_unit".equals(rate.getModel())) {
      add(metric, usage, quantity, rate.getUnitPrice(), "per_unit", 0, charges);
      return;
    }
    List<PriceRateTier> tiers = jsonMapper.readValue(rate.getTiers(), TIERS_TYPE);
    if ("volume".equals(rate.getModel())) {
      for (PriceRateTier tier : tiers)
        if (tier.upTo() == null || quantity.compareTo(tier.upTo()) <= 0) {
          add(metric, usage, quantity, tier.unitPrice(), "volume", 0, charges);
          return;
        }
    } else {
      BigDecimal lower = BigDecimal.ZERO;
      for (int index = 0; index < tiers.size(); index++) {
        PriceRateTier tier = tiers.get(index);
        DecimalFraction upper = tier.upTo() == null ? quantity : quantity.min(tier.upTo());
        DecimalFraction tierQuantity = upper.subtract(lower);
        if (tierQuantity.signum() > 0)
          add(metric, usage, tierQuantity, tier.unitPrice(), "graduated_tier", index, charges);
        if (tier.upTo() == null || quantity.compareTo(tier.upTo()) <= 0) break;
        lower = tier.upTo();
      }
    }
  }

  private static void add(
      BillableMetric metric,
      DimensionUsage usage,
      DecimalFraction quantity,
      BigDecimal price,
      String type,
      int tierIndex,
      CustomerCharges charges) {
    charges
        .dimensionCharges()
        .add(
            new DimensionCharge(
                metric.getCode(),
                metric.getTargetProperty(),
                usage.dimensionValues(),
                quantity.decimalValue(usage.quantity().scale()),
                price,
                quantity.charge(price),
                type,
                tierIndex,
                quantity.numerator(),
                quantity.denominator()));
  }

  private static void apply(PricePlan plan, CustomerCharges charges) {
    long adjustment =
        Math.max(0, Math.subtractExact(trim(plan.getMinimumAmount()), total(charges)));
    if (adjustment > 0) charges.dimensionCharges().add(surcharge(adjustment, "minimum_adjustment"));
    long baseFee = trim(plan.getBaseFee());
    if (baseFee > 0) charges.dimensionCharges().add(surcharge(baseFee, "base_fee"));
  }

  private static DimensionCharge surcharge(long amount, String type) {
    return new DimensionCharge(
        null, null, Map.of(), BigDecimal.ONE, BigDecimal.valueOf(amount), amount, type, 0);
  }

  private static long total(CustomerCharges charges) {
    return charges.dimensionCharges().stream()
        .mapToLong(DimensionCharge::amount)
        .reduce(0L, Math::addExact);
  }

  private static boolean active(String from, String until, String month) {
    return from.compareTo(month) <= 0 && (until == null || until.compareTo(month) > 0);
  }

  private static DecimalFraction quantity(BillableMetric metric, DimensionUsage usage) {
    return "none".equals(metric.getRoundingMode())
        ? new DecimalFraction(
            usage.rawQuantityNumerator(),
            usage.rawQuantityDenominator().multiply(metric.getUnitSize()))
        : DecimalFraction.of(usage.quantity());
  }

  private static long trim(long amount) {
    return amount - amount % 10;
  }

  private record CustomerCharges(
      List<DimensionCharge> dimensionCharges,
      List<UnpricedUsage> unpricedUsages,
      List<NegativeUsage> negativeUsages) {
    private CustomerCharges() {
      this(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
    }
  }

  private record PriceRateKey(
      UUID customerId,
      String billableMetricCode,
      Map<String, Object> dimensionValueByDimensionProperty) {}
}
