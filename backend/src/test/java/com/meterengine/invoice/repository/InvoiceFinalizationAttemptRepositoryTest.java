package com.meterengine.invoice.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.meterengine.TestcontainersConfiguration;
import com.meterengine.invoice.entity.InvoiceFinalizationAttempt;
import com.meterengine.invoice.repository.InvoiceFinalizationAttemptRepository.OpenAttempt;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
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
class InvoiceFinalizationAttemptRepositoryTest {

  private static final String AUGUST = "2026-08";
  private static final String SEPTEMBER = "2026-09";
  private static final OffsetDateTime SEPTEMBER_CUTOFF_AT =
      OffsetDateTime.parse("2026-10-01T13:00:00+09:00");
  private static final OffsetDateTime OCTOBER_1_AT_14 =
      OffsetDateTime.parse("2026-10-01T14:00:00+09:00");
  private static final OffsetDateTime OCTOBER_1_AT_15 =
      OffsetDateTime.parse("2026-10-01T15:00:00+09:00");
  private static final String UNPRICED_USAGES =
      """
      [{"billable_metric_code": "tokens", "target_property": "token",\
       "dimension_values": {"model": "new"}, "quantity": 500}]""";

  @Autowired private InvoiceFinalizationAttemptRepository invoiceFinalizationAttemptRepository;
  @Autowired private EntityManager entityManager;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void 저장한_시도는_환경과_결과와_사유와_단가_없는_사용량을_그대로_되읽는다() {
    UUID organizationId = insertOrganization();
    UUID testEnvironmentId = environmentId(organizationId, "test");
    UUID customerId = insertCustomer(organizationId, testEnvironmentId, "가");
    InvoiceFinalizationAttempt attempt =
        new InvoiceFinalizationAttempt(
            organizationId,
            testEnvironmentId,
            customerId,
            SEPTEMBER,
            InvoiceFinalizationAttempt.HELD,
            "단가 없는 사용량: tokens {model=new} 500",
            UNPRICED_USAGES,
            OCTOBER_1_AT_14);
    invoiceFinalizationAttemptRepository.saveAndFlush(attempt);
    entityManager.clear();

    InvoiceFinalizationAttempt reloaded =
        invoiceFinalizationAttemptRepository.findById(attempt.getId()).orElseThrow();

    assertThat(reloaded.getOrganizationId()).isEqualTo(organizationId);
    assertThat(reloaded.getEnvironmentId()).isEqualTo(testEnvironmentId);
    assertThat(reloaded.getCustomerId()).isEqualTo(customerId);
    assertThat(reloaded.getPeriod()).isEqualTo(SEPTEMBER);
    assertThat(reloaded.getResult()).isEqualTo(InvoiceFinalizationAttempt.HELD);
    assertThat(reloaded.getReason()).isEqualTo("단가 없는 사용량: tokens {model=new} 500");
    assertThat(reloaded.getUnpricedUsages()).contains("\"model\": \"new\"");
    assertThat(reloaded.getAttemptedAt()).isAtSameInstantAs(OCTOBER_1_AT_14);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT environment_scope_id FROM invoice_finalization_attempt WHERE id = ?",
                UUID.class,
                attempt.getId()))
        .isEqualTo(testEnvironmentId);
  }

  @Test
  void 사유가_2000자를_넘으면_앞의_2000자만_남긴다() {
    UUID organizationId = insertOrganization();
    UUID customerId = insertCustomer(organizationId, null, "가");
    InvoiceFinalizationAttempt attempt =
        new InvoiceFinalizationAttempt(
            organizationId,
            null,
            customerId,
            SEPTEMBER,
            InvoiceFinalizationAttempt.FAILED,
            "가".repeat(2001),
            null,
            OCTOBER_1_AT_14);
    invoiceFinalizationAttemptRepository.saveAndFlush(attempt);
    entityManager.clear();

    assertThat(
            invoiceFinalizationAttemptRepository
                .findById(attempt.getId())
                .orElseThrow()
                .getReason())
        .isEqualTo("가".repeat(2000));
  }

  @Test
  void 그_달_후보는_마감_전에_등록됐고_그_달에_끝난_시도가_없는_고객이다() {
    UUID organizationId = insertOrganization();
    UUID finished = insertCustomer(organizationId, null, "가");
    UUID held = insertCustomer(organizationId, null, "나");
    UUID failed = insertCustomer(organizationId, null, "다");
    UUID finishedInAugustOnly = insertCustomer(organizationId, null, "라");
    UUID untouched = insertCustomer(organizationId, null, "마");
    insertCustomer(organizationId, null, "바", SEPTEMBER_CUTOFF_AT);
    UUID otherOrganizationId = insertOrganization();
    insertCustomer(otherOrganizationId, null, "사");
    save(organizationId, null, finished, SEPTEMBER, InvoiceFinalizationAttempt.HELD);
    save(organizationId, null, finished, SEPTEMBER, InvoiceFinalizationAttempt.ZERO_AMOUNT);
    save(organizationId, null, held, SEPTEMBER, InvoiceFinalizationAttempt.HELD);
    save(organizationId, null, failed, SEPTEMBER, InvoiceFinalizationAttempt.FAILED);
    save(organizationId, null, finishedInAugustOnly, AUGUST, InvoiceFinalizationAttempt.FINALIZED);

    assertThat(candidateIds(organizationId, null))
        .containsExactly(held, failed, finishedInAugustOnly, untouched);
  }

  @Test
  void 열린_시도는_끝난_시도도_인보이스도_없는_고객과_달마다_마지막_시도다() {
    UUID organizationId = insertOrganization();
    UUID heldThenFailed = insertCustomer(organizationId, null, "가");
    UUID failedThenFinalized = insertCustomer(organizationId, null, "나");
    UUID failedWithInvoice = insertCustomer(organizationId, null, "다");
    UUID heldTwoMonths = insertCustomer(organizationId, null, "라");
    UUID otherOrganizationId = insertOrganization();
    save(
        otherOrganizationId,
        null,
        insertCustomer(otherOrganizationId, null, "마"),
        SEPTEMBER,
        InvoiceFinalizationAttempt.FAILED);
    save(organizationId, null, heldThenFailed, SEPTEMBER, InvoiceFinalizationAttempt.HELD);
    save(
        organizationId,
        null,
        heldThenFailed,
        SEPTEMBER,
        InvoiceFinalizationAttempt.FAILED,
        OCTOBER_1_AT_15);
    save(organizationId, null, failedThenFinalized, SEPTEMBER, InvoiceFinalizationAttempt.FAILED);
    save(
        organizationId,
        null,
        failedThenFinalized,
        SEPTEMBER,
        InvoiceFinalizationAttempt.FINALIZED,
        OCTOBER_1_AT_15);
    save(organizationId, null, failedWithInvoice, SEPTEMBER, InvoiceFinalizationAttempt.FAILED);
    insertInvoice(organizationId, null, failedWithInvoice, SEPTEMBER);
    save(organizationId, null, heldTwoMonths, AUGUST, InvoiceFinalizationAttempt.HELD);
    save(organizationId, null, heldTwoMonths, SEPTEMBER, InvoiceFinalizationAttempt.HELD);

    List<OpenAttempt> openAttempts =
        invoiceFinalizationAttemptRepository.findOpenAttempts(organizationId, null);

    assertThat(openAttempts)
        .extracting(OpenAttempt::getCustomerId, OpenAttempt::getPeriod, OpenAttempt::getResult)
        .containsExactlyInAnyOrder(
            tuple(heldThenFailed, SEPTEMBER, InvoiceFinalizationAttempt.FAILED),
            tuple(heldTwoMonths, AUGUST, InvoiceFinalizationAttempt.HELD),
            tuple(heldTwoMonths, SEPTEMBER, InvoiceFinalizationAttempt.HELD));
  }

  @Test
  void 마지막_시도가_실패인_고객이_있을_때만_그_달에_열린_실패가_있다() {
    UUID organizationId = insertOrganization();
    UUID failedThenHeld = insertCustomer(organizationId, null, "가");
    save(organizationId, null, failedThenHeld, AUGUST, InvoiceFinalizationAttempt.FAILED);
    save(
        organizationId,
        null,
        failedThenHeld,
        AUGUST,
        InvoiceFinalizationAttempt.HELD,
        OCTOBER_1_AT_15);
    save(organizationId, null, failedThenHeld, SEPTEMBER, InvoiceFinalizationAttempt.FAILED);

    assertThat(invoiceFinalizationAttemptRepository.existsOpenFailure(organizationId, null, AUGUST))
        .isFalse();
    assertThat(
            invoiceFinalizationAttemptRepository.existsOpenFailure(organizationId, null, SEPTEMBER))
        .isTrue();
  }

  @Test
  void 끝난_시도나_인보이스가_있는_고객의_실패는_열린_실패가_아니다() {
    UUID organizationId = insertOrganization();
    UUID failedThenSkipped = insertCustomer(organizationId, null, "가");
    UUID failedWithInvoice = insertCustomer(organizationId, null, "나");
    save(organizationId, null, failedThenSkipped, SEPTEMBER, InvoiceFinalizationAttempt.FAILED);
    save(
        organizationId,
        null,
        failedThenSkipped,
        SEPTEMBER,
        InvoiceFinalizationAttempt.SKIPPED,
        OCTOBER_1_AT_15);
    save(organizationId, null, failedWithInvoice, SEPTEMBER, InvoiceFinalizationAttempt.FAILED);
    insertInvoice(organizationId, null, failedWithInvoice, SEPTEMBER);

    assertThat(
            invoiceFinalizationAttemptRepository.existsOpenFailure(organizationId, null, SEPTEMBER))
        .isFalse();
  }

  @Test
  void 다_훑은_달_표시는_환경_범위와_달마다_한_번만_남는다() {
    UUID organizationId = insertOrganization();
    UUID testEnvironmentId = environmentId(organizationId, "test");

    int first =
        invoiceFinalizationAttemptRepository.insertSweptIfAbsent(
            organizationId, null, SEPTEMBER, OCTOBER_1_AT_14);
    int again =
        invoiceFinalizationAttemptRepository.insertSweptIfAbsent(
            organizationId, null, SEPTEMBER, OCTOBER_1_AT_15);
    int otherMonth =
        invoiceFinalizationAttemptRepository.insertSweptIfAbsent(
            organizationId, null, AUGUST, OCTOBER_1_AT_14);
    int otherEnvironment =
        invoiceFinalizationAttemptRepository.insertSweptIfAbsent(
            organizationId, testEnvironmentId, SEPTEMBER, OCTOBER_1_AT_14);

    assertThat(List.of(first, again, otherMonth, otherEnvironment)).containsExactly(1, 0, 1, 1);
    assertThat(invoiceFinalizationAttemptRepository.findSweptPeriods(organizationId, null))
        .containsExactlyInAnyOrder(AUGUST, SEPTEMBER);
    assertThat(
            invoiceFinalizationAttemptRepository.findSweptPeriods(
                organizationId, testEnvironmentId))
        .containsExactly(SEPTEMBER);
    assertThat(
            invoiceFinalizationAttemptRepository.existsSweptPeriod(
                organizationId, testEnvironmentId, AUGUST))
        .isFalse();
    assertThat(
            invoiceFinalizationAttemptRepository.existsSweptPeriod(
                organizationId, testEnvironmentId, SEPTEMBER))
        .isTrue();
  }

  @Test
  void 후보와_열린_시도와_열린_실패는_환경_범위마다_따로_본다() {
    UUID organizationId = insertOrganization();
    UUID testEnvironmentId = environmentId(organizationId, "test");
    UUID liveEnvironmentId = environmentId(organizationId, "live");
    UUID legacyCustomer = insertCustomer(organizationId, null, "가");
    UUID testCustomer = insertCustomer(organizationId, testEnvironmentId, "나");
    UUID liveCustomer = insertCustomer(organizationId, liveEnvironmentId, "다");
    save(organizationId, null, legacyCustomer, SEPTEMBER, InvoiceFinalizationAttempt.FAILED);
    save(
        organizationId,
        testEnvironmentId,
        testCustomer,
        SEPTEMBER,
        InvoiceFinalizationAttempt.FAILED);

    assertThat(candidateIds(organizationId, null)).containsExactly(legacyCustomer);
    assertThat(candidateIds(organizationId, testEnvironmentId)).containsExactly(testCustomer);
    assertThat(candidateIds(organizationId, liveEnvironmentId)).containsExactly(liveCustomer);
    assertThat(openAttemptCustomerIds(organizationId, null)).containsExactly(legacyCustomer);
    assertThat(openAttemptCustomerIds(organizationId, testEnvironmentId))
        .containsExactly(testCustomer);
    assertThat(openAttemptCustomerIds(organizationId, liveEnvironmentId)).isEmpty();
    assertThat(
            invoiceFinalizationAttemptRepository.existsOpenFailure(
                organizationId, liveEnvironmentId, SEPTEMBER))
        .isFalse();
    assertThat(
            invoiceFinalizationAttemptRepository.existsOpenFailure(
                organizationId, testEnvironmentId, SEPTEMBER))
        .isTrue();
  }

  @Test
  void 다른_달의_인보이스는_그_달의_열린_시도를_닫지_않는다() {
    UUID organizationId = insertOrganization();
    UUID testEnvironmentId = environmentId(organizationId, "test");
    UUID testCustomer = insertCustomer(organizationId, testEnvironmentId, "가");
    save(
        organizationId,
        testEnvironmentId,
        testCustomer,
        SEPTEMBER,
        InvoiceFinalizationAttempt.FAILED);

    insertInvoice(organizationId, testEnvironmentId, testCustomer, AUGUST);

    assertThat(openAttemptCustomerIds(organizationId, testEnvironmentId))
        .containsExactly(testCustomer);
    assertThat(
            invoiceFinalizationAttemptRepository.existsOpenFailure(
                organizationId, testEnvironmentId, SEPTEMBER))
        .isTrue();
  }

  private List<UUID> candidateIds(UUID organizationId, UUID environmentId) {
    return invoiceFinalizationAttemptRepository.findUnfinishedCandidates(
        organizationId, environmentId, SEPTEMBER, SEPTEMBER_CUTOFF_AT);
  }

  private List<UUID> openAttemptCustomerIds(UUID organizationId, UUID environmentId) {
    return invoiceFinalizationAttemptRepository
        .findOpenAttempts(organizationId, environmentId)
        .stream()
        .map(OpenAttempt::getCustomerId)
        .toList();
  }

  private void save(
      UUID organizationId, UUID environmentId, UUID customerId, String period, String result) {
    save(organizationId, environmentId, customerId, period, result, OCTOBER_1_AT_14);
  }

  private void save(
      UUID organizationId,
      UUID environmentId,
      UUID customerId,
      String period,
      String result,
      OffsetDateTime attemptedAt) {
    invoiceFinalizationAttemptRepository.saveAndFlush(
        new InvoiceFinalizationAttempt(
            organizationId,
            environmentId,
            customerId,
            period,
            result,
            null,
            InvoiceFinalizationAttempt.HELD.equals(result) ? UNPRICED_USAGES : null,
            attemptedAt));
  }

  private UUID insertOrganization() {
    return jdbcTemplate.queryForObject(
        "INSERT INTO organization (name) VALUES ('테스트 도입사') RETURNING id", UUID.class);
  }

  private UUID environmentId(UUID organizationId, String mode) {
    return jdbcTemplate.queryForObject(
        "SELECT id FROM organization_environment WHERE organization_id = ? AND mode = ?",
        UUID.class,
        organizationId,
        mode);
  }

  private UUID insertCustomer(UUID organizationId, UUID environmentId, String name) {
    return insertCustomer(organizationId, environmentId, name, SEPTEMBER_CUTOFF_AT.minusDays(30));
  }

  private UUID insertCustomer(
      UUID organizationId, UUID environmentId, String name, OffsetDateTime createdAt) {
    return jdbcTemplate.queryForObject(
        """
        INSERT INTO customer (organization_id, environment_id, name, billing_email, created_at)
        VALUES (?, ?, ?, ?, ?)
        RETURNING id
        """,
        UUID.class,
        organizationId,
        environmentId,
        name,
        name + "@example.com",
        createdAt);
  }

  private void insertInvoice(
      UUID organizationId, UUID environmentId, UUID customerId, String period) {
    jdbcTemplate.update(
        """
        INSERT INTO invoice
          (organization_id, environment_id, customer_id, customer_name, period, supply_amount,
           tax_amount, total_amount, cutoff_at, finalized_at)
        VALUES (?, ?, ?, '고객', ?, 1000, 100, 1100, now(), now())
        """,
        organizationId,
        environmentId,
        customerId,
        period);
  }
}
