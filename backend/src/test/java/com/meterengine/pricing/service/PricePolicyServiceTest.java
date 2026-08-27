package com.meterengine.pricing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.metric.entity.BillableMetricId;
import com.meterengine.metric.repository.BillableMetricRepository;
import com.meterengine.pricing.dto.MetricPricePolicyResponse;
import com.meterengine.pricing.dto.PricePolicyListResponse;
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

  @Test
  void 목록의_순서는_미터_조회가_정한다() {
    when(metrics.findByOrganizationIdOrderByCodeAsc(ORG_ID))
        .thenReturn(List.of(metric("input-tokens"), metric("token-usage")));
    when(policies.findByOrganizationId(ORG_ID))
        .thenReturn(List.of(policy("token-usage", List.of()), policy("input-tokens", List.of())));

    PricePolicyListResponse response = service.list(ORG_ID);

    assertThat(response.pricePolicies())
        .extracting(MetricPricePolicyResponse::metricCode)
        .containsExactly("input-tokens", "token-usage");
  }

  private static BillableMetric metric(String code) {
    return new BillableMetric(ORG_ID, code, "토큰 사용량", "chat_completion", "SUM", "token");
  }

  private static PricePolicy policy(String code, List<String> dimensionProperties) {
    return new PricePolicy(ORG_ID, code, dimensionProperties);
  }

  private void metricExists() {
    when(metrics.existsById(new BillableMetricId(ORG_ID, METRIC))).thenReturn(true);
  }

  private PricePolicyResponse register(List<String> properties) {
    return service.register(ORG_ID, METRIC, new SavePricePolicyRequest(properties));
  }
}
