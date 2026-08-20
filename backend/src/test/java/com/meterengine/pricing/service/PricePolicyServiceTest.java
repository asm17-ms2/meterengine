package com.meterengine.pricing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meterengine.metric.entity.BillableMetricId;
import com.meterengine.metric.repository.BillableMetricRepository;
import com.meterengine.pricing.dto.PricePolicyResponse;
import com.meterengine.pricing.dto.SavePricePolicyRequest;
import com.meterengine.pricing.dto.SavePricePolicyRequest.RateEntry;
import com.meterengine.pricing.exception.InvalidPricePolicyException;
import com.meterengine.pricing.exception.MetricNotFoundException;
import com.meterengine.pricing.exception.PricePolicyAlreadyExistsException;
import com.meterengine.pricing.repository.PricePolicyRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

/**
 * 등록의 검증과 예외 분기를 본다 (MS2-157).
 *
 * <p>저장이 실제로 되는지, HTTP 상태로 어떻게 나가는지는 {@code PricePolicyIntegrationTest}의 몫이다.
 */
@ExtendWith(MockitoExtension.class)
class PricePolicyServiceTest {

  private static final UUID ORG_ID = UUID.randomUUID();
  private static final String METRIC = "token-usage";
  private static final RateEntry BASE_RATE = new RateEntry(Map.of(), new BigDecimal("0.5"));

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

    assertThatThrownBy(() -> register(List.of(), List.of(BASE_RATE)))
        .isInstanceOf(MetricNotFoundException.class);
    verify(policies, never()).insertPolicy(any(), anyString(), any());
  }

  @Test
  void 정책이_이미_있으면_AlreadyExists다() {
    metricExists();
    when(policies.exists(ORG_ID, METRIC)).thenReturn(true);

    assertThatThrownBy(() -> register(List.of(), List.of(BASE_RATE)))
        .isInstanceOf(PricePolicyAlreadyExistsException.class);
    verify(policies, never()).insertPolicy(any(), anyString(), any());
  }

  @Test
  void 확인과_INSERT_사이의_경합도_AlreadyExists로_바뀐다() {
    metricExists();
    when(policies.exists(ORG_ID, METRIC)).thenReturn(false);
    doThrow(new DuplicateKeyException("pk")).when(policies).insertPolicy(ORG_ID, METRIC, List.of());

    assertThatThrownBy(() -> register(List.of(), List.of(BASE_RATE)))
        .isInstanceOf(PricePolicyAlreadyExistsException.class);
  }

  @Test
  void 기본_단가_행이_없으면_Invalid다() {
    metricExists();

    assertThatThrownBy(
            () ->
                register(
                    List.of("model"),
                    List.of(new RateEntry(Map.of("model", "opus5"), new BigDecimal("2.0")))))
        .isInstanceOf(InvalidPricePolicyException.class)
        .hasMessageContaining("base rate");
  }

  @Test
  void 조합의_키_집합이_선언과_다르면_Invalid다() {
    metricExists();

    assertThatThrownBy(
            () ->
                register(
                    List.of("model"),
                    List.of(
                        BASE_RATE, new RateEntry(Map.of("region", "kr"), new BigDecimal("2.0")))))
        .isInstanceOf(InvalidPricePolicyException.class)
        .hasMessageContaining("do not match");
  }

  @Test
  void 같은_조합이_두_번_오면_Invalid다() {
    metricExists();

    assertThatThrownBy(
            () ->
                register(
                    List.of("model"),
                    List.of(
                        BASE_RATE,
                        new RateEntry(Map.of("model", "opus5"), new BigDecimal("2.0")),
                        new RateEntry(Map.of("model", "opus5"), new BigDecimal("3.0")))))
        .isInstanceOf(InvalidPricePolicyException.class)
        .hasMessageContaining("duplicate");
  }

  @Test
  void 선언의_중복이나_빈_키도_Invalid다() {
    metricExists();

    assertThatThrownBy(() -> register(List.of("model", "model"), List.of(BASE_RATE)))
        .isInstanceOf(InvalidPricePolicyException.class);
    assertThatThrownBy(() -> register(List.of(" "), List.of(BASE_RATE)))
        .isInstanceOf(InvalidPricePolicyException.class);
  }

  @Test
  void 정상_등록이면_정책과_단가가_저장되고_저장된_모양이_응답이_된다() {
    metricExists();
    when(policies.exists(ORG_ID, METRIC)).thenReturn(false);
    RateEntry opusRate = new RateEntry(Map.of("model", "opus5"), new BigDecimal("2.0"));

    PricePolicyResponse response = register(List.of("model"), List.of(BASE_RATE, opusRate));

    verify(policies).insertPolicy(ORG_ID, METRIC, List.of("model"));
    verify(policies).insertRate(ORG_ID, METRIC, Map.of(), new BigDecimal("0.5"));
    verify(policies).insertRate(ORG_ID, METRIC, Map.of("model", "opus5"), new BigDecimal("2.0"));
    assertThat(response.metricCode()).isEqualTo(METRIC);
    assertThat(response.rates()).hasSize(2);
  }

  private void metricExists() {
    when(metrics.existsById(new BillableMetricId(ORG_ID, METRIC))).thenReturn(true);
  }

  private PricePolicyResponse register(List<String> properties, List<RateEntry> rates) {
    return service.register(ORG_ID, METRIC, new SavePricePolicyRequest(properties, rates));
  }
}
