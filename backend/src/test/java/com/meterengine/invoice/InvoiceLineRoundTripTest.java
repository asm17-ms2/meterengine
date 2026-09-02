package com.meterengine.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.TestcontainersConfiguration;
import com.meterengine.invoice.entity.InvoiceLine;
import com.meterengine.invoice.repository.InvoiceLineRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class InvoiceLineRoundTripTest {

  @Autowired private InvoiceLineRepository invoiceLineRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void 저장한_라인은_수량과_단가를_적은_자릿수_그대로_되읽는다() {
    UUID organizationId = insertOrganization();
    UUID invoiceId = insertInvoice(organizationId);
    UUID lineId = saveLine(organizationId, invoiceId, "token-usage", "1234.500", "0.0007").getId();
    flushAndClear();

    InvoiceLine reloadedLine = invoiceLineRepository.findById(lineId).orElseThrow();

    assertThat(reloadedLine.getQuantity()).isEqualTo(new BigDecimal("1234.500"));
    assertThat(reloadedLine.getUnitPrice()).isEqualTo(new BigDecimal("0.0007"));
  }

  @Test
  void 라인은_삽입_순서와_무관하게_미터_코드_순으로_되읽힌다() {
    UUID organizationId = insertOrganization();
    UUID invoiceId = insertInvoice(organizationId);
    saveLine(organizationId, invoiceId, "token-usage", "1200", "0.5");
    saveLine(organizationId, invoiceId, "api-call", "34", "10");
    saveLine(organizationId, invoiceId, "storage-gb", "7", "100");
    flushAndClear();

    List<InvoiceLine> reloadedLines =
        invoiceLineRepository.findByOrganizationIdAndInvoiceIdOrderByMetricCodeAsc(
            organizationId, invoiceId);

    assertThat(reloadedLines)
        .extracting(InvoiceLine::getMetricCode)
        .containsExactly("api-call", "storage-gb", "token-usage");
  }

  private UUID insertInvoice(UUID organizationId) {
    return jdbc.queryForObject(
        """
        INSERT INTO invoice
          (organization_id, customer_id, period, supply_amount, tax_amount, finalized_at)
        VALUES (?, ?, '2026-08', 12000, 1200, now())
        RETURNING id
        """,
        UUID.class,
        organizationId,
        insertCustomer(organizationId));
  }

  private InvoiceLine saveLine(
      UUID organizationId, UUID invoiceId, String metricCode, String quantity, String unitPrice) {
    InvoiceLine line =
        new InvoiceLine(
            UUID.randomUUID(),
            organizationId,
            invoiceId,
            metricCode,
            "token",
            "{}",
            new BigDecimal(quantity),
            new BigDecimal(unitPrice),
            600L);
    return invoiceLineRepository.save(line);
  }

  private void flushAndClear() {
    entityManager.flush();
    entityManager.clear();
  }

  private UUID insertOrganization() {
    return jdbc.queryForObject(
        "INSERT INTO organization (name) VALUES ('테스트 도입사') RETURNING id", UUID.class);
  }

  private UUID insertCustomer(UUID organizationId) {
    return jdbc.queryForObject(
        "INSERT INTO customer (organization_id, name) VALUES (?, 'acme') RETURNING id",
        UUID.class,
        organizationId);
  }
}
