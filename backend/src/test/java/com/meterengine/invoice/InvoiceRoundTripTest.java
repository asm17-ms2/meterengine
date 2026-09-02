package com.meterengine.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.TestcontainersConfiguration;
import com.meterengine.invoice.entity.Invoice;
import com.meterengine.invoice.repository.InvoiceRepository;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
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
class InvoiceRoundTripTest {

  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void 저장한_인보이스는_기간과_금액과_확정_시각이_그대로_되읽힌다() {
    UUID organizationId = insertOrganization();
    OffsetDateTime finalizedAt = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
    UUID invoiceId = UUID.randomUUID();
    invoiceRepository.save(
        new Invoice(
            invoiceId,
            organizationId,
            insertCustomer(organizationId),
            "2026-08",
            12000L,
            1200L,
            finalizedAt));
    entityManager.flush();
    entityManager.clear();

    Invoice reloadedInvoice = invoiceRepository.findById(invoiceId).orElseThrow();

    assertThat(reloadedInvoice.getPeriod()).isEqualTo("2026-08");
    assertThat(reloadedInvoice.getSupplyAmount()).isEqualTo(12000L);
    assertThat(reloadedInvoice.getTaxAmount()).isEqualTo(1200L);
    assertThat(reloadedInvoice.getFinalizedAt()).isAtSameInstantAs(finalizedAt);
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
