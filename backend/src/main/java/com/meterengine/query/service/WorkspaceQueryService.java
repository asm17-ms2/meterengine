package com.meterengine.query.service;

import com.meterengine.customer.dto.CustomerResponse;
import com.meterengine.customer.service.CustomerService;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ForbiddenException;
import com.meterengine.global.security.BusinessEnvironmentContext;
import com.meterengine.invoice.dto.DraftInvoiceResponse;
import com.meterengine.invoice.dto.DraftInvoiceResponse.DraftInvoiceCustomer;
import com.meterengine.invoice.dto.InvoiceResponse;
import com.meterengine.invoice.service.DraftInvoiceService;
import com.meterengine.invoice.service.InvoiceService;
import com.meterengine.metric.dto.BillableMetricResponse;
import com.meterengine.metric.dto.BillableMetricUsageResponse;
import com.meterengine.metric.dto.BillableMetricUsageResponse.BillableMetricUsageCustomer;
import com.meterengine.metric.service.BillableMetricUsageService;
import com.meterengine.payment.dto.InvoicePaymentResponse;
import com.meterengine.payment.service.InvoicePaymentService;
import com.meterengine.payment.service.PaymentSettingService;
import com.meterengine.query.dto.InvoiceSearchResponse;
import com.meterengine.query.dto.OptionResponse;
import com.meterengine.query.dto.PriceSearchResponse;
import com.meterengine.query.dto.SearchPageResponse;
import com.meterengine.query.dto.UsageCustomerResponse;
import com.meterengine.query.repository.WorkspaceQueryRepository;
import java.text.Collator;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(
    readOnly = true,
    isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
public class WorkspaceQueryService {
  private static final Set<String> PAYMENT_STATUSES =
      Set.of(
          "all",
          "not_required",
          "unpaid",
          "pending",
          "paid",
          "failed",
          "partially_canceled",
          "canceled");
  private final WorkspaceQueryRepository workspaceQueryRepository;
  private final CustomerService customerService;
  private final BillableMetricUsageService billableMetricUsageService;
  private final DraftInvoiceService draftInvoiceService;
  private final InvoiceService invoiceService;
  private final InvoicePaymentService invoicePaymentService;
  private final PaymentSettingService paymentSettingService;

  WorkspaceQueryService(
      WorkspaceQueryRepository workspaceQueryRepository,
      CustomerService customerService,
      BillableMetricUsageService billableMetricUsageService,
      DraftInvoiceService draftInvoiceService,
      InvoiceService invoiceService,
      InvoicePaymentService invoicePaymentService,
      PaymentSettingService paymentSettingService) {
    this.workspaceQueryRepository = workspaceQueryRepository;
    this.customerService = customerService;
    this.billableMetricUsageService = billableMetricUsageService;
    this.draftInvoiceService = draftInvoiceService;
    this.invoiceService = invoiceService;
    this.invoicePaymentService = invoicePaymentService;
    this.paymentSettingService = paymentSettingService;
  }

  public SearchPageResponse<CustomerResponse> search(UUID organizationId, SearchParameters search) {
    return workspaceQueryRepository.customers(organizationId, search);
  }

  public SearchPageResponse<BillableMetricResponse> metrics(
      UUID organizationId, SearchParameters search) {
    return workspaceQueryRepository.metrics(organizationId, search);
  }

  public SearchPageResponse<PriceSearchResponse> prices(
      UUID organizationId, SearchParameters search) {
    if (search.customerId() != null) customerService.get(organizationId, search.customerId());
    return workspaceQueryRepository.prices(organizationId, search);
  }

  public SearchPageResponse<OptionResponse> suggest(
      UUID organizationId, SearchParameters search, String resource) {
    return switch (resource) {
      case "customers" ->
          options(
              search(organizationId, search),
              customer ->
                  new OptionResponse(
                      customer.id().toString(), customer.name(), customer.archivedAt() != null));
      case "metrics" ->
          options(
              metrics(organizationId, search),
              metric ->
                  new OptionResponse(metric.code(), metric.name(), metric.archivedAt() != null));
      default -> throw new IllegalArgumentException("Unknown option resource");
    };
  }

  private static <T> SearchPageResponse<OptionResponse> options(
      SearchPageResponse<T> found, Function<T, OptionResponse> convert) {
    return new SearchPageResponse<>(
        found.items().stream().map(convert).toList(),
        found.total(),
        found.totalUnfiltered(),
        found.page(),
        found.size(),
        found.summary());
  }

  public Map<String, List<String>> list(UUID organizationId, String resource) {
    return Map.of("months", workspaceQueryRepository.months(organizationId, resource));
  }

  public SearchPageResponse<UsageCustomerResponse> usage(
      UUID organizationId, SearchParameters search) {
    if (search.customerId() != null) customerService.get(organizationId, search.customerId());
    YearMonth month = search.monthOrCurrent();
    List<BillableMetricUsageResponse> metrics =
        billableMetricUsageService.aggregate(organizationId, month).stream()
            .map(BillableMetricUsageResponse::from)
            .filter(
                metric -> search.metricCode() == null || search.metricCode().equals(metric.code()))
            .toList();
    List<CustomerResponse> customers =
        customerService.list(organizationId, "all").stream().map(CustomerResponse::from).toList();
    Map<UUID, Map<String, BillableMetricUsageCustomer>> quantities = new LinkedHashMap<>();
    metrics.forEach(
        metric ->
            metric
                .customers()
                .forEach(
                    customer ->
                        quantities
                            .computeIfAbsent(
                                customer.customerId(), ignored -> new LinkedHashMap<>())
                            .put(metric.code(), customer)));
    var status =
        com.meterengine.global.validation.ArchiveStatus.parse(
            search.status() == null ? "all" : search.status());
    List<UsageCustomerResponse> filtered = new ArrayList<>();
    for (CustomerResponse customer : customers) {
      if (search.customerId() != null && !search.customerId().equals(customer.id())) continue;
      if (!search.matches(customer.name(), customer.externalId(), customer.id().toString()))
        continue;
      if (!status.includes(customer.archivedAt())) continue;
      Map<String, BillableMetricUsageCustomer> values =
          quantities.getOrDefault(customer.id(), Map.of());
      filtered.add(
          new UsageCustomerResponse(
              customer.id(),
              customer.name(),
              metrics.stream()
                  .filter(metric -> values.containsKey(metric.code()))
                  .map(
                      metric ->
                          new BillableMetricUsageResponse(
                              metric.code(),
                              metric.name(),
                              metric.eventType(),
                              metric.aggregation(),
                              metric.targetProperty(),
                              List.of(values.get(metric.code())),
                              metric.unitSize(),
                              metric.roundingMode(),
                              metric.roundingPrecision(),
                              metric.percentile()))
                  .toList()));
    }
    String sort =
        search.sortOr("customer_name", Set.of("customer_name", "customer_id", "line_count"));
    Comparator<UsageCustomerResponse> ordering =
        switch (sort) {
          case "customer_id" -> Comparator.comparing(value -> value.customerId().toString());
          case "line_count" -> Comparator.comparingLong(WorkspaceQueryService::lines);
          default -> Comparator.comparing(UsageCustomerResponse::customerName, korean());
        };
    if (search.descending()) ordering = ordering.reversed();
    filtered.sort(ordering.thenComparing(value -> value.customerId().toString()));
    long nonzero =
        filtered.stream()
            .filter(
                customer ->
                    customer.metrics().stream()
                        .anyMatch(metric -> metric.customers().getFirst().quantity().signum() != 0))
            .count();
    return page(
        filtered,
        customers.size(),
        search,
        Map.of(
            "month",
            month.toString(),
            "customer_count",
            filtered.size(),
            "metric_count",
            metrics.size(),
            "line_count",
            filtered.stream().mapToLong(WorkspaceQueryService::lines).sum(),
            "nonzero_customer_count",
            nonzero));
  }

  private static long lines(UsageCustomerResponse customer) {
    return customer.metrics().stream()
        .mapToLong(metric -> Math.max(1, metric.customers().getFirst().dimensionUsages().size()))
        .sum();
  }

  public SearchPageResponse<DraftInvoiceCustomer> drafts(
      UUID organizationId, SearchParameters search) {
    if (search.customerId() != null) customerService.get(organizationId, search.customerId());
    DraftInvoiceResponse draft =
        draftInvoiceService.preview(organizationId, search.monthOrCurrent());
    Map<UUID, CustomerResponse> customers =
        customerService.list(organizationId, "all").stream()
            .map(CustomerResponse::from)
            .collect(Collectors.toMap(CustomerResponse::id, Function.identity()));
    List<DraftInvoiceCustomer> selected =
        draft.customers().stream()
            .filter(
                customer ->
                    search.customerId() == null
                        || search.customerId().equals(customer.customerId()))
            .filter(
                customer ->
                    search.matches(
                        customer.customerName(),
                        customer.customerId().toString(),
                        customers.containsKey(customer.customerId())
                            ? customers.get(customer.customerId()).externalId()
                            : null))
            .map(customer -> filterMetric(customer, search.metricCode()))
            .filter(
                customer -> search.minAmount() == null || customer.amount() >= search.minAmount())
            .filter(
                customer -> search.maxAmount() == null || customer.amount() <= search.maxAmount())
            .collect(Collectors.toCollection(ArrayList::new));
    String sort =
        search.sortOr(
            "customer_name", Set.of("customer_name", "customer_id", "amount", "line_count"));
    Comparator<DraftInvoiceCustomer> ordering =
        switch (sort) {
          case "customer_id" -> Comparator.comparing(value -> value.customerId().toString());
          case "amount" -> Comparator.comparingLong(DraftInvoiceCustomer::amount);
          case "line_count" -> Comparator.comparingInt(value -> value.lines().size());
          default -> Comparator.comparing(DraftInvoiceCustomer::customerName, korean());
        };
    if (search.descending()) ordering = ordering.reversed();
    selected.sort(ordering.thenComparing(value -> value.customerId().toString()));
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("month", draft.month());
    summary.put("calculated_at", draft.calculatedAt());
    summary.put("amount", sum(selected, DraftInvoiceCustomer::amount));
    summary.put("customer_count", selected.size());
    summary.put(
        "billable_customer_count",
        selected.stream().filter(customer -> customer.amount() != 0).count());
    summary.put(
        "line_count", selected.stream().mapToLong(customer -> customer.lines().size()).sum());
    summary.put(
        "unpriced_count",
        selected.stream().mapToLong(customer -> customer.unpricedUsages().size()).sum());
    summary.put(
        "negative_count",
        selected.stream().mapToLong(customer -> customer.negativeUsages().size()).sum());
    summary.put("month_amount", draft.totalAmount());
    summary.put("month_customer_count", draft.customers().size());
    summary.put(
        "month_billable_customer_count",
        draft.customers().stream().filter(customer -> customer.amount() != 0).count());
    summary.put(
        "month_unpriced_count",
        draft.customers().stream().mapToLong(customer -> customer.unpricedUsages().size()).sum());
    summary.put(
        "month_negative_count",
        draft.customers().stream().mapToLong(customer -> customer.negativeUsages().size()).sum());
    summary.put(
        "month_line_count",
        draft.customers().stream().mapToLong(customer -> customer.lines().size()).sum());
    summary.put("finalization_scope", "month");
    return page(selected, draft.customers().size(), search, summary);
  }

  public SearchPageResponse<InvoiceSearchResponse> invoices(
      UUID organizationId, SearchParameters search) {
    if (search.status() != null && !PAYMENT_STATUSES.contains(search.status()))
      throw SearchParameters.invalid("status", "지원하지 않는 결제 상태입니다");
    String environmentMode = BusinessEnvironmentContext.mode();
    if (environmentMode != null && search.mode() != null && !environmentMode.equals(search.mode()))
      throw new ForbiddenException(ErrorCode.ENVIRONMENT_MISMATCH);
    String mode =
        environmentMode != null
            ? environmentMode
            : search.mode() == null
                ? paymentSettingService.get(organizationId).mode()
                : search.mode();
    if (search.customerId() != null) customerService.get(organizationId, search.customerId());
    List<InvoiceResponse> invoices =
        invoiceService.list(organizationId, search.customerId(), search.month()).invoices();
    Map<UUID, InvoicePaymentResponse> payments =
        invoicePaymentService
            .list(organizationId, search.month(), search.customerId(), mode)
            .invoicePayments()
            .stream()
            .collect(Collectors.toMap(InvoicePaymentResponse::invoiceId, Function.identity()));
    List<InvoiceSearchResponse> selected =
        invoices.stream()
            .filter(
                invoice ->
                    search.customerId() == null || search.customerId().equals(invoice.customerId()))
            .filter(
                invoice ->
                    search.month() == null || search.month().toString().equals(invoice.month()))
            .filter(
                invoice ->
                    search.fromAt() == null || !invoice.finalizedAt().isBefore(search.fromAt()))
            .filter(
                invoice -> search.toAt() == null || invoice.finalizedAt().isBefore(search.toAt()))
            .filter(
                invoice ->
                    search.minAmount() == null || invoice.totalAmount() >= search.minAmount())
            .filter(
                invoice ->
                    search.maxAmount() == null || invoice.totalAmount() <= search.maxAmount())
            .filter(
                invoice ->
                    search.metricCode() == null
                        || invoice.lines().stream()
                            .anyMatch(
                                line -> search.metricCode().equals(line.billableMetricCode())))
            .filter(
                invoice ->
                    search.matches(
                        invoice.invoiceNumber(),
                        invoice.customerName(),
                        invoice.customerId().toString()))
            .map(invoice -> new InvoiceSearchResponse(invoice, payments.get(invoice.id())))
            .filter(
                value ->
                    search.status() == null
                        || search.status().equals("all")
                        || value.payment() != null
                            && search.status().equals(value.payment().status()))
            .collect(Collectors.toCollection(ArrayList::new));
    String sort =
        search.sortOr(
            "month",
            Set.of(
                "month",
                "customer_name",
                "total_amount",
                "finalized_at",
                "invoice_number",
                "status"));
    Comparator<InvoiceSearchResponse> ordering =
        switch (sort) {
          case "customer_name" ->
              Comparator.comparing(value -> value.invoice().customerName(), korean());
          case "total_amount" -> Comparator.comparingLong(value -> value.invoice().totalAmount());
          case "finalized_at" -> Comparator.comparing(value -> value.invoice().finalizedAt());
          case "invoice_number" -> Comparator.comparing(value -> value.invoice().invoiceNumber());
          case "status" ->
              Comparator.comparing(
                  value -> value.payment() == null ? "unpaid" : value.payment().status());
          default -> Comparator.comparing(value -> value.invoice().month());
        };
    if (search.descending()) ordering = ordering.reversed();
    selected.sort(
        ordering
            .thenComparing(value -> value.invoice().customerName(), korean())
            .thenComparing(value -> value.invoice().id().toString()));
    Map<String, Object> summary = new LinkedHashMap<>();
    summary.put("mode", mode);
    summary.put("invoice_count", selected.size());
    summary.put("supply_amount", sum(selected, value -> value.invoice().supplyAmount()));
    summary.put("tax_amount", sum(selected, value -> value.invoice().taxAmount()));
    summary.put("total_amount", sum(selected, value -> value.invoice().totalAmount()));
    summary.put(
        "canceled_amount",
        sum(selected, value -> value.payment() == null ? 0 : value.payment().canceledAmount()));
    boolean pending =
        selected.stream()
            .anyMatch(
                value -> value.payment() != null && value.payment().status().equals("pending"));
    summary.put("has_pending", pending);
    summary.put(
        "outstanding_amount",
        pending
            ? null
            : sum(
                selected,
                value ->
                    value.payment() == null
                            || Set.of("unpaid", "failed").contains(value.payment().status())
                        ? value.invoice().totalAmount()
                        : 0));
    summary.put(
        "status_counts",
        selected.stream()
            .collect(
                Collectors.groupingBy(
                    value -> value.payment() == null ? "unpaid" : value.payment().status(),
                    LinkedHashMap::new,
                    Collectors.counting())));
    return page(selected, invoices.size(), search, summary);
  }

  private static DraftInvoiceCustomer filterMetric(DraftInvoiceCustomer customer, String code) {
    if (code == null) return customer;
    var lines =
        customer.lines().stream().filter(line -> code.equals(line.billableMetricCode())).toList();
    return new DraftInvoiceCustomer(
        customer.customerId(),
        customer.customerName(),
        sum(lines, DraftInvoiceResponse.DraftInvoiceLine::amount),
        lines,
        customer.unpricedUsages().stream()
            .filter(usage -> code.equals(usage.billableMetricCode()))
            .toList(),
        customer.negativeUsages().stream()
            .filter(usage -> code.equals(usage.billableMetricCode()))
            .toList());
  }

  private static <T> SearchPageResponse<T> page(
      List<T> values, long unfiltered, SearchParameters parameters, Map<String, Object> summary) {
    return new SearchPageResponse<>(
        parameters.page(values),
        values.size(),
        unfiltered,
        parameters.page(),
        parameters.size(),
        summary);
  }

  private static Comparator<String> korean() {
    Collator collator = Collator.getInstance(Locale.KOREAN);
    return collator::compare;
  }

  private static <T> long sum(List<T> values, java.util.function.ToLongFunction<T> amount) {
    return values.stream().mapToLong(amount).reduce(0L, Math::addExact);
  }
}
