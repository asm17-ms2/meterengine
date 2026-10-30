package com.meterengine.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.meterengine.TestcontainersConfiguration;
import com.meterengine.event.repository.EventRepository;
import com.meterengine.payment.client.TossPaymentsBilling;
import com.meterengine.payment.client.TossPaymentsClient;
import com.meterengine.payment.client.TossPaymentsResult;
import com.meterengine.payment.dto.CreateBillingKeyRequest;
import com.meterengine.payment.dto.UpdateTossPaymentsCredentialRequest;
import com.meterengine.payment.entity.BillingKey;
import com.meterengine.payment.entity.TossPaymentsCredential;
import com.meterengine.payment.entity.TossPaymentsCredentialId;
import com.meterengine.payment.repository.BillingKeyRepository;
import com.meterengine.payment.repository.PaymentArchiveRepository;
import com.meterengine.payment.repository.TossPaymentsCredentialRepository;
import com.meterengine.payment.service.BillingKeyService;
import com.meterengine.payment.service.PaymentMethodRegistrationService;
import com.meterengine.payment.service.PaymentMethodService;
import com.meterengine.payment.service.TossPaymentsCredentialService;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@org.springframework.test.annotation.DirtiesContext
class PaymentArchiveConcurrencyTest {
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private PaymentArchiveRepository paymentArchiveRepository;
  @Autowired private TossPaymentsCredentialRepository tossPaymentsCredentialRepository;
  @Autowired private TossPaymentsCredentialService tossPaymentsCredentialService;
  @Autowired private BillingKeyRepository billingKeyRepository;
  @Autowired private BillingKeyService billingKeyService;
  @Autowired private PaymentMethodRegistrationService paymentMethodRegistrationService;
  @Autowired private PaymentMethodService paymentMethodService;
  @Autowired private EventRepository eventRepository;
  @MockitoBean private TossPaymentsClient tossPaymentsClient;
  private UUID organizationId;
  private UUID customerId;

  @BeforeEach
  void setup() {
    organizationId =
        jdbcTemplate.queryForObject(
            "INSERT INTO organization(name) VALUES ('이력 경합') RETURNING id", UUID.class);
    customerId =
        jdbcTemplate.queryForObject(
            "INSERT INTO customer(organization_id,name) VALUES (?, '고객') RETURNING id",
            UUID.class,
            organizationId);
    OffsetDateTime before = OffsetDateTime.now().minusDays(1);
    tossPaymentsCredentialRepository.saveAndFlush(
        new TossPaymentsCredential(organizationId, "test", "test_ck_A001", "test_sk_A001", before));
    when(tossPaymentsClient.findPaymentByOrderId(any(), any()))
        .thenReturn(new TossPaymentsResult.Rejected<>(404, "NOT_FOUND_PAYMENT", "not found"));
  }

  @Test
  void 동시에_PG키를_바꿔도_중간버전의_이력을_잃지_않는다() throws Exception {
    race(() -> update("B002"), () -> update("C003"));
    assertThat(paymentArchiveRepository.credentials(organizationId))
        .extracting(credential -> credential.secretKeyLastFour())
        .containsExactlyInAnyOrder("A001", "B002");
    assertThat(
            tossPaymentsCredentialRepository
                .findById(new TossPaymentsCredentialId(organizationId, "test"))
                .orElseThrow()
                .getSecretKeyLastFour())
        .isEqualTo("C003");
  }

  @Test
  void 서로다른_등록링크의_동시_카드재등록도_중간카드의_이력을_남긴다() throws Exception {
    OffsetDateTime before = OffsetDateTime.now().minusDays(1);
    billingKeyRepository.saveAndFlush(
        new BillingKey(
            organizationId,
            customerId,
            "test",
            "billing-A001",
            "61",
            "A001****",
            before,
            "v1",
            before));
    String first = paymentMethodRegistrationService.create(organizationId, customerId).token();
    String second = paymentMethodRegistrationService.create(organizationId, customerId).token();
    when(tossPaymentsClient.issueBillingKey(any(), eq("auth-B"), eq(customerId.toString())))
        .thenReturn(new TossPaymentsResult.Success<>(billing("B002")));
    when(tossPaymentsClient.issueBillingKey(any(), eq("auth-C"), eq(customerId.toString())))
        .thenReturn(new TossPaymentsResult.Success<>(billing("C003")));
    when(tossPaymentsClient.deleteBillingKey(any(), any()))
        .thenReturn(new TossPaymentsResult.Success<>(null));
    race(
        () ->
            billingKeyService.create(
                first, new CreateBillingKeyRequest("auth-B", customerId.toString(), "v1")),
        () ->
            billingKeyService.create(
                second, new CreateBillingKeyRequest("auth-C", customerId.toString(), "v1")));
    assertThat(paymentArchiveRepository.methods(organizationId, "test"))
        .extracting(method -> method.cardNumber())
        .containsExactlyInAnyOrder("A001****", "B002****");
  }

  @Test
  void 카드_보관도_같은_도입사의_잠금이_풀릴_때까지_기다린다() throws Exception {
    OffsetDateTime before = OffsetDateTime.now().minusDays(1);
    billingKeyRepository.saveAndFlush(
        new BillingKey(
            organizationId,
            customerId,
            "test",
            "billing-A001",
            "61",
            "A001****",
            before,
            "v1",
            before));
    when(tossPaymentsClient.deleteBillingKey(any(), any()))
        .thenReturn(new TossPaymentsResult.Success<>(null));
    race(() -> {}, () -> paymentMethodService.delete(organizationId, customerId));
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT archived_at IS NOT NULL FROM billing_key WHERE organization_id = ? AND customer_id = ?",
                Boolean.class,
                organizationId,
                customerId))
        .isTrue();
  }

  @Test
  void 도입사_잠금을_쥐고_있어도_그_도입사의_이벤트_수집은_기다리지_않는다() throws Exception {
    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CompletableFuture<Void> holder =
        CompletableFuture.runAsync(
            () ->
                new TransactionTemplate(transactionManager)
                    .executeWithoutResult(
                        status -> {
                          paymentArchiveRepository.lock(organizationId);
                          locked.countDown();
                          awaitRelease(release);
                        }));
    try {
      assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
      CompletableFuture<Integer> ingestion =
          CompletableFuture.supplyAsync(
              () ->
                  eventRepository.insertIfAbsent(
                      organizationId,
                      UUID.randomUUID().toString(),
                      customerId,
                      "tokens",
                      "{}",
                      OffsetDateTime.now()));
      assertThat(ingestion.get(2, TimeUnit.SECONDS)).isEqualTo(1);
    } finally {
      release.countDown();
    }
    holder.get(10, TimeUnit.SECONDS);
  }

  private void update(String suffix) {
    tossPaymentsCredentialService.update(
        organizationId,
        "test",
        new UpdateTossPaymentsCredentialRequest("test_ck_" + suffix, "test_sk_" + suffix));
  }

  private TossPaymentsBilling billing(String suffix) {
    return new TossPaymentsBilling(
        "billing-" + suffix,
        OffsetDateTime.now(),
        new TossPaymentsBilling.Card("61", suffix + "****"));
  }

  private void race(Runnable first, Runnable second) throws Exception {
    CountDownLatch saved = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CompletableFuture<Void> firstWrite =
        CompletableFuture.runAsync(
            () ->
                new TransactionTemplate(transactionManager)
                    .executeWithoutResult(
                        status -> {
                          paymentArchiveRepository.lock(organizationId);
                          first.run();
                          saved.countDown();
                          awaitRelease(release);
                        }));
    try {
      assertThat(saved.await(10, TimeUnit.SECONDS)).isTrue();
      CompletableFuture<Void> secondWrite = CompletableFuture.runAsync(second);
      assertThatThrownBy(() -> secondWrite.get(200, TimeUnit.MILLISECONDS))
          .isInstanceOf(TimeoutException.class);
      release.countDown();
      firstWrite.get(10, TimeUnit.SECONDS);
      secondWrite.get(10, TimeUnit.SECONDS);
    } finally {
      release.countDown();
    }
  }

  private static void awaitRelease(CountDownLatch release) {
    try {
      if (!release.await(10, TimeUnit.SECONDS))
        throw new IllegalStateException("release timed out");
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }
}
