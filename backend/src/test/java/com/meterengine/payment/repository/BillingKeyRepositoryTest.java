package com.meterengine.payment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.TestcontainersConfiguration;
import com.meterengine.payment.entity.BillingKey;
import com.meterengine.payment.entity.BillingKeyId;
import com.meterengine.payment.entity.PaymentSetting;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
class BillingKeyRepositoryTest {

  private static final String BILLING_KEY = "plainBillingKey5678";
  private static final OffsetDateTime AUTHENTICATED_AT =
      OffsetDateTime.of(2026, 10, 1, 0, 0, 0, 0, ZoneOffset.UTC);

  @Autowired private BillingKeyRepository billingKeyRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EntityManager entityManager;

  @Test
  void 빌링키는_암호문으로_저장되고_평문으로_다시_읽힌다() {
    UUID organizationId =
        jdbcTemplate.queryForObject(
            "INSERT INTO organization (name) VALUES ('결제 도입사') RETURNING id", UUID.class);
    UUID customerId =
        jdbcTemplate.queryForObject(
            "INSERT INTO customer (organization_id, name) VALUES (?, '결제 고객') RETURNING id",
            UUID.class,
            organizationId);
    billingKeyRepository.saveAndFlush(
        new BillingKey(
            organizationId,
            customerId,
            PaymentSetting.TEST,
            BILLING_KEY,
            "61",
            "1234****",
            AUTHENTICATED_AT,
            "2026-10",
            AUTHENTICATED_AT));
    entityManager.clear();

    BillingKey reloaded =
        billingKeyRepository
            .findById(new BillingKeyId(organizationId, customerId, PaymentSetting.TEST))
            .orElseThrow();

    assertThat(reloaded.getBillingKey()).isEqualTo(BILLING_KEY);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT billing_key FROM billing_key WHERE customer_id = ?",
                String.class,
                customerId))
        .startsWith("v1:")
        .doesNotContain(BILLING_KEY);
  }
}
