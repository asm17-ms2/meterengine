package com.meterengine.metric.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meterengine.metric.dto.BillableMetricResponse;
import com.meterengine.metric.dto.CreateBillableMetricRequest;
import com.meterengine.metric.dto.ListBillableMetricsResponse;
import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.metric.entity.BillableMetricId;
import com.meterengine.metric.exception.InvalidBillableMetricException;
import com.meterengine.metric.exception.MetricAlreadyExistsException;
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

  private static final UUID ORG_ID = UUID.randomUUID();
  private static final String CODE = "token-usage";

  @Mock private BillableMetricRepository billableMetricRepository;

  private BillableMetricService service;

  @BeforeEach
  void setUp() {
    service = new BillableMetricService(billableMetricRepository);
  }

  @Test
  void SUM이_아닌_집계_함수는_Invalid다() {
    assertThatThrownBy(() -> create("COUNT", "token"))
        .isInstanceOf(InvalidBillableMetricException.class)
        .hasMessageContaining("SUM");
    verify(billableMetricRepository, never()).saveAndFlush(any());
  }

  @Test
  void SUM인데_target_property가_없으면_Invalid다() {
    assertThatThrownBy(() -> create("SUM", null))
        .isInstanceOf(InvalidBillableMetricException.class)
        .hasMessageContaining("target_property");
    assertThatThrownBy(() -> create("SUM", " ")).isInstanceOf(InvalidBillableMetricException.class);
    verify(billableMetricRepository, never()).saveAndFlush(any());
  }

  @Test
  void 같은_코드의_미터가_이미_있으면_AlreadyExists다() {
    when(billableMetricRepository.existsById(new BillableMetricId(ORG_ID, CODE))).thenReturn(true);

    assertThatThrownBy(() -> create("SUM", "token"))
        .isInstanceOf(MetricAlreadyExistsException.class);
    verify(billableMetricRepository, never()).saveAndFlush(any());
  }

  @Test
  void 확인과_INSERT_사이의_경합도_AlreadyExists로_바뀐다() {
    when(billableMetricRepository.existsById(new BillableMetricId(ORG_ID, CODE))).thenReturn(false);
    when(billableMetricRepository.saveAndFlush(any())).thenThrow(violation("billable_metric_pkey"));

    assertThatThrownBy(() -> create("SUM", "token"))
        .isInstanceOf(MetricAlreadyExistsException.class);
  }

  @Test
  void 미등록_도입사의_제약_위반은_그대로_나간다() {
    when(billableMetricRepository.existsById(new BillableMetricId(ORG_ID, CODE))).thenReturn(false);
    when(billableMetricRepository.saveAndFlush(any()))
        .thenThrow(violation("billable_metric_organization_id_fkey"));

    assertThatThrownBy(() -> create("SUM", "token"))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  private DataIntegrityViolationException violation(String constraintName) {
    return new DataIntegrityViolationException(
        constraintName,
        new ConstraintViolationException("rejected", new SQLException(), constraintName));
  }

  @Test
  void 정상_등록이면_미터가_저장되고_저장된_모양이_응답이_된다() {
    when(billableMetricRepository.existsById(new BillableMetricId(ORG_ID, CODE))).thenReturn(false);

    BillableMetricResponse response = create("SUM", "token");

    ArgumentCaptor<BillableMetric> saved = ArgumentCaptor.forClass(BillableMetric.class);
    verify(billableMetricRepository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getOrganizationId()).isEqualTo(ORG_ID);
    assertThat(saved.getValue().getCode()).isEqualTo(CODE);
    assertThat(saved.getValue().getName()).isEqualTo("토큰 사용량");
    assertThat(saved.getValue().getEventType()).isEqualTo("chat_completion");
    assertThat(saved.getValue().getAggregation()).isEqualTo("SUM");
    assertThat(saved.getValue().getTargetProperty()).isEqualTo("token");
    assertThat(response.code()).isEqualTo(CODE);
    assertThat(response.targetProperty()).isEqualTo("token");
  }

  @Test
  void 목록_조회는_저장소의_code_순_목록을_응답으로_바꾼다() {
    when(billableMetricRepository.findByOrganizationIdOrderByCodeAsc(ORG_ID))
        .thenReturn(
            List.of(
                new BillableMetric(ORG_ID, "api-calls", "호출 수", "chat_completion", "SUM", "calls"),
                new BillableMetric(ORG_ID, CODE, "토큰 사용량", "chat_completion", "SUM", "token")));

    ListBillableMetricsResponse response = service.list(ORG_ID);

    assertThat(response.billableMetrics())
        .extracting(BillableMetricResponse::code)
        .containsExactly("api-calls", CODE);
  }

  private BillableMetricResponse create(String aggregation, String targetProperty) {
    return service.create(
        ORG_ID,
        new CreateBillableMetricRequest(
            CODE, "토큰 사용량", "chat_completion", aggregation, targetProperty));
  }
}
