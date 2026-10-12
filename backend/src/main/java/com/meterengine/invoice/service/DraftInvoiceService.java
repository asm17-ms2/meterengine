package com.meterengine.invoice.service;

import com.meterengine.customer.entity.Customer;
import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.invoice.dto.DraftInvoiceResponse;
import com.meterengine.invoice.dto.DraftInvoiceResponse.DraftInvoiceCustomer;
import com.meterengine.invoice.dto.DraftInvoiceResponse.DraftInvoiceLine;
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
import java.util.Optional;
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

    Map<UUID, List<DraftInvoiceLine>> draftInvoiceLinesByCustomerId =
        draftInvoiceLinesByCustomerId(
            billableMetricUsageService.aggregate(organizationId, month), unitPriceByPriceRateKey);

    List<DraftInvoiceCustomer> draftInvoiceCustomers =
        organizationCustomers.stream()
            .map(
                customer ->
                    toDraftInvoiceCustomer(
                        customer,
                        draftInvoiceLinesByCustomerId.getOrDefault(customer.getId(), List.of())))
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

  private static Map<UUID, List<DraftInvoiceLine>> draftInvoiceLinesByCustomerId(
      List<BillableMetricUsage> billableMetricUsages,
      Map<PriceRateKey, BigDecimal> unitPriceByPriceRateKey) {
    Map<UUID, List<DraftInvoiceLine>> draftInvoiceLinesByCustomerId = new HashMap<>();
    for (BillableMetricUsage billableMetricUsage : billableMetricUsages) {
      for (CustomerUsage customerUsage : billableMetricUsage.customers()) {
        draftInvoiceLinesByCustomerId
            .computeIfAbsent(customerUsage.customerId(), customerId -> new ArrayList<>())
            .addAll(
                toDraftInvoiceLines(
                    billableMetricUsage.billableMetric(), customerUsage, unitPriceByPriceRateKey));
      }
    }
    return draftInvoiceLinesByCustomerId;
  }

  // 단가가 있는 조합만 라인이 된다.
  private static List<DraftInvoiceLine> toDraftInvoiceLines(
      BillableMetric billableMetric,
      CustomerUsage customerUsage,
      Map<PriceRateKey, BigDecimal> unitPriceByPriceRateKey) {
    return customerUsage.dimensionUsages().stream()
        .flatMap(
            dimensionUsage ->
                Optional.ofNullable(
                    unitPriceByPriceRateKey.get(
                        new PriceRateKey(
                            billableMetric.getCode(), dimensionUsage.dimensionValues())))
                    .map(unitPrice -> toDraftInvoiceLine(billableMetric, dimensionUsage, unitPrice))
                    .stream())
        .toList();
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
      Customer customer, List<DraftInvoiceLine> draftInvoiceLines) {
    long amount =
        draftInvoiceLines.stream().mapToLong(DraftInvoiceLine::amount).reduce(0L, Math::addExact);

    return new DraftInvoiceCustomer(
        customer.getId(), customer.getName(), amount, draftInvoiceLines);
  }

  private static long charge(BigDecimal quantity, BigDecimal unitPrice) {
    return quantity.multiply(unitPrice).setScale(-1, RoundingMode.DOWN).longValueExact();
  }

  // 단가를 찾는 키다.
  private record PriceRateKey(
      String billableMetricCode, Map<String, Object> dimensionValueByDimensionProperty) {}
}
