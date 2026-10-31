package com.meterengine.invoice.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.meterengine.SideEffectTestSupport;
import com.meterengine.global.database.DatabaseScope;
import com.meterengine.global.database.DatabaseScopes;
import com.meterengine.global.security.OrganizationEnvironments;
import com.meterengine.invoice.dto.DraftInvoiceResponse;
import com.meterengine.invoice.dto.DraftInvoiceResponse.DraftInvoiceCustomer;
import com.meterengine.invoice.entity.Invoice;
import com.meterengine.invoice.repository.InvoiceRepository;
import com.meterengine.organization.repository.OrganizationRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class InvoiceFinalizationBacklogSchemaIntegrationTest extends SideEffectTestSupport {
  @Autowired OrganizationRepository organizations;
  @Autowired OrganizationEnvironments environments;
  @Autowired InvoiceRepository invoices;

  @Test
  void 각_슬롯은_현재_schema만_읽고_한쪽_확정이_반대쪽_캐시와_게이지를_지우지_않는다() {
    ownerJdbc()
        .update(
            "UPDATE shared.organization SET created_at=? WHERE id=?",
            OffsetDateTime.parse("2020-01-01T00:00:00Z"),
            organizationId);
    UUID customer = UUID.randomUUID();
    createCustomer("test", customer, "same-customer");
    createCustomer("live", customer, "same-customer");
    List<String> observed = new ArrayList<>();
    var draft = mock(DraftInvoiceService.class);
    when(draft.preview(any(), any()))
        .thenAnswer(
            invocation -> {
              String schema = jdbc.queryForObject("SELECT current_schema()", String.class);
              assertThat(jdbc.queryForObject("SELECT current_user", String.class))
                  .isEqualTo("meterengine_" + schema);
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
              assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
              observed.add(schema);
              boolean august = YearMonth.of(2026, 8).equals(invocation.getArgument(1));
              return new DraftInvoiceResponse(
                  "2026-08",
                  OffsetDateTime.parse("2026-10-30T01:00:00Z"),
                  august ? 1000 : 0,
                  august
                      ? List.of(
                          new DraftInvoiceCustomer(
                              customer, "fixture", 1000, List.of(), List.of(), List.of()))
                      : List.of());
            });
    var registry = new SimpleMeterRegistry();
    var service =
        new InvoiceFinalizationBacklogService(
            organizations,
            environments,
            draft,
            invoices,
            registry,
            Clock.fixed(Instant.parse("2026-10-30T01:00:00Z"), ZoneOffset.UTC),
            transactions);

    assertThat(in("test", service::measure).processedCount()).isEqualTo(12);
    assertThat(observed).hasSize(12).containsOnly("test");
    assertThat(
            registry
                .find("meterengine.invoice.finalization.backlog")
                .tag("environment", "live")
                .gauge())
        .isNull();
    assertThat(in("live", service::measure).processedCount()).isEqualTo(12);
    assertThat(observed.subList(12, 24)).containsOnly("live");
    assertThat(value(registry, "test")).isEqualTo(1);
    assertThat(value(registry, "live")).isEqualTo(1);

    transactions.write(
        DatabaseScope.TEST,
        () ->
            invoices.saveAndFlush(
                new Invoice(
                    UUID.randomUUID(),
                    organizationId,
                    customer,
                    "fixture",
                    "2026-08",
                    1000,
                    100,
                    OffsetDateTime.parse("2026-09-01T05:00:00Z"))));
    assertThat(in("test", service::measure).skippedCount()).isEqualTo(12);
    assertThat(value(registry, "test")).isZero();
    assertThat(value(registry, "live")).isEqualTo(1);
    assertThat(in("live", service::measure).skippedCount()).isEqualTo(12);
    assertThat(value(registry, "test")).isZero();
    assertThat(value(registry, "live")).isEqualTo(1);
    assertThat(observed).hasSize(24);
    assertThat(DatabaseScopes.current()).isEqualTo(DatabaseScope.SHARED);
  }

  private static double value(SimpleMeterRegistry registry, String mode) {
    return registry
        .get("meterengine.invoice.finalization.backlog")
        .tag("environment", mode)
        .gauge()
        .value();
  }
}
