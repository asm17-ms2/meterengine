package com.meterengine.payment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.meterengine.TestcontainersConfiguration;
import com.meterengine.payment.entity.PaymentSetting;
import com.meterengine.payment.entity.TossPaymentsCredential;
import com.meterengine.payment.entity.TossPaymentsCredentialId;
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
class TossPaymentsCredentialRepositoryTest {

  private static final String SECRET_KEY = "test_sk_plainSecret1234";
  private static final OffsetDateTime VERIFIED_AT =
      OffsetDateTime.of(2026, 10, 1, 0, 0, 0, 0, ZoneOffset.UTC);

  @Autowired private TossPaymentsCredentialRepository tossPaymentsCredentialRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EntityManager entityManager;

  @Test
  void 비밀_키는_암호문으로_저장되고_평문으로_다시_읽힌다() {
    UUID organizationId = insertOrganization();
    tossPaymentsCredentialRepository.saveAndFlush(
        new TossPaymentsCredential(
            organizationId, PaymentSetting.TEST, "test_ck_client", SECRET_KEY, VERIFIED_AT));
    entityManager.clear();

    TossPaymentsCredential reloaded =
        tossPaymentsCredentialRepository
            .findById(new TossPaymentsCredentialId(organizationId, PaymentSetting.TEST))
            .orElseThrow();

    assertThat(reloaded.getSecretKey()).isEqualTo(SECRET_KEY);
    assertThat(reloaded.getSecretKeyLastFour()).isEqualTo("1234");
    assertThat(storedSecretKey(organizationId)).startsWith("v1:").doesNotContain(SECRET_KEY);
  }

  @Test
  void 읽어_온_키를_고쳐_저장하면_같은_행이_바뀐다() {
    UUID organizationId = insertOrganization();
    tossPaymentsCredentialRepository.saveAndFlush(
        new TossPaymentsCredential(
            organizationId, PaymentSetting.TEST, "test_ck_client", SECRET_KEY, VERIFIED_AT));
    entityManager.clear();
    TossPaymentsCredential loaded =
        tossPaymentsCredentialRepository
            .findById(new TossPaymentsCredentialId(organizationId, PaymentSetting.TEST))
            .orElseThrow();

    loaded.update("test_ck_other", "test_sk_otherSecret9876", VERIFIED_AT.plusDays(1));
    tossPaymentsCredentialRepository.saveAndFlush(loaded);
    entityManager.clear();

    TossPaymentsCredential reloaded =
        tossPaymentsCredentialRepository
            .findById(new TossPaymentsCredentialId(organizationId, PaymentSetting.TEST))
            .orElseThrow();
    assertThat(reloaded.getClientKey()).isEqualTo("test_ck_other");
    assertThat(reloaded.getSecretKey()).isEqualTo("test_sk_otherSecret9876");
    assertThat(reloaded.getSecretKeyLastFour()).isEqualTo("9876");
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM toss_payments_credential WHERE organization_id = ?",
                Integer.class,
                organizationId))
        .isEqualTo(1);
  }

  private String storedSecretKey(UUID organizationId) {
    return jdbcTemplate.queryForObject(
        "SELECT secret_key FROM toss_payments_credential WHERE organization_id = ?",
        String.class,
        organizationId);
  }

  private UUID insertOrganization() {
    return jdbcTemplate.queryForObject(
        "INSERT INTO organization (name) VALUES ('결제 도입사') RETURNING id", UUID.class);
  }
}
