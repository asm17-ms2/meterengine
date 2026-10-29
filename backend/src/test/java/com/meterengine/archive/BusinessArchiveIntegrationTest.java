package com.meterengine.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.customer.service.CustomerService;
import com.meterengine.event.dto.IngestEventRequest;
import com.meterengine.event.service.EventIngestionService;
import com.meterengine.event.service.EventQueryService;
import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.metric.service.BillableMetricService;
import com.meterengine.metric.service.BillableMetricUsageService;
import com.meterengine.payment.entity.BillingKey;
import com.meterengine.payment.entity.TossPaymentsCredential;
import com.meterengine.payment.repository.BillingKeyRepository;
import com.meterengine.payment.repository.PaymentArchiveRepository;
import com.meterengine.payment.repository.TossPaymentsCredentialRepository;
import com.meterengine.payment.service.PaymentMethodRegistrationService;
import com.meterengine.payment.service.PaymentMethodService;
import com.meterengine.payment.service.TossPaymentsCredentialService;
import com.meterengine.pricing.dto.CreatePriceRateRequest;
import com.meterengine.pricing.service.BillableMetricPriceService;
import com.meterengine.pricing.service.PriceRateService;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class BusinessArchiveIntegrationTest {
  @Autowired private JdbcTemplate jdbc;
  @Autowired private CustomerService customers;
  @Autowired private EventIngestionService ingestion;
  @Autowired private com.meterengine.event.service.EventBatchService batches;
  @Autowired private tools.jackson.databind.json.JsonMapper jsonMapper;
  @Autowired private EventQueryService events;
  @Autowired private com.meterengine.event.service.UnbilledEventService unbilledEvents;
  @Autowired private BillableMetricService metrics;
  @Autowired private BillableMetricUsageService usage;
  @Autowired private PriceRateService rates;
  @Autowired private BillableMetricPriceService prices;
  @Autowired private BillingKeyRepository keys;
  @Autowired private TossPaymentsCredentialRepository credentials;
  @Autowired private TossPaymentsCredentialService credentialService;
  @Autowired private PaymentMethodService methods;
  @Autowired private PaymentMethodRegistrationService registrations;
  @Autowired private PaymentArchiveRepository archives;
  @Autowired private EntityManager entityManager;
  @Autowired private WebApplicationContext context;
  private UUID organizationId;
  private UUID customerId;
  private YearMonth month;

  @BeforeEach
  void setup() {
    organizationId =
        jdbc.queryForObject(
            "INSERT INTO organization(name) VALUES ('보관 검증') RETURNING id", UUID.class);
    customerId = customers.create(organizationId, "고객", null).getId();
    jdbc.update(
        "INSERT INTO billable_metric (organization_id, code, name, event_type, aggregation, target_property) VALUES (?, 'tokens', '토큰', 'tokens', 'sum', 'quantity')",
        organizationId);
    month = BillableMetricUsageService.currentMonth();
  }

  @Test
  void 고객_보관은_신규수집을_막고_중복응답과_조회와_기존사용량을_유지한다() {
    ingestion.ingest(organizationId, request("stored"));
    customers.delete(organizationId, customerId);
    assertThat(ingestion.ingest(organizationId, request("stored")).duplicate()).isTrue();
    assertThatThrownBy(() -> ingestion.ingest(organizationId, request("new")))
        .isInstanceOfSatisfying(
            ConflictException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.CUSTOMER_ARCHIVED));
    var batch =
        batches.ingest(
            organizationId,
            new com.meterengine.event.dto.IngestEventBatchRequest(
                java.util.List.of(
                    jsonMapper.readTree(jsonMapper.writeValueAsString(request("stored"))),
                    jsonMapper.readTree(jsonMapper.writeValueAsString(request("batch-new"))))));
    assertThat(batch.results().getFirst().result()).isEqualTo("duplicate");
    assertThat(batch.results().getLast().error().code()).isEqualTo(ErrorCode.CUSTOMER_ARCHIVED);
    assertThat(events.find(organizationId, "stored").customerId()).isEqualTo(customerId);
    assertThat(usage.aggregate(organizationId, month).getFirst().customers().getFirst().quantity())
        .isEqualByComparingTo("10");
    assertThat(customers.list(organizationId)).isEmpty();
    assertThat(customers.list(organizationId, "archived")).hasSize(1);
    customers.restore(organizationId, customerId);
    assertThat(ingestion.ingest(organizationId, request("new")).duplicate()).isFalse();
  }

  @Test
  void 미터_보관은_현재월을_유지하고_복원은_보관기간을_소급활성화하지_않는다() {
    ingestion.ingest(organizationId, request("stored"));
    metrics.delete(organizationId, "tokens");
    metrics.delete(organizationId, "tokens");
    assertThat(usage.aggregate(organizationId, month).getFirst().customers().getFirst().quantity())
        .isEqualByComparingTo("10");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM billable_metric_period WHERE organization_id = ?",
                Integer.class,
                organizationId))
        .isEqualTo(1);
    entityManager.flush();
    jdbc.update(
        "UPDATE billable_metric_period SET active_until = ? WHERE organization_id = ?",
        month.toString(),
        organizationId);
    jdbc.update(
        "UPDATE billable_metric SET inactive_from = ? WHERE organization_id = ?",
        month.toString(),
        organizationId);
    entityManager.clear();
    var restored = metrics.restore(organizationId, "tokens");
    assertThat(restored.activeFrom()).isEqualTo(month.plusMonths(1).toString());
    assertThat(usage.aggregate(organizationId, month)).isEmpty();
    assertThat(unbilledEvents.get(organizationId, month).unmatchedType().eventCount()).isEqualTo(1);
    assertThat(metrics.list(organizationId).billableMetrics()).hasSize(1);
  }

  @Test
  void 단가_보관은_현재효력과_이력을_보존하고_미래예약슬롯은_재사용한다() {
    var current = rates.create(organizationId, "tokens", rate(month, "2"));
    var future = rates.create(organizationId, "tokens", rate(month.plusMonths(1), "3"));
    rates.delete(organizationId, "tokens", current.id());
    assertThat(applied(month)).isEqualByComparingTo("2");
    assertThat(rates.list(organizationId, "tokens", null, "archived").priceRates()).hasSize(1);
    rates.restore(organizationId, "tokens", current.id());
    rates.delete(organizationId, "tokens", future.id());
    var replacement = rates.create(organizationId, "tokens", rate(month.plusMonths(1), "4"));
    assertThat(replacement.id()).isNotEqualTo(future.id());
    assertThatThrownBy(() -> rates.restore(organizationId, "tokens", future.id()))
        .isInstanceOf(ConflictException.class);
    assertThat(applied(month.plusMonths(1))).isEqualByComparingTo("4");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM price_rate WHERE organization_id = ?",
                Integer.class,
                organizationId))
        .isEqualTo(3);
    UUID otherOrganizationId =
        jdbc.queryForObject(
            "INSERT INTO organization(name) VALUES ('다른 도입사') RETURNING id", UUID.class);
    assertThatThrownBy(() -> rates.restore(otherOrganizationId, "tokens", future.id()))
        .isInstanceOfSatisfying(
            NotFoundException.class,
            error -> assertThat(error.getErrorCode()).isEqualTo(ErrorCode.PRICE_RATE_NOT_FOUND));
    entityManager.flush();
    assertThat(
            jdbc.queryForObject(
                "SELECT archived_at IS NOT NULL FROM price_rate WHERE id = ?",
                Boolean.class,
                future.id()))
        .isTrue();
  }

  @Test
  void 보관한_카드와_PG키는_새등록을_막고_재등록전_이력을_보존한다() {
    OffsetDateTime before = OffsetDateTime.now().minusDays(1);
    BillingKey key =
        keys.saveAndFlush(
            new BillingKey(
                organizationId,
                customerId,
                "test",
                "dummy-billing-key",
                "61",
                "1234****5678",
                before,
                "v1",
                before));
    TossPaymentsCredential credential =
        credentials.saveAndFlush(
            new TossPaymentsCredential(
                organizationId, "test", "test_ck_dummy", "test_sk_dummy", before));
    methods.delete(organizationId, customerId);
    credentialService.delete(organizationId, "test");
    assertThat(methods.list(organizationId).paymentMethods()).isEmpty();
    assertThat(methods.get(organizationId, customerId).archivedAt()).isNotNull();
    assertThat(credentialService.list(organizationId).tossPaymentsCredentials()).isEmpty();
    assertThatThrownBy(() -> registrations.create(organizationId, customerId))
        .isInstanceOf(ConflictException.class);
    entityManager.flush();
    archives.method(organizationId, customerId, "test");
    archives.credential(organizationId, "test");
    key.update(
        "dummy-new-key", "61", "9876****1234", OffsetDateTime.now(), "v1", OffsetDateTime.now());
    credential.update("test_ck_new", "test_sk_new", OffsetDateTime.now());
    entityManager.flush();
    assertThat(methods.list(organizationId, "all").paymentMethods()).hasSize(2);
    assertThat(credentialService.list(organizationId, "all").tossPaymentsCredentials()).hasSize(2);
    assertThat(archives.historical(organizationId, "test", before.plusHours(1))).isPresent();
  }

  @Test
  void 읽기전용키는_보관과_복원을_할_수_없다() {
    String auth = TestCredentials.issueApiKey(jdbc, organizationId, "read");
    MockMvcTester mvc =
        MockMvcTester.from(context, builder -> builder.apply(springSecurity()).build());
    assertThat(
            mvc.delete().uri("/v1/customers/" + customerId).header(AUTHORIZATION, auth).exchange())
        .hasStatus(403);
    assertThat(
            mvc.post()
                .uri("/v1/customers/" + customerId + "/restorations")
                .header(AUTHORIZATION, auth)
                .exchange())
        .hasStatus(403);
    assertThat(customers.get(organizationId, customerId).getArchivedAt()).isNull();
  }

  private IngestEventRequest request(String transactionId) {
    return new IngestEventRequest(
        transactionId,
        customerId,
        "tokens",
        Map.of("quantity", 10),
        OffsetDateTime.now().minusSeconds(1));
  }

  private CreatePriceRateRequest rate(YearMonth start, String value) {
    return new CreatePriceRateRequest(
        Map.of(), new BigDecimal(value), null, start, null, "per_unit", null);
  }

  private BigDecimal applied(YearMonth target) {
    return prices
        .list(organizationId, target, null)
        .billableMetricPrices()
        .getFirst()
        .priceRates()
        .getFirst()
        .unitPrice();
  }
}
