package com.meterengine.pricing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meterengine.metric.entity.BillableMetricId;
import com.meterengine.metric.repository.BillableMetricRepository;
import com.meterengine.pricing.dto.PricePolicyResponse;
import com.meterengine.pricing.dto.SavePricePolicyRequest;
import com.meterengine.pricing.entity.PricePolicy;
import com.meterengine.pricing.entity.PricePolicyId;
import com.meterengine.pricing.exception.InvalidPricePolicyException;
import com.meterengine.pricing.exception.MetricNotFoundException;
import com.meterengine.pricing.exception.PricePolicyAlreadyExistsException;
import com.meterengine.pricing.repository.PricePolicyRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * 등록의 검증과 예외 분기를 본다 (MS2-157).
 *
 * <p>저장이 실제로 되는지, HTTP 상태로 어떻게 나가는지는 {@code PricePolicyIntegrationTest}의 몫이다.
 */
@ExtendWith(MockitoExtension.class)
class PricePolicyServiceTest {

  private static final UUID ORG_ID = UUID.randomUUID();
  private static final String METRIC = "token-usage";

  @Mock private PricePolicyRepository policies;
  @Mock private BillableMetricRepository metrics;

  private PricePolicyService service;

  @BeforeEach
  void setUp() {
    service = new PricePolicyService(policies, metrics);
  }

  @Test
  void 미터가_없으면_MetricNotFound다() {
    when(metrics.existsById(new BillableMetricId(ORG_ID, METRIC))).thenReturn(false);

    assertThatThrownBy(() -> register(List.of())).isInstanceOf(MetricNotFoundException.class);
    verify(policies, never()).saveAndFlush(any());
  }

  @Test
  void 정책이_이미_있으면_AlreadyExists다() {
    metricExists();
    when(policies.existsById(new PricePolicyId(ORG_ID, METRIC))).thenReturn(true);

    assertThatThrownBy(() -> register(List.of()))
        .isInstanceOf(PricePolicyAlreadyExistsException.class);
    verify(policies, never()).saveAndFlush(any());
  }

  @Test
  void 확인과_INSERT_사이의_경합도_AlreadyExists로_바뀐다() {
    metricExists();
    when(policies.existsById(new PricePolicyId(ORG_ID, METRIC))).thenReturn(false);
    when(policies.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("pk"));

    assertThatThrownBy(() -> register(List.of()))
        .isInstanceOf(PricePolicyAlreadyExistsException.class);
  }

  @Test
  void 선언의_중복이나_빈_키는_Invalid다() {
    metricExists();

    assertThatThrownBy(() -> register(List.of("model", "model")))
        .isInstanceOf(InvalidPricePolicyException.class)
        .hasMessageContaining("duplicate");
    assertThatThrownBy(() -> register(List.of(" ")))
        .isInstanceOf(InvalidPricePolicyException.class)
        .hasMessageContaining("blank");
    verify(policies, never()).saveAndFlush(any());
  }

  @Test
  void 정상_등록이면_정책이_저장되고_저장된_모양이_응답이_된다() {
    metricExists();
    when(policies.existsById(new PricePolicyId(ORG_ID, METRIC))).thenReturn(false);

    PricePolicyResponse response = register(List.of("model"));

    ArgumentCaptor<PricePolicy> saved = ArgumentCaptor.forClass(PricePolicy.class);
    verify(policies).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getOrganizationId()).isEqualTo(ORG_ID);
    assertThat(saved.getValue().getMetricCode()).isEqualTo(METRIC);
    assertThat(saved.getValue().getDimensionProperties()).containsExactly("model");
    assertThat(response.metricCode()).isEqualTo(METRIC);
    assertThat(response.dimensionProperties()).containsExactly("model");
  }

  private void metricExists() {
    when(metrics.existsById(new BillableMetricId(ORG_ID, METRIC))).thenReturn(true);
  }

  private PricePolicyResponse register(List<String> properties) {
    return service.register(ORG_ID, METRIC, new SavePricePolicyRequest(properties));
  }
}
