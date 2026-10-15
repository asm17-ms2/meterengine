package com.meterengine.invoice.service;

import com.meterengine.customer.entity.Customer;
import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.invoice.dto.DraftInvoiceResponse;
import com.meterengine.invoice.dto.DraftInvoiceResponse.DraftInvoiceCustomer;
import com.meterengine.invoice.dto.DraftInvoiceResponse.DraftInvoiceLine;
import com.meterengine.invoice.dto.DraftInvoiceResponse.DraftInvoiceUnpricedUsage;
import com.meterengine.invoice.repository.InvoiceRepository;
import com.meterengine.metric.dto.BillableMetricUsage;
import com.meterengine.metric.dto.CustomerUsage;
import com.meterengine.metric.dto.DimensionUsage;
import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.metric.service.BillableMetricUsageService;
import com.meterengine.pricing.entity.PriceRate;
import com.meterengine.pricing.repository.PriceRateRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

@Service
public class DraftInvoiceService {

  private static final TypeReference<Map<String, Object>> DIMENSION_VALUES_TYPE =
      new TypeReference<>() {};

  private final BillableMetricUsageService billableMetricUsageService;
  private final CustomerRepository customerRepository;
  private final PriceRateRepository priceRateRepository;
  private final InvoiceRepository invoiceRepository;
  private final JsonMapper jsonMapper;

  DraftInvoiceService(
      BillableMetricUsageService billableMetricUsageService,
      CustomerRepository customerRepository,
      PriceRateRepository priceRateRepository,
      InvoiceRepository invoiceRepository,
      JsonMapper jsonMapper) {
    this.billableMetricUsageService = billableMetricUsageService;
    this.customerRepository = customerRepository;
    this.priceRateRepository = priceRateRepository;
    this.invoiceRepository = invoiceRepository;
    this.jsonMapper = jsonMapper;
  }

  @Transactional(readOnly = true)
  public DraftInvoiceResponse preview(UUID organizationId, YearMonth month) {
    OffsetDateTime calculatedAt = OffsetDateTime.now(BillableMetricUsageService.BILLING_ZONE);

    Set<UUID> finalizedCustomerIds =
        Set.copyOf(invoiceRepository.findCustomerIds(organizationId, month.toString()));
    List<Customer> organizationCustomers =
        customerRepository.findByOrganizationIdOrderByNameAscIdAsc(organizationId).stream()
            .filter(customer -> !finalizedCustomerIds.contains(customer.getId()))
            .toList();

    Map<PriceRateKey, BigDecimal> unitPriceByPriceRateKey = unitPriceByPriceRateKey(organizationId);

    Map<UUID, CustomerCharges> customerChargesByCustomerId =
        customerChargesByCustomerId(
            billableMetricUsageService.aggregate(organizationId, month), unitPriceByPriceRateKey);

    List<DraftInvoiceCustomer> draftInvoiceCustomers =
        organizationCustomers.stream()
            .map(
                customer ->
                    toDraftInvoiceCustomer(
                        customer,
                        customerChargesByCustomerId.getOrDefault(
                            customer.getId(), new CustomerCharges(List.of(), List.of()))))
            .toList();

    long totalAmount =
        draftInvoiceCustomers.stream()
            .mapToLong(DraftInvoiceCustomer::amount)
            .reduce(0L, Math::addExact);

    return new DraftInvoiceResponse(
        month.toString(), calculatedAt, totalAmount, draftInvoiceCustomers);
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

  // 단가가 있는 조합은 라인이 되고, 없는 조합은 수량이 0이 아니면 단가 없는 사용량이 된다.
  private static Map<UUID, CustomerCharges> customerChargesByCustomerId(
      List<BillableMetricUsage> billableMetricUsages,
      Map<PriceRateKey, BigDecimal> unitPriceByPriceRateKey) {
    Map<UUID, CustomerCharges> customerChargesByCustomerId = new HashMap<>();
    for (BillableMetricUsage billableMetricUsage : billableMetricUsages) {
      BillableMetric billableMetric = billableMetricUsage.billableMetric();
      for (CustomerUsage customerUsage : billableMetricUsage.customers()) {
        CustomerCharges customerCharges =
            customerChargesByCustomerId.computeIfAbsent(
                customerUsage.customerId(),
                customerId -> new CustomerCharges(new ArrayList<>(), new ArrayList<>()));
        for (DimensionUsage dimensionUsage : customerUsage.dimensionUsages()) {
          BigDecimal unitPrice =
              unitPriceByPriceRateKey.get(
                  new PriceRateKey(billableMetric.getCode(), dimensionUsage.dimensionValues()));
          if (unitPrice != null) {
            customerCharges
                .draftInvoiceLines()
                .add(toDraftInvoiceLine(billableMetric, dimensionUsage, unitPrice));
          } else if (dimensionUsage.quantity().signum() != 0) {
            customerCharges
                .unpricedUsages()
                .add(
                    new DraftInvoiceUnpricedUsage(
                        billableMetric.getCode(),
                        billableMetric.getTargetProperty(),
                        dimensionUsage.dimensionValues(),
                        dimensionUsage.quantity()));
          }
        }
      }
    }
    return customerChargesByCustomerId;
  }

  private static DraftInvoiceLine toDraftInvoiceLine(
      BillableMetric billableMetric, DimensionUsage dimensionUsage, BigDecimal unitPrice) {
    return new DraftInvoiceLine(
        billableMetric.getCode(),
        billableMetric.getTargetProperty(),
        dimensionUsage.dimensionValues(),
        dimensionUsage.quantity(),
        unitPrice,
        charge(dimensionUsage.quantity(), unitPrice));
  }

  private static DraftInvoiceCustomer toDraftInvoiceCustomer(
      Customer customer, CustomerCharges customerCharges) {
    long amount =
        customerCharges.draftInvoiceLines().stream()
            .mapToLong(DraftInvoiceLine::amount)
            .reduce(0L, Math::addExact);

    return new DraftInvoiceCustomer(
        customer.getId(),
        customer.getName(),
        amount,
        customerCharges.draftInvoiceLines(),
        customerCharges.unpricedUsages());
  }

  private static long charge(BigDecimal quantity, BigDecimal unitPrice) {
    return quantity.multiply(unitPrice).setScale(-1, RoundingMode.DOWN).longValueExact();
  }

  // 고객 한 명의 라인과 단가 없는 사용량이다.
  private record CustomerCharges(
      List<DraftInvoiceLine> draftInvoiceLines, List<DraftInvoiceUnpricedUsage> unpricedUsages) {}

  // 단가를 찾는 키다.
  private record PriceRateKey(
      String billableMetricCode, Map<String, Object> dimensionValueByDimensionProperty) {}
}
