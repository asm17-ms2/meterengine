package com.meterengine.metric.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meterengine.global.error.BusinessException;
import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ErrorResponse.FieldError;
import com.meterengine.global.error.InvalidRequestException;
import com.meterengine.metric.dto.BillableMetricResponse;
import com.meterengine.metric.dto.CreateBillableMetricRequest;
import com.meterengine.metric.dto.ListBillableMetricsResponse;
import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.metric.entity.BillableMetricId;
import com.meterengine.metric.repository.BillableMetricRepository;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class BillableMetricServiceTest {

  private static final UUID ORGANIZATION_ID = UUID.randomUUID();
  private static final String BILLABLE_METRIC_CODE = "token-usage";

  @Mock private BillableMetricRepository billableMetricRepository;

  private BillableMetricService billableMetricService;

  @BeforeEach
  void setUp() {
    billableMetricService = new BillableMetricService(billableMetricRepository);
  }

  @Test
  void SUM이_아닌_집계_함수는_Invalid다() {
    assertThatThrownBy(() -> create("COUNT", "token"))
        .isInstanceOf(InvalidRequestException.class)
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_BILLABLE_METRIC);
    verify(billableMetricRepository, never()).saveAndFlush(any());
  }

  @Test
  void SUM이_아닌_집계_함수는_aggregation을_errors에_담는다() {
    assertThatThrownBy(() -> create("COUNT", "token"))
        .isInstanceOfSatisfying(
            InvalidRequestException.class,
            exception ->
                assertThat(exception.getErrors())
                    .extracting(FieldError::field)
                    .containsExactly("aggregation"));
  }

  @Test
  void SUM인데_target_property가_없으면_Invalid다() {
    assertThatThrownBy(() -> create("sum", null))
        .isInstanceOf(InvalidRequestException.class)
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_BILLABLE_METRIC);
    assertThatThrownBy(() -> create("sum", " ")).isInstanceOf(InvalidRequestException.class);
    verify(billableMetricRepository, never()).saveAndFlush(any());
  }

  @Test
  void SUM인데_target_property가_없으면_target_property를_errors에_담는다() {
    assertThatThrownBy(() -> create("sum", null))
        .isInstanceOfSatisfying(
            InvalidRequestException.class,
            exception ->
                assertThat(exception.getErrors())
                    .extracting(FieldError::field)
                    .containsExactly("target_property"));
  }

  @Test
  void 같은_코드의_미터가_이미_있으면_AlreadyExists다() {
    when(billableMetricRepository.existsById(
            new BillableMetricId(ORGANIZATION_ID, BILLABLE_METRIC_CODE)))
        .thenReturn(true);

    assertThatThrownBy(() -> create("sum", "token"))
        .isInstanceOf(ConflictException.class)
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(ErrorCode.BILLABLE_METRIC_ALREADY_EXISTS);
    verify(billableMetricRepository, never()).saveAndFlush(any());
  }

  @Test
  void 확인과_INSERT_사이의_경합도_AlreadyExists로_바뀐다() {
    when(billableMetricRepository.existsById(
            new BillableMetricId(ORGANIZATION_ID, BILLABLE_METRIC_CODE)))
        .thenReturn(false);
    when(billableMetricRepository.saveAndFlush(any())).thenThrow(violation("billable_metric_pk"));

    assertThatThrownBy(() -> create("sum", "token"))
        .isInstanceOf(ConflictException.class)
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(ErrorCode.BILLABLE_METRIC_ALREADY_EXISTS);
  }

  @Test
  void 미등록_도입사의_제약_위반은_400_예외로_바뀐다() {
    when(billableMetricRepository.existsById(
            new BillableMetricId(ORGANIZATION_ID, BILLABLE_METRIC_CODE)))
        .thenReturn(false);
    when(billableMetricRepository.saveAndFlush(any()))
        .thenThrow(violation("billable_metric_organization_fk"));

    assertThatThrownBy(() -> create("sum", "token"))
        .isInstanceOf(InvalidRequestException.class)
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(ErrorCode.UNKNOWN_ORGANIZATION);
  }

  private DataIntegrityViolationException violation(String constraintName) {
    return new DataIntegrityViolationException(
        constraintName,
        new ConstraintViolationException("rejected", new SQLException(), constraintName));
  }

  @Test
  void 정상_등록이면_미터가_저장되고_저장된_모양이_응답이_된다() {
    when(billableMetricRepository.existsById(
            new BillableMetricId(ORGANIZATION_ID, BILLABLE_METRIC_CODE)))
        .thenReturn(false);

    BillableMetricResponse response = create("sum", "token");

    ArgumentCaptor<BillableMetric> saved = ArgumentCaptor.forClass(BillableMetric.class);
    verify(billableMetricRepository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getOrganizationId()).isEqualTo(ORGANIZATION_ID);
    assertThat(saved.getValue().getCode()).isEqualTo(BILLABLE_METRIC_CODE);
    assertThat(saved.getValue().getName()).isEqualTo("토큰 사용량");
    assertThat(saved.getValue().getEventType()).isEqualTo("chat_completion");
    assertThat(saved.getValue().getAggregation()).isEqualTo("sum");
    assertThat(saved.getValue().getTargetProperty()).isEqualTo("token");
    assertThat(response.code()).isEqualTo(BILLABLE_METRIC_CODE);
    assertThat(response.targetProperty()).isEqualTo("token");
  }

  @Test
  void 목록_조회는_저장소의_code_순_목록을_응답으로_바꾼다() {
    when(billableMetricRepository.findByOrganizationIdOrderByCodeAsc(ORGANIZATION_ID))
        .thenReturn(
            List.of(
                new BillableMetric(
                    ORGANIZATION_ID, "api-calls", "호출 수", "chat_completion", "sum", "calls"),
                new BillableMetric(
                    ORGANIZATION_ID,
                    BILLABLE_METRIC_CODE,
                    "토큰 사용량",
                    "chat_completion",
                    "sum",
                    "token")));

    ListBillableMetricsResponse response = billableMetricService.list(ORGANIZATION_ID);

    assertThat(response.billableMetrics())
        .extracting(BillableMetricResponse::code)
        .containsExactly("api-calls", BILLABLE_METRIC_CODE);
  }

  private BillableMetricResponse create(String aggregation, String targetProperty) {
    return billableMetricService.create(
        ORGANIZATION_ID,
        new CreateBillableMetricRequest(
            BILLABLE_METRIC_CODE, "토큰 사용량", "chat_completion", aggregation, targetProperty));
  }
}
