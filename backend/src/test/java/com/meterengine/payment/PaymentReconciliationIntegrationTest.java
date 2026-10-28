package com.meterengine.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.meterengine.TestCredentials;
import com.meterengine.TestcontainersConfiguration;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.invoice.entity.Invoice;
import com.meterengine.invoice.repository.InvoiceRepository;
import com.meterengine.payment.client.TossPaymentsClient;
import com.meterengine.payment.client.TossPaymentsPayment;
import com.meterengine.payment.client.TossPaymentsResult;
import com.meterengine.payment.client.TossPaymentsTransaction;
import com.meterengine.payment.dto.PaymentJobResult;
import com.meterengine.payment.entity.TossPaymentsCredential;
import com.meterengine.payment.repository.TossPaymentsCredentialRepository;
import com.meterengine.payment.service.PaymentReconciliationService;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class PaymentReconciliationIntegrationTest {

  private static final String CLIENT_KEY = "test_ck_D5GePWvyJnrK0W0k6q8gLzN97Eoq";
  private static final String ORDER_ID_PREFIX = "1755d8_";
  private static final OffsetDateTime SLOT = OffsetDateTime.parse("2026-10-26T00:10:00+09:00");
  private static final LocalDateTime TOSS_START = LocalDateTime.parse("2026-10-24T23:00:00");
  private static final LocalDateTime TOSS_END = LocalDateTime.parse("2026-10-26T00:00:00");
  private static final List<String> KINDS =
      List.of(
          "missing_in_meterengine",
          "missing_in_toss_payments",
          "amount_mismatch",
          "status_mismatch");

  @Autowired private WebApplicationContext webApplicationContext;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private InvoiceRepository invoiceRepository;
  @Autowired private TossPaymentsCredentialRepository tossPaymentsCredentialRepository;
  @Autowired private MeterRegistry meterRegistry;
  @Autowired private PaymentReconciliationService paymentReconciliationService;

  @MockitoBean private TossPaymentsClient tossPaymentsClient;

  private MockMvcTester mvc;
  private int invoiceCount;

  // 다른 도입사의 거래 목록 조회에 결과 모름으로 답한다.
  @BeforeEach
  void setUp() {
    mvc =
        MockMvcTester.from(
            webApplicationContext, builder -> builder.apply(springSecurity()).build());
    when(tossPaymentsClient.findPayment(anyString(), anyString()))
        .thenReturn(new TossPaymentsResult.Rejected<>(404, "NOT_FOUND_PAYMENT", "없는 결제"));
    when(tossPaymentsClient.listTransactions(anyString(), any(), any(), any(), anyInt()))
        .thenReturn(new TossPaymentsResult.Unknown<>("다른 테스트의 도입사"));
  }

  @Test
  void 토스에만_있는_거래_우리에게만_있는_done_금액이_다른_거래_상태가_다른_거래를_kind별로_한_줄씩_남긴다() {
    Organization organization = organization();
    UUID matchedAttemptId = doneAttempt(organization, 13200, "2026-10-25T10:00:03+09:00");
    UUID amountAttemptId = doneAttempt(organization, 13200, "2026-10-25T11:00:03+09:00");
    UUID missingInTossAttemptId = doneAttempt(organization, 13200, "2026-10-25T12:00:03+09:00");
    UUID failedAttemptId = failedAttempt(organization, "2026-10-25T13:00:03+09:00");
    String unknownOrderId = ORDER_ID_PREFIX + UUID.randomUUID();
    String statusOrderId = ORDER_ID_PREFIX + failedAttemptId;
    stubPages(
        organization,
        List.of(
            transaction(
                "tx_matched",
                paymentKey(matchedAttemptId),
                ORDER_ID_PREFIX + matchedAttemptId,
                "DONE",
                "2026-10-25T10:00:00+09:00",
                13200),
            transaction(
                "tx_amount",
                paymentKey(amountAttemptId),
                ORDER_ID_PREFIX + amountAttemptId,
                "DONE",
                "2026-10-25T11:00:00+09:00",
                12000),
            transaction(
                "tx_unknown",
                "tgen_unknown",
                unknownOrderId,
                "DONE",
                "2026-10-25T14:00:00+09:00",
                5000),
            transaction(
                "tx_status",
                "tgen_status",
                statusOrderId,
                "DONE",
                "2026-10-25T13:00:00+09:00",
                13200)));
    Map<String, Double> before = mismatchCounts();

    reconcile(SLOT, 1, 0, 0);

    assertThat(mismatchRows(organization))
        .extracting(
            row -> row.get("kind"),
            row -> row.get("order_id"),
            row -> row.get("payment_key"),
            row -> row.get("transaction_key"),
            row -> row.get("meterengine_amount"),
            row -> row.get("toss_payments_amount"),
            row -> row.get("transacted_at"))
        .containsExactlyInAnyOrder(
            tuple(
                "amount_mismatch",
                ORDER_ID_PREFIX + amountAttemptId,
                paymentKey(amountAttemptId),
                "tx_amount",
                13200L,
                12000L,
                instant("2026-10-25T11:00:00+09:00")),
            tuple(
                "missing_in_meterengine",
                unknownOrderId,
                "tgen_unknown",
                "tx_unknown",
                null,
                5000L,
                instant("2026-10-25T14:00:00+09:00")),
            tuple(
                "status_mismatch",
                statusOrderId,
                "tgen_status",
                "tx_status",
                13200L,
                13200L,
                instant("2026-10-25T13:00:00+09:00")),
            tuple(
                "missing_in_toss_payments",
                missingInTossAttemptId.toString(),
                paymentKey(missingInTossAttemptId),
                null,
                13200L,
                null,
                instant("2026-10-25T12:00:03+09:00")));
    assertThat(deltas(before))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "missing_in_meterengine", 1.0,
                "missing_in_toss_payments", 1.0,
                "amount_mismatch", 1.0,
                "status_mismatch", 1.0));
  }

  @Test
  void 취소_거래는_transactionKey로_맞추고_금액이_다르거나_한쪽에만_있으면_남긴다() {
    Organization organization = organization();
    UUID attemptId = doneAttempt(organization, 13200, "2026-10-20T10:00:03+09:00");
    doneCancellation(
        organization, attemptId, 5000, "tx_cancel_matched", "2026-10-25T10:00:03+09:00");
    doneCancellation(
        organization, attemptId, 3000, "tx_cancel_amount", "2026-10-25T11:00:03+09:00");
    doneCancellation(
        organization, attemptId, 1000, "tx_cancel_missing", "2026-10-25T12:00:03+09:00");
    String orderId = ORDER_ID_PREFIX + attemptId;
    stubPages(
        organization,
        List.of(
            transaction(
                "tx_cancel_matched",
                paymentKey(attemptId),
                orderId,
                "PARTIAL_CANCELED",
                "2026-10-25T10:00:00+09:00",
                5000),
            transaction(
                "tx_cancel_amount",
                paymentKey(attemptId),
                orderId,
                "PARTIAL_CANCELED",
                "2026-10-25T11:00:00+09:00",
                2000),
            transaction(
                "tx_cancel_unknown",
                paymentKey(attemptId),
                orderId,
                "CANCELED",
                "2026-10-25T13:00:00+09:00",
                4200)));
    Map<String, Double> before = mismatchCounts();

    reconcile(SLOT, 1, 0, 0);

    assertThat(mismatchRows(organization))
        .extracting(
            row -> row.get("kind"),
            row -> row.get("order_id"),
            row -> row.get("payment_key"),
            row -> row.get("transaction_key"),
            row -> row.get("meterengine_amount"),
            row -> row.get("toss_payments_amount"))
        .containsExactlyInAnyOrder(
            tuple(
                "amount_mismatch",
                orderId,
                paymentKey(attemptId),
                "tx_cancel_amount",
                3000L,
                2000L),
            tuple(
                "missing_in_meterengine",
                orderId,
                paymentKey(attemptId),
                "tx_cancel_unknown",
                null,
                4200L),
            tuple(
                "missing_in_toss_payments",
                attemptId.toString(),
                paymentKey(attemptId),
                "tx_cancel_missing",
                1000L,
                null));
    assertThat(deltas(before))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "missing_in_meterengine", 1.0,
                "missing_in_toss_payments", 1.0,
                "amount_mismatch", 1.0,
                "status_mismatch", 0.0));
  }

  @Test
  void 같은_slot으로_다시_돌려도_행과_지표가_늘지_않는다() {
    Organization organization = organization();
    doneAttempt(organization, 13200, "2026-10-25T12:00:03+09:00");
    stubPages(
        organization,
        List.of(
            transaction(
                "tx_unknown",
                "tgen_unknown",
                ORDER_ID_PREFIX + UUID.randomUUID(),
                "DONE",
                "2026-10-25T14:00:00+09:00",
                5000)));
    reconcile(SLOT, 1, 0, 0);
    List<Map<String, Object>> rowsAfterFirstRun = mismatchRows(organization);
    Map<String, Double> before = mismatchCounts();

    reconcile(SLOT, 1, 0, 0);

    assertThat(rowsAfterFirstRun).hasSize(2);
    assertThat(mismatchRows(organization)).isEqualTo(rowsAfterFirstRun);
    assertThat(deltas(before)).allSatisfy((kind, delta) -> assertThat(delta).isZero());
  }

  @Test
  void 맞는_거래만_있으면_한_줄도_남기지_않는다() {
    Organization organization = organization();
    UUID attemptId = doneAttempt(organization, 13200, "2026-10-25T10:00:03+09:00");
    doneCancellation(organization, attemptId, 13200, "tx_cancel", "2026-10-25T11:00:03+09:00");
    UUID otherAttemptId = doneAttempt(organization, 9900, "2026-10-25T23:59:58+09:00");
    stubPages(
        organization,
        List.of(
            transaction(
                "tx_approval",
                paymentKey(attemptId),
                ORDER_ID_PREFIX + attemptId,
                "DONE",
                "2026-10-25T10:00:00+09:00",
                13200),
            transaction(
                "tx_cancel",
                paymentKey(attemptId),
                ORDER_ID_PREFIX + attemptId,
                "CANCELED",
                "2026-10-25T11:00:00+09:00",
                -13200),
            transaction(
                "tx_other",
                paymentKey(otherAttemptId),
                otherAttemptId.toString(),
                "DONE",
                "2026-10-25T23:59:57+09:00",
                9900)));
    Map<String, Double> before = mismatchCounts();

    reconcile(SLOT, 1, 0, 0);

    assertThat(mismatchRows(organization)).isEmpty();
    assertThat(deltas(before)).allSatisfy((kind, delta) -> assertThat(delta).isZero());
  }

  @Test
  void 창_앞_한_시간의_거래와_기록은_맞추는_데만_쓰고_불일치로_남기지_않는다() {
    Organization organization = organization();
    UUID edgeAttemptId = doneAttempt(organization, 13200, "2026-10-25T00:00:02+09:00");
    doneAttempt(organization, 13200, "2026-10-24T23:30:00+09:00");
    stubPages(
        organization,
        List.of(
            transaction(
                "tx_edge",
                paymentKey(edgeAttemptId),
                ORDER_ID_PREFIX + edgeAttemptId,
                "DONE",
                "2026-10-24T23:59:59+09:00",
                13200),
            transaction(
                "tx_before_window",
                "tgen_before_window",
                ORDER_ID_PREFIX + UUID.randomUUID(),
                "DONE",
                "2026-10-24T23:10:00+09:00",
                5000)));

    reconcile(SLOT, 1, 0, 0);

    assertThat(mismatchRows(organization)).isEmpty();
  }

  @Test
  void 천_건을_받으면_마지막_transactionKey를_startingAfter로_다음_쪽을_부른다() {
    Organization organization = organization();
    List<TossPaymentsTransaction> firstPage = new ArrayList<>();
    for (int i = 0; i < 1000; i++) {
      firstPage.add(
          transaction(
              "tx_page_" + i,
              "tgen_page_" + i,
              ORDER_ID_PREFIX + UUID.randomUUID(),
              "DONE",
              "2026-10-24T23:30:00+09:00",
              1000));
    }
    String secondPageOrderId = ORDER_ID_PREFIX + UUID.randomUUID();
    when(tossPaymentsClient.listTransactions(
            organization.secretKey(), TOSS_START, TOSS_END, null, 1000))
        .thenReturn(new TossPaymentsResult.Success<>(firstPage));
    when(tossPaymentsClient.listTransactions(
            organization.secretKey(), TOSS_START, TOSS_END, "tx_page_999", 1000))
        .thenReturn(
            new TossPaymentsResult.Success<>(
                List.of(
                    transaction(
                        "tx_second_page",
                        "tgen_second_page",
                        secondPageOrderId,
                        "DONE",
                        "2026-10-25T09:00:00+09:00",
                        7000))));

    reconcile(SLOT, 1, 0, 0);

    verify(tossPaymentsClient, times(2))
        .listTransactions(eq(organization.secretKey()), any(), any(), any(), anyInt());
    assertThat(mismatchRows(organization))
        .extracting(row -> row.get("kind"), row -> row.get("transaction_key"))
        .containsExactly(tuple("missing_in_meterengine", "tx_second_page"));
  }

  @Test
  void 거래_목록이_거절되면_그_도입사는_failed이고_다음_도입사는_대사한다() {
    Organization rejectedOrganization = organization();
    doneAttempt(rejectedOrganization, 13200, "2026-10-25T12:00:03+09:00");
    when(tossPaymentsClient.listTransactions(
            eq(rejectedOrganization.secretKey()), any(), any(), isNull(), anyInt()))
        .thenReturn(new TossPaymentsResult.Rejected<>(400, "INVALID_DATE", "날짜 범위 오류"));
    Organization organization = organization();
    UUID attemptId = doneAttempt(organization, 13200, "2026-10-25T12:00:03+09:00");
    stubPages(organization, List.of());

    reconcile(SLOT, 1, 0, 1);

    assertThat(mismatchRows(rejectedOrganization)).isEmpty();
    assertThat(mismatchRows(organization))
        .extracting(row -> row.get("kind"), row -> row.get("order_id"))
        .containsExactly(tuple("missing_in_toss_payments", attemptId.toString()));
  }

  @Test
  void 창_끝과_slot_사이에_done이_된_시도는_창_끝_전_토스_거래와_맞추고_다음_날에도_남기지_않는다() {
    Organization organization = organization();
    UUID attemptId = doneAttempt(organization, 13200, "2026-10-26T00:05:00+09:00");
    TossPaymentsTransaction approval =
        transaction(
            "tx_late",
            paymentKey(attemptId),
            ORDER_ID_PREFIX + attemptId,
            "DONE",
            "2026-10-25T23:59:59+09:00",
            13200);
    stubPages(organization, List.of(approval));
    when(tossPaymentsClient.listTransactions(
            organization.secretKey(), TOSS_START.plusDays(1), TOSS_END.plusDays(1), null, 1000))
        .thenReturn(new TossPaymentsResult.Success<>(List.of(approval)));

    reconcile(SLOT, 1, 0, 0);
    reconcile(SLOT.plusDays(1), 1, 0, 0);

    assertThat(mismatchRows(organization)).isEmpty();
  }

  @Test
  void 창_끝과_slot_사이에_done이_된_시도만_있으면_남기지_않는다() {
    Organization organization = organization();
    doneAttempt(organization, 13200, "2026-10-26T00:05:00+09:00");
    stubPages(organization, List.of());

    reconcile(SLOT, 1, 0, 0);

    assertThat(mismatchRows(organization)).isEmpty();
  }

  @Test
  void slot_뒤에_done이_된_시도는_orderId로_찾아_금액만_비교한다() {
    Organization organization = organization();
    UUID attemptId = doneAttempt(organization, 13200, "2026-10-26T00:15:00+09:00");
    stubPages(
        organization,
        List.of(
            transaction(
                "tx_after_slot",
                paymentKey(attemptId),
                ORDER_ID_PREFIX + attemptId,
                "DONE",
                "2026-10-25T23:59:59+09:00",
                13200)));

    reconcile(SLOT, 1, 0, 0);

    assertThat(mismatchRows(organization)).isEmpty();
  }

  @Test
  void DONE이_아닌_거래는_시도의_매칭을_차지하지_않고_status_mismatch만_남긴다() {
    Organization organization = organization();
    UUID attemptId = doneAttempt(organization, 13200, "2026-10-25T10:00:03+09:00");
    stubPages(
        organization,
        List.of(
            transaction(
                "tx_aborted",
                paymentKey(attemptId),
                ORDER_ID_PREFIX + attemptId,
                "ABORTED",
                "2026-10-25T09:59:00+09:00",
                13200),
            transaction(
                "tx_done",
                paymentKey(attemptId),
                ORDER_ID_PREFIX + attemptId,
                "DONE",
                "2026-10-25T10:00:00+09:00",
                12000)));

    reconcile(SLOT, 1, 0, 0);

    assertThat(mismatchRows(organization))
        .extracting(
            row -> row.get("kind"),
            row -> row.get("transaction_key"),
            row -> row.get("meterengine_amount"),
            row -> row.get("toss_payments_amount"))
        .containsExactlyInAnyOrder(
            tuple("status_mismatch", "tx_aborted", 13200L, 13200L),
            tuple("amount_mismatch", "tx_done", 13200L, 12000L));
  }

  @Test
  void slot_뒤에_done이_된_시도와_paymentKey가_다른_토스_DONE은_missing_in_meterengine이다() {
    Organization organization = organization();
    UUID attemptId = doneAttempt(organization, 13200, "2026-10-26T00:15:00+09:00");
    String orderId = ORDER_ID_PREFIX + attemptId;
    stubPages(
        organization,
        List.of(
            transaction(
                "tx_other_payment",
                "tgen_other_payment",
                orderId,
                "DONE",
                "2026-10-25T23:59:59+09:00",
                13200)));

    reconcile(SLOT, 1, 0, 0);

    assertThat(mismatchRows(organization))
        .extracting(
            row -> row.get("kind"),
            row -> row.get("order_id"),
            row -> row.get("payment_key"),
            row -> row.get("transaction_key"),
            row -> row.get("meterengine_amount"),
            row -> row.get("toss_payments_amount"))
        .containsExactly(
            tuple(
                "missing_in_meterengine",
                orderId,
                "tgen_other_payment",
                "tx_other_payment",
                null,
                13200L));
  }

  @Test
  void 다음_쪽의_마지막_transactionKey가_그대로면_그_도입사는_failed다() {
    Organization organization = organization();
    List<TossPaymentsTransaction> page = new ArrayList<>();
    for (int i = 0; i < 1000; i++) {
      page.add(
          transaction(
              "tx_page_" + i,
              "tgen_page_" + i,
              ORDER_ID_PREFIX + UUID.randomUUID(),
              "DONE",
              "2026-10-24T23:30:00+09:00",
              1000));
    }
    when(tossPaymentsClient.listTransactions(
            organization.secretKey(), TOSS_START, TOSS_END, null, 1000))
        .thenReturn(new TossPaymentsResult.Success<>(page));
    when(tossPaymentsClient.listTransactions(
            organization.secretKey(), TOSS_START, TOSS_END, "tx_page_999", 1000))
        .thenReturn(new TossPaymentsResult.Success<>(page));

    reconcile(SLOT, 0, 0, 1);

    verify(tossPaymentsClient, times(2))
        .listTransactions(eq(organization.secretKey()), any(), any(), any(), anyInt());
    assertThat(mismatchRows(organization)).isEmpty();
  }

  @Test
  void payment_key가_겹치는_done_시도는_앞의_것만_맞추고_뒤의_것은_missing_in_toss_payments로_남긴다() {
    Organization organization = organization();
    doneAttempt(organization, "test", 13200, "2026-10-25T10:00:03+09:00", "tgen_duplicate");
    UUID laterAttemptId =
        doneAttempt(organization, "test", 13200, "2026-10-25T11:00:03+09:00", "tgen_duplicate");
    stubPages(
        organization,
        List.of(
            transaction(
                "tx_duplicate",
                "tgen_duplicate",
                ORDER_ID_PREFIX + UUID.randomUUID(),
                "DONE",
                "2026-10-25T10:00:00+09:00",
                13200)));

    reconcile(SLOT, 1, 0, 0);

    assertThat(mismatchRows(organization))
        .extracting(row -> row.get("kind"), row -> row.get("order_id"))
        .containsExactly(tuple("missing_in_toss_payments", laterAttemptId.toString()));
  }

  @Test
  void 거래도_기록도_없는_도입사는_skipped다() {
    stubPages(organization(), List.of());

    reconcile(SLOT, 0, 1, 0);
  }

  @Test
  void 다른_모드의_done_시도와_취소는_대사하지_않는다() {
    Organization organization = organization();
    UUID liveAttemptId =
        doneAttempt(organization, "live", 13200, "2026-10-25T12:00:03+09:00", "tgen_live");
    jdbcTemplate.update(
        """
        INSERT INTO payment_cancellation
          (id, organization_id, payment_attempt_id, mode, amount, reason, status,
           transaction_key, requested_at, completed_at)
        VALUES (?, ?, ?, 'live', 1000, '고객 요청', 'done', 'tx_live_cancel',
                '2026-10-25T13:00:00+09:00', '2026-10-25T13:00:03+09:00')
        """,
        UUID.randomUUID(),
        organization.organizationId(),
        liveAttemptId);
    stubPages(organization, List.of());

    reconcile(SLOT, 0, 1, 0);

    assertThat(mismatchRows(organization)).isEmpty();
  }

  @Test
  void 창_시작은_포함하고_창_끝은_뺀다() {
    Organization organization = organization();
    doneAttempt(organization, 13200, "2026-10-26T00:00:00+09:00");
    String startOrderId = ORDER_ID_PREFIX + UUID.randomUUID();
    stubPages(
        organization,
        List.of(
            transaction(
                "tx_window_start",
                "tgen_window_start",
                startOrderId,
                "DONE",
                "2026-10-25T00:00:00+09:00",
                5000),
            transaction(
                "tx_window_end",
                "tgen_window_end",
                ORDER_ID_PREFIX + UUID.randomUUID(),
                "DONE",
                "2026-10-26T00:00:00+09:00",
                5000)));

    reconcile(SLOT, 1, 0, 0);

    assertThat(mismatchRows(organization))
        .extracting(row -> row.get("kind"), row -> row.get("order_id"))
        .containsExactly(tuple("missing_in_meterengine", startOrderId));
  }

  @Test
  void 접두사_없는_orderId도_우리_시도_id로_찾는다() {
    Organization organization = organization();
    UUID failedAttemptId = failedAttempt(organization, "2026-10-25T13:00:03+09:00");
    stubPages(
        organization,
        List.of(
            transaction(
                "tx_no_prefix",
                "tgen_no_prefix",
                failedAttemptId.toString(),
                "DONE",
                "2026-10-25T13:00:00+09:00",
                13200)));

    reconcile(SLOT, 1, 0, 0);

    assertThat(mismatchRows(organization))
        .extracting(row -> row.get("kind"), row -> row.get("order_id"))
        .containsExactly(tuple("status_mismatch", failedAttemptId.toString()));
  }

  @Test
  void 조회_API는_from이_to보다_늦으면_400_validation_error다() {
    UUID organizationId = organization().organizationId();

    assertThat(list(organizationId, "?from=2026-10-03&to=2026-10-02"))
        .hasStatus(400)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"code": "%s", "errors": [{"field": "to"}]}
            """
                .formatted(ErrorCode.VALIDATION_ERROR.getCode()));
  }

  @Test
  void 조회_API는_from과_to가_없거나_연도가_네_자리가_아니면_400_validation_error다() {
    UUID organizationId = organization().organizationId();

    assertThat(list(organizationId, "?to=2026-10-02"))
        .hasStatus(400)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"code": "validation_error", "errors": [{"field": "from"}]}
            """);
    assertThat(list(organizationId, "?from=0000-12-31&to=2026-10-02"))
        .hasStatus(400)
        .bodyJson()
        .isLenientlyEqualTo(
            """
            {"code": "validation_error", "errors": [{"field": "from",
              "message": "yyyy-MM-dd 형식의 0001-01-01부터 9999-12-31까지만 받습니다"}]}
            """);
    assertThat(list(organizationId, "?from=2026-10-01&to=9999-12-31")).hasStatusOk();
  }

  @Test
  void 조회_API는_그_도입사의_KST_기간_안_불일치를_detected_at_내림차순으로_돌려준다() {
    UUID organizationId = organization().organizationId();
    UUID otherOrganizationId = organization().organizationId();
    insertMismatch(organizationId, "order_before", "2026-10-01T23:59:59.999999+09:00");
    UUID first = insertMismatch(organizationId, "order_first", "2026-10-02T00:00:00+09:00");
    UUID last = insertMismatch(organizationId, "order_last", "2026-10-03T23:59:59.999999+09:00");
    insertMismatch(organizationId, "order_after", "2026-10-04T00:00:00+09:00");
    insertMismatch(otherOrganizationId, "order_other", "2026-10-02T12:00:00+09:00");

    assertThat(list(organizationId, "?from=2026-10-02&to=2026-10-03"))
        .hasStatusOk()
        .bodyJson()
        .isStrictlyEqualTo(
            """
            {"payment_reconciliation_mismatches": [
              {"id": "%s", "mode": "test", "kind": "amount_mismatch", "order_id": "order_last",
               "payment_key": "tgen_order_last", "transaction_key": "tx_order_last",
               "meterengine_amount": 13200, "toss_payments_amount": 12000,
               "transacted_at": "2026-10-01T10:00:00+09:00",
               "detected_at": "2026-10-03T23:59:59.999999+09:00"},
              {"id": "%s", "mode": "test", "kind": "amount_mismatch", "order_id": "order_first",
               "payment_key": "tgen_order_first", "transaction_key": "tx_order_first",
               "meterengine_amount": 13200, "toss_payments_amount": 12000,
               "transacted_at": "2026-10-01T10:00:00+09:00",
               "detected_at": "2026-10-02T00:00:00+09:00"}
            ]}
            """
                .formatted(last, first));
  }

  @Test
  void 이전_날_02시_승인을_05시에_복구해도_거래_조회_창에서_빠진_결제를_누락으로_남기지_않는다() {
    Organization organization = organization();
    UUID attemptId =
        doneAttempt(
            organization,
            "test",
            13200,
            "2026-10-25T05:00:00+09:00",
            null,
            OffsetDateTime.parse("2026-10-25T01:59:59+09:00"));
    OffsetDateTime slot = OffsetDateTime.parse("2026-10-26T04:00:00+09:00");
    LocalDateTime start = LocalDateTime.parse("2026-10-25T02:50:00");
    LocalDateTime end = LocalDateTime.parse("2026-10-26T03:50:00");
    TossPaymentsTransaction approval =
        transaction(
            "tx_earlier",
            paymentKey(attemptId),
            attemptId.toString(),
            "DONE",
            "2026-10-25T02:00:00+09:00",
            13200);
    assertThat(approval.transactionAt().toLocalDateTime()).isBefore(start);
    when(tossPaymentsClient.listTransactions(organization.secretKey(), start, end, null, 1000))
        .thenReturn(new TossPaymentsResult.Success<>(List.of()));
    when(tossPaymentsClient.findPayment(organization.secretKey(), paymentKey(attemptId)))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              return new TossPaymentsResult.Success<>(
                  new TossPaymentsPayment(
                      paymentKey(attemptId),
                      attemptId.toString(),
                      "DONE",
                      13200,
                      13200,
                      List.of()));
            });
    Map<String, Double> before = mismatchCounts();

    reconcile(slot, 1, 0, 0);

    verify(tossPaymentsClient).listTransactions(organization.secretKey(), start, end, null, 1000);
    verify(tossPaymentsClient).findPayment(organization.secretKey(), paymentKey(attemptId));
    assertThat(mismatchRows(organization)).isEmpty();
    assertThat(deltas(before)).allSatisfy((kind, delta) -> assertThat(delta).isZero());
  }

  @Test
  void 거래_목록에_없어도_재조회한_결제의_금액이나_상태나_주문이_다르면_불일치를_남긴다() {
    Organization organization = organization();
    UUID amountId = doneAttempt(organization, 13200, "2026-10-25T05:00:00+09:00");
    UUID statusId = doneAttempt(organization, 13200, "2026-10-25T06:00:00+09:00");
    UUID orderId = doneAttempt(organization, 13200, "2026-10-25T07:00:00+09:00");
    stubPages(organization, List.of());
    stubPayment(organization, amountId, ORDER_ID_PREFIX + amountId, "DONE", 12000);
    stubPayment(organization, statusId, statusId.toString(), "ABORTED", 13200);
    stubPayment(organization, orderId, UUID.randomUUID().toString(), "DONE", 13200);

    reconcile(SLOT, 1, 0, 0);

    assertThat(mismatchRows(organization))
        .extracting(
            row -> row.get("kind"),
            row -> row.get("order_id"),
            row -> row.get("toss_payments_amount"))
        .containsExactlyInAnyOrder(
            tuple("amount_mismatch", amountId.toString(), 12000L),
            tuple("status_mismatch", statusId.toString(), 13200L),
            tuple("status_mismatch", orderId.toString(), 13200L));
  }

  @Test
  void 거래_목록에_없어도_원래_승인_뒤에_취소된_결제는_승인_누락으로_남기지_않는다() {
    Organization organization = organization();
    UUID attemptId = doneAttempt(organization, 13200, "2026-10-25T05:00:00+09:00");
    stubPages(organization, List.of());
    stubPayment(organization, attemptId, attemptId.toString(), "CANCELED", 13200);

    reconcile(SLOT, 1, 0, 0);

    assertThat(mismatchRows(organization)).isEmpty();
  }

  @Test
  void 누락_후보의_결제_재조회가_불확실하면_failed이고_확인된_다른_누락만_남긴다() {
    Organization organization = organization();
    UUID unknownId = doneAttempt(organization, 13200, "2026-10-25T05:00:00+09:00");
    UUID rejectedId = doneAttempt(organization, 13200, "2026-10-25T06:00:00+09:00");
    UUID missingId = doneAttempt(organization, 13200, "2026-10-25T07:00:00+09:00");
    stubPages(organization, List.of());
    when(tossPaymentsClient.findPayment(organization.secretKey(), paymentKey(unknownId)))
        .thenReturn(new TossPaymentsResult.Unknown<>("응답 없음"));
    when(tossPaymentsClient.findPayment(organization.secretKey(), paymentKey(rejectedId)))
        .thenReturn(new TossPaymentsResult.Rejected<>(401, "UNAUTHORIZED_KEY", "인증되지 않은 키"));

    reconcile(SLOT, 0, 0, 1);

    assertThat(mismatchRows(organization))
        .extracting(row -> row.get("kind"), row -> row.get("order_id"))
        .containsExactly(tuple("missing_in_toss_payments", missingId.toString()));
  }

  @Test
  void 시도_이후_키가_바뀌면_재조회가_404여도_failed이고_누락으로_남기지_않는다() {
    Organization organization = organization();
    doneAttempt(organization, 13200, "2026-10-25T05:00:00+09:00");
    stubPages(organization, List.of());
    jdbcTemplate.update(
        "UPDATE toss_payments_credential SET updated_at = now() WHERE organization_id = ?",
        organization.organizationId());

    reconcile(SLOT, 0, 0, 1);

    assertThat(mismatchRows(organization)).isEmpty();
  }

  @Test
  void 거래_목록에_없는_완료_취소도_재조회한_결제의_cancels에_있으면_누락으로_남기지_않고_금액만_비교한다() {
    Organization organization = organization();
    UUID attemptId = doneAttempt(organization, 13200, "2026-10-20T10:00:03+09:00");
    doneCancellation(organization, attemptId, 3000, "tx_late_cancel", "2026-10-25T10:00:03+09:00");
    doneCancellation(organization, attemptId, 2000, "tx_late_amount", "2026-10-25T11:00:03+09:00");
    doneCancellation(
        organization, attemptId, 1000, "tx_cancel_missing", "2026-10-25T12:00:03+09:00");
    stubPages(organization, List.of());
    when(tossPaymentsClient.findPayment(organization.secretKey(), paymentKey(attemptId)))
        .thenReturn(
            new TossPaymentsResult.Success<>(
                new TossPaymentsPayment(
                    paymentKey(attemptId),
                    ORDER_ID_PREFIX + attemptId,
                    "PARTIAL_CANCELED",
                    13200,
                    8700,
                    List.of(
                        new TossPaymentsPayment.Cancel(
                            "tx_late_cancel",
                            3000,
                            OffsetDateTime.parse("2026-10-24T20:00:00+09:00"),
                            "DONE"),
                        new TossPaymentsPayment.Cancel(
                            "tx_late_amount",
                            1500,
                            OffsetDateTime.parse("2026-10-24T21:00:00+09:00"),
                            "DONE")))));
    Map<String, Double> before = mismatchCounts();

    reconcile(SLOT, 1, 0, 0);

    assertThat(mismatchRows(organization))
        .extracting(
            row -> row.get("kind"),
            row -> row.get("order_id"),
            row -> row.get("payment_key"),
            row -> row.get("transaction_key"),
            row -> row.get("meterengine_amount"),
            row -> row.get("toss_payments_amount"))
        .containsExactlyInAnyOrder(
            tuple(
                "amount_mismatch",
                attemptId.toString(),
                paymentKey(attemptId),
                "tx_late_amount",
                2000L,
                1500L),
            tuple(
                "missing_in_toss_payments",
                attemptId.toString(),
                paymentKey(attemptId),
                "tx_cancel_missing",
                1000L,
                null));
    assertThat(deltas(before))
        .containsExactlyInAnyOrderEntriesOf(
            Map.of(
                "missing_in_meterengine", 0.0,
                "missing_in_toss_payments", 1.0,
                "amount_mismatch", 1.0,
                "status_mismatch", 0.0));
  }

  @Test
  void 거래_목록에_없는_완료_취소의_결제_재조회가_불확실하면_failed이고_누락으로_남기지_않는다() {
    Organization organization = organization();
    UUID attemptId = doneAttempt(organization, 13200, "2026-10-20T10:00:03+09:00");
    doneCancellation(
        organization, attemptId, 3000, "tx_cancel_unknown", "2026-10-25T10:00:03+09:00");
    stubPages(organization, List.of());
    when(tossPaymentsClient.findPayment(organization.secretKey(), paymentKey(attemptId)))
        .thenReturn(new TossPaymentsResult.Unknown<>("응답 없음"));

    reconcile(SLOT, 0, 0, 1);

    assertThat(mismatchRows(organization)).isEmpty();
  }

  private void stubPayment(
      Organization organization, UUID attemptId, String orderId, String status, long amount) {
    when(tossPaymentsClient.findPayment(organization.secretKey(), paymentKey(attemptId)))
        .thenReturn(
            new TossPaymentsResult.Success<>(
                new TossPaymentsPayment(
                    paymentKey(attemptId), orderId, status, amount, amount, List.of())));
  }

  private void reconcile(OffsetDateTime slot, int processed, int skipped, int failed) {
    long otherCredentials = tossPaymentsCredentialRepository.count() - processed - skipped - failed;

    PaymentJobResult result = paymentReconciliationService.reconcile(slot);

    assertThat(result)
        .isEqualTo(new PaymentJobResult(processed, skipped, failed + (int) otherCredentials));
  }

  private MvcTestResult list(UUID organizationId, String query) {
    return withOrganization(
            mvc.get().uri("/v1/payment-reconciliation-mismatches" + query), organizationId)
        .exchange();
  }

  private MockMvcTester.MockMvcRequestBuilder withOrganization(
      MockMvcTester.MockMvcRequestBuilder request, UUID organizationId) {
    return request.header(
        "Authorization", TestCredentials.issueMemberSession(jdbcTemplate, organizationId));
  }

  private void stubPages(Organization organization, List<TossPaymentsTransaction> transactions) {
    when(tossPaymentsClient.listTransactions(
            organization.secretKey(), TOSS_START, TOSS_END, null, 1000))
        .thenReturn(new TossPaymentsResult.Success<>(transactions));
  }

  private static TossPaymentsTransaction transaction(
      String transactionKey,
      String paymentKey,
      String orderId,
      String status,
      String transactionAt,
      long amount) {
    return new TossPaymentsTransaction(
        transactionKey, paymentKey, orderId, status, OffsetDateTime.parse(transactionAt), amount);
  }

  private static String paymentKey(UUID attemptId) {
    return "tgen_" + attemptId;
  }

  private static Instant instant(String time) {
    return OffsetDateTime.parse(time).toInstant();
  }

  private List<Map<String, Object>> mismatchRows(Organization organization) {
    return jdbcTemplate
        .queryForList(
            "SELECT * FROM payment_reconciliation_mismatch WHERE organization_id = ?"
                + " ORDER BY kind, transaction_key",
            organization.organizationId())
        .stream()
        .map(PaymentReconciliationIntegrationTest::withInstant)
        .toList();
  }

  private static Map<String, Object> withInstant(Map<String, Object> row) {
    Map<String, Object> converted = new HashMap<>(row);
    for (String column : List.of("transacted_at", "detected_at")) {
      if (row.get(column) instanceof Timestamp timestamp) {
        converted.put(column, timestamp.toInstant());
      }
    }
    return converted;
  }

  private Map<String, Double> mismatchCounts() {
    Map<String, Double> counts = new HashMap<>();
    for (String kind : KINDS) {
      counts.put(
          kind,
          meterRegistry
              .get("meterengine.payment.reconciliation.mismatch")
              .tags("mode", "test", "kind", kind)
              .counter()
              .count());
    }
    return counts;
  }

  private Map<String, Double> deltas(Map<String, Double> before) {
    Map<String, Double> after = mismatchCounts();
    Map<String, Double> deltas = new HashMap<>();
    for (String kind : KINDS) {
      deltas.put(kind, after.get(kind) - before.get(kind));
    }
    return deltas;
  }

  private UUID insertMismatch(UUID organizationId, String orderId, String detectedAt) {
    UUID id = UUID.randomUUID();
    jdbcTemplate.update(
        """
        INSERT INTO payment_reconciliation_mismatch
          (id, organization_id, mode, kind, order_id, payment_key, transaction_key,
           meterengine_amount, toss_payments_amount, transacted_at, detected_at)
        VALUES (?, ?, 'test', 'amount_mismatch', ?, ?, ?, 13200, 12000,
                '2026-10-01T10:00:00+09:00', ?)
        """,
        id,
        organizationId,
        orderId,
        "tgen_" + orderId,
        "tx_" + orderId,
        OffsetDateTime.parse(detectedAt));
    return id;
  }

  private UUID doneAttempt(Organization organization, long amount, String completedAt) {
    return doneAttempt(organization, "test", amount, completedAt, null);
  }

  private UUID doneAttempt(
      Organization organization, String mode, long amount, String completedAt, String paymentKey) {
    return doneAttempt(
        organization,
        mode,
        amount,
        completedAt,
        paymentKey,
        OffsetDateTime.parse(completedAt).minusSeconds(3));
  }

  private UUID doneAttempt(
      Organization organization,
      String mode,
      long amount,
      String completedAt,
      String paymentKey,
      OffsetDateTime requestedAt) {
    UUID attemptId = UUID.randomUUID();
    OffsetDateTime completed = OffsetDateTime.parse(completedAt);
    jdbcTemplate.update(
        """
        INSERT INTO payment_attempt
          (id, organization_id, customer_id, invoice_id, mode, amount, order_name, status,
           payment_key, requested_at, completed_at)
        VALUES (?, ?, ?, ?, ?, ?, '2026-09 사용료', 'done', ?, ?, ?)
        """,
        attemptId,
        organization.organizationId(),
        organization.customerId(),
        invoice(organization),
        mode,
        amount,
        paymentKey == null ? paymentKey(attemptId) : paymentKey,
        requestedAt,
        completed);
    return attemptId;
  }

  private UUID failedAttempt(Organization organization, String completedAt) {
    UUID attemptId = UUID.randomUUID();
    OffsetDateTime completed = OffsetDateTime.parse(completedAt);
    jdbcTemplate.update(
        """
        INSERT INTO payment_attempt
          (id, organization_id, customer_id, invoice_id, mode, amount, order_name, status,
           failure_code, failure_message, requested_at, completed_at)
        VALUES (?, ?, ?, ?, 'test', 13200, '2026-09 사용료', 'failed', 'REJECT_CARD_PAYMENT',
                '한도 초과', ?, ?)
        """,
        attemptId,
        organization.organizationId(),
        organization.customerId(),
        invoice(organization),
        completed.minusSeconds(3),
        completed);
    return attemptId;
  }

  private void doneCancellation(
      Organization organization,
      UUID attemptId,
      long amount,
      String transactionKey,
      String completedAt) {
    OffsetDateTime completed = OffsetDateTime.parse(completedAt);
    jdbcTemplate.update(
        """
        INSERT INTO payment_cancellation
          (id, organization_id, payment_attempt_id, mode, amount, reason, status,
           transaction_key, requested_at, completed_at)
        VALUES (?, ?, ?, 'test', ?, '고객 요청', 'done', ?, ?, ?)
        """,
        UUID.randomUUID(),
        organization.organizationId(),
        attemptId,
        amount,
        transactionKey,
        completed.minusSeconds(3),
        completed);
  }

  private UUID invoice(Organization organization) {
    return invoiceRepository
        .save(
            new Invoice(
                UUID.randomUUID(),
                organization.organizationId(),
                organization.customerId(),
                "대사 고객",
                "2025-%02d".formatted(++invoiceCount),
                12000,
                1200,
                OffsetDateTime.parse("2026-09-01T05:00:00Z")))
        .getId();
  }

  private Organization organization() {
    UUID organizationId =
        jdbcTemplate.queryForObject(
            "INSERT INTO organization (name) VALUES ('대사 도입사') RETURNING id", UUID.class);
    UUID customerId =
        jdbcTemplate.queryForObject(
            "INSERT INTO customer (organization_id, name) VALUES (?, '대사 고객') RETURNING id",
            UUID.class,
            organizationId);
    String secretKey = "test_sk_" + UUID.randomUUID().toString().replace("-", "");
    tossPaymentsCredentialRepository.saveAndFlush(
        new TossPaymentsCredential(
            organizationId,
            "test",
            CLIENT_KEY,
            secretKey,
            OffsetDateTime.parse("2026-10-01T00:00:00Z")));
    invoiceCount = 0;
    return new Organization(organizationId, customerId, secretKey);
  }

  private record Organization(UUID organizationId, UUID customerId, String secretKey) {}
}
