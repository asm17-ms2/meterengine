package com.meterengine.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.payment.client.TossPaymentsClient;
import com.meterengine.payment.entity.BillingKey;
import com.meterengine.payment.repository.BillingKeyRepository;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@Transactional
class PaymentMethodIntegrationTest {

  private static final OffsetDateTime AUTHENTICATED_AT =
      OffsetDateTime.parse("2026-10-26T01:02:03Z");

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private BillingKeyRepository billingKeyRepository;

  @MockitoBean private TossPaymentsClient tossPaymentsClient;

  private MockMvcTester mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
  }

  @Test
  void 고객_카드는_현재_모드의_것만_나오고_빌링키는_싣지_않는다() {
    UUID organizationId = insertOrganization();
    UUID customerId = insertCustomer(organizationId);
    insertBillingKey(organizationId, customerId, "live", "liveBillingKey0001", "41", "5365****");

    assertError(
        getPaymentMethod(organizationId, customerId), 404, ErrorCode.PAYMENT_METHOD_NOT_FOUND);

    insertBillingKey(organizationId, customerId, "test", "testBillingKey0001", "61", "4330****");
    MvcTestResult testCard = getPaymentMethod(organizationId, customerId);
    assertThat(testCard)
        .hasStatus(200)
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {
              "customer_id": "%s", "card_issuer_code": "61", "card_number": "4330****",
              "mode": "test", "authenticated_at": "2026-10-26T10:02:03+09:00"
            }
            """
                .formatted(customerId));
    assertThat(testCard).bodyText().doesNotContain("testBillingKey0001");

    jdbcTemplate.update(
        "INSERT INTO payment_setting (organization_id, mode, updated_at) VALUES (?, 'live', now())",
        organizationId);
    assertThat(getPaymentMethod(organizationId, customerId))
        .hasStatus(200)
        .bodyJson()
        .extractingPath("$.mode")
        .isEqualTo("live");
  }

  @Test
  void 고객이_없거나_다른_도입사의_고객이면_404_customer_not_found다() {
    UUID organizationId = insertOrganization();
    UUID otherOrganizationId = insertOrganization();
    UUID otherCustomerId = insertCustomer(otherOrganizationId);
    insertBillingKey(
        otherOrganizationId, otherCustomerId, "test", "otherKey0001", "61", "4330****");

    assertError(
        getPaymentMethod(organizationId, UUID.randomUUID()), 404, ErrorCode.CUSTOMER_NOT_FOUND);
    assertError(
        getPaymentMethod(organizationId, otherCustomerId), 404, ErrorCode.CUSTOMER_NOT_FOUND);
  }

  @Test
  void 카드_목록은_현재_모드의_카드만_customer_id_순으로_나오고_다른_도입사의_카드는_없다() {
    UUID organizationId = insertOrganization();
    UUID otherOrganizationId = insertOrganization();
    UUID firstCustomerId = insertCustomer(organizationId);
    UUID secondCustomerId = insertCustomer(organizationId);
    UUID liveOnlyCustomerId = insertCustomer(organizationId);
    UUID otherCustomerId = insertCustomer(otherOrganizationId);
    insertBillingKey(organizationId, firstCustomerId, "test", "key0001", "61", "4330****");
    insertBillingKey(organizationId, secondCustomerId, "test", "key0002", "41", "5365****");
    insertBillingKey(organizationId, liveOnlyCustomerId, "live", "key0003", "11", "9410****");
    insertBillingKey(otherOrganizationId, otherCustomerId, "test", "key0004", "61", "4330****");

    MvcTestResult listed = listPaymentMethods(organizationId);

    assertThat(listed)
        .hasStatus(200)
        .bodyJson()
        .extractingPath("$.payment_methods[*].customer_id")
        .asArray()
        .containsExactlyElementsOf(
            Stream.of(firstCustomerId, secondCustomerId)
                .map(UUID::toString)
                .sorted(Comparator.naturalOrder())
                .toList());
    assertThat(listed)
        .bodyJson()
        .extractingPath("$.payment_methods[*].mode")
        .asArray()
        .containsOnly("test");
    assertThat(listed).bodyText().doesNotContain("key0001").doesNotContain("key0002");
    assertThat(listPaymentMethods(insertOrganization()))
        .hasStatus(200)
        .bodyJson()
        .isStrictlyEqualTo("{\"payment_methods\": []}");
  }

  private MvcTestResult getPaymentMethod(UUID organizationId, UUID customerId) {
    return withOrganization(
            mvc.get().uri("/v1/customers/{id}/payment-method", customerId), organizationId)
        .exchange();
  }

  private MvcTestResult listPaymentMethods(UUID organizationId) {
    return withOrganization(mvc.get().uri("/v1/payment-methods"), organizationId).exchange();
  }

  private MockMvcTester.MockMvcRequestBuilder withOrganization(
      MockMvcTester.MockMvcRequestBuilder request, UUID organizationId) {
    return request.header(
        "Authorization", TestCredentials.issueMemberSession(jdbcTemplate, organizationId));
  }

  private void assertError(MvcTestResult result, int status, ErrorCode errorCode) {
    assertThat(result)
        .hasStatus(status)
        .bodyJson()
        .extractingPath("$.code")
        .isEqualTo(errorCode.getCode());
  }

  private void insertBillingKey(
      UUID organizationId,
      UUID customerId,
      String mode,
      String billingKey,
      String cardIssuerCode,
      String cardNumber) {
    billingKeyRepository.saveAndFlush(
        new BillingKey(
            organizationId,
            customerId,
            mode,
            billingKey,
            cardIssuerCode,
            cardNumber,
            AUTHENTICATED_AT,
            "2026-10-01",
            AUTHENTICATED_AT));
  }

  private UUID insertOrganization() {
    return jdbcTemplate.queryForObject(
        "INSERT INTO organization (name) VALUES ('결제 도입사') RETURNING id", UUID.class);
  }

  private UUID insertCustomer(UUID organizationId) {
    return jdbcTemplate.queryForObject(
        "INSERT INTO customer (organization_id, name) VALUES (?, '결제 고객') RETURNING id",
        UUID.class,
        organizationId);
  }
}
