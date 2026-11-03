package com.meterengine.query.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.meterengine.customer.service.CustomerService;
import com.meterengine.invoice.dto.DraftInvoiceResponse;
import com.meterengine.invoice.dto.DraftInvoiceResponse.DraftInvoiceCustomer;
import com.meterengine.invoice.dto.DraftInvoiceResponse.DraftInvoiceLine;
import com.meterengine.invoice.dto.DraftInvoiceResponse.DraftInvoiceUnpricedUsage;
import com.meterengine.invoice.service.DraftInvoiceService;
import com.meterengine.invoice.service.InvoiceService;
import com.meterengine.metric.service.BillableMetricUsageService;
import com.meterengine.payment.service.InvoicePaymentService;
import com.meterengine.payment.service.PaymentSettingService;
import com.meterengine.query.repository.WorkspaceQueryRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkspaceQueryServiceTest {

  private static final UUID ORGANIZATION_ID = UUID.randomUUID();

  @Mock private WorkspaceQueryRepository workspaceQueryRepository;
  @Mock private CustomerService customerService;
  @Mock private BillableMetricUsageService billableMetricUsageService;
  @Mock private DraftInvoiceService draftInvoiceService;
  @Mock private InvoiceService invoiceService;
  @Mock private InvoicePaymentService invoicePaymentService;
  @Mock private PaymentSettingService paymentSettingService;

  private WorkspaceQueryService workspaceQueryService;

  @BeforeEach
  void setUp() {
    workspaceQueryService =
        new WorkspaceQueryService(
            workspaceQueryRepository,
            customerService,
            billableMetricUsageService,
            draftInvoiceService,
            invoiceService,
            invoicePaymentService,
            paymentSettingService);
  }

  @Test
  void 미터_조건으로_거른_청구_예정액도_다른_미터의_단가_없는_사용량으로_정한_자동_확정_보류를_유지한다() {
    DraftInvoiceCustomer mixed =
        new DraftInvoiceCustomer(
            UUID.randomUUID(),
            "가 고객",
            100,
            List.of(
                new DraftInvoiceLine(
                    "tokens",
                    "token",
                    Map.of(),
                    BigDecimal.TEN,
                    BigDecimal.TEN,
                    100,
                    "per_unit",
                    0,
                    BigDecimal.TEN,
                    BigDecimal.ONE)),
            List.of(new DraftInvoiceUnpricedUsage("api_calls", "count", Map.of(), BigDecimal.ONE)),
            List.of(),
            true);
    when(draftInvoiceService.preview(ORGANIZATION_ID, YearMonth.of(2026, 9)))
        .thenReturn(
            new DraftInvoiceResponse(
                "2026-09",
                OffsetDateTime.parse("2026-10-30T10:00:00+09:00"),
                true,
                null,
                false,
                100,
                List.of(mixed)));

    DraftInvoiceCustomer item =
        workspaceQueryService
            .drafts(
                ORGANIZATION_ID,
                SearchParameters.parse(
                    Map.of("month", "2026-09", "billable_metric_code", "tokens"), "drafts"))
            .items()
            .getFirst();

    assertThat(item.lines()).hasSize(1);
    assertThat(item.unpricedUsages()).isEmpty();
    assertThat(item.autoFinalizeHeld()).isTrue();
  }
}
