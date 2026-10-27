package com.meterengine.archive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.meterengine.TestcontainersConfiguration;
import com.meterengine.customer.service.CustomerService;
import com.meterengine.global.error.ConflictException;
import com.meterengine.metric.dto.UpdateBillableMetricRequest;
import com.meterengine.metric.service.BillableMetricService;
import java.util.List;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@org.springframework.test.annotation.DirtiesContext
class ArchiveMutationConcurrencyTest {
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private CustomerService customerService;
  @Autowired private BillableMetricService billableMetricService;
  private UUID organizationId;
  private UUID customerId;

  @BeforeEach
  void setup() {
    organizationId =
        jdbcTemplate.queryForObject(
            "INSERT INTO organization(name) VALUES ('보관 경합') RETURNING id", UUID.class);
    customerId = customerService.create(organizationId, "고객", null).getId();
    jdbcTemplate.update(
        "INSERT INTO billable_metric(organization_id,code,name,event_type,aggregation,target_property) VALUES (?, 'tokens', '토큰', 'tokens', 'sum', 'quantity')",
        organizationId);
  }

  @Test
  void 고객_보관이_진행중이면_수정은_기다린뒤_활성상태를_다시_검사한다() throws Exception {
    serial(
        () -> customerService.delete(organizationId, customerId),
        () ->
            assertThatThrownBy(() -> customerService.update(organizationId, customerId, "변경", null))
                .isInstanceOf(ConflictException.class));
    assertThat(customerService.get(organizationId, customerId).getArchivedAt()).isNotNull();
    assertThat(customerService.get(organizationId, customerId).getName()).isEqualTo("고객");
  }

  @Test
  void 미터_보관이_진행중이면_수정은_기다린뒤_활성상태를_다시_검사한다() throws Exception {
    serial(
        () -> billableMetricService.delete(organizationId, "tokens"),
        () ->
            assertThatThrownBy(
                    () ->
                        billableMetricService.update(
                            organizationId,
                            "tokens",
                            new UpdateBillableMetricRequest(
                                "변경", "tokens", "sum", "quantity", List.of())))
                .isInstanceOf(ConflictException.class));
    assertThat(billableMetricService.get(organizationId, "tokens").archivedAt()).isNotNull();
    assertThat(billableMetricService.get(organizationId, "tokens").name()).isEqualTo("토큰");
  }

  private void serial(Runnable first, Runnable second) throws Exception {
    CountDownLatch saved = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    CompletableFuture<Void> firstWrite =
        CompletableFuture.runAsync(
            () ->
                new TransactionTemplate(transactionManager)
                    .executeWithoutResult(
                        status -> {
                          first.run();
                          saved.countDown();
                          try {
                            if (!release.await(10, TimeUnit.SECONDS))
                              throw new IllegalStateException("release timed out");
                          } catch (InterruptedException exception) {
                            Thread.currentThread().interrupt();
                            throw new IllegalStateException(exception);
                          }
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
}
