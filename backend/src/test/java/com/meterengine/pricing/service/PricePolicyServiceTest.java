package com.meterengine.pricing.service;

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
import com.meterengine.global.error.NotFoundException;
import com.meterengine.metric.entity.BillableMetricId;
import com.meterengine.metric.repository.BillableMetricRepository;
import com.meterengine.pricing.dto.CreatePricePolicyRequest;
import com.meterengine.pricing.dto.PricePolicyResponse;
import com.meterengine.pricing.entity.PricePolicy;
import com.meterengine.pricing.entity.PricePolicyId;
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
  private static final String BILLABLE_METRIC_CODE = "token-usage";

  @Mock private PricePolicyRepository pricePolicyRepository;
  @Mock private BillableMetricRepository billableMetricRepository;

  private PricePolicyService service;

  @BeforeEach
  void setUp() {
    service = new PricePolicyService(pricePolicyRepository, billableMetricRepository);
  }

  @Test
  void 미터가_없으면_NotFound다() {
    when(billableMetricRepository.existsById(new BillableMetricId(ORG_ID, BILLABLE_METRIC_CODE)))
        .thenReturn(false);

    assertThatThrownBy(() -> create(List.of()))
        .isInstanceOf(NotFoundException.class)
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(ErrorCode.BILLABLE_METRIC_NOT_FOUND);
    verify(pricePolicyRepository, never()).saveAndFlush(any());
  }

  @Test
  void 정책이_이미_있으면_AlreadyExists다() {
    billableMetricExists();
    when(pricePolicyRepository.existsById(new PricePolicyId(ORG_ID, BILLABLE_METRIC_CODE)))
        .thenReturn(true);

    assertThatThrownBy(() -> create(List.of()))
        .isInstanceOf(ConflictException.class)
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(ErrorCode.PRICE_POLICY_ALREADY_EXISTS);
    verify(pricePolicyRepository, never()).saveAndFlush(any());
  }

  @Test
  void 확인과_INSERT_사이의_경합도_AlreadyExists로_바뀐다() {
    billableMetricExists();
    when(pricePolicyRepository.existsById(new PricePolicyId(ORG_ID, BILLABLE_METRIC_CODE)))
        .thenReturn(false);
    when(pricePolicyRepository.saveAndFlush(any()))
        .thenThrow(new DataIntegrityViolationException("pk"));

    assertThatThrownBy(() -> create(List.of()))
        .isInstanceOf(ConflictException.class)
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(ErrorCode.PRICE_POLICY_ALREADY_EXISTS);
  }

  @Test
  void 선언의_중복이나_빈_키는_Invalid다() {
    billableMetricExists();

    assertThatThrownBy(() -> create(List.of("model", "model")))
        .isInstanceOf(InvalidRequestException.class)
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_PRICE_POLICY);
    assertThatThrownBy(() -> create(List.of(" ")))
        .isInstanceOf(InvalidRequestException.class)
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_PRICE_POLICY);
    verify(pricePolicyRepository, never()).saveAndFlush(any());
  }

  @Test
  void 선언의_중복이나_빈_키는_dimension_properties를_errors에_담는다() {
    billableMetricExists();

    assertThatThrownBy(() -> create(List.of("model", "model")))
        .isInstanceOfSatisfying(
            InvalidRequestException.class,
            exception ->
                assertThat(exception.getErrors())
                    .extracting(FieldError::field)
                    .containsExactly("dimension_properties"));
    assertThatThrownBy(() -> create(List.of(" ")))
        .isInstanceOfSatisfying(
            InvalidRequestException.class,
            exception ->
                assertThat(exception.getErrors())
                    .extracting(FieldError::field)
                    .containsExactly("dimension_properties"));
  }

  @Test
  void 정상_등록이면_정책이_저장되고_저장된_모양이_응답이_된다() {
    billableMetricExists();
    when(pricePolicyRepository.existsById(new PricePolicyId(ORG_ID, BILLABLE_METRIC_CODE)))
        .thenReturn(false);

    PricePolicyResponse response = create(List.of("model"));

    ArgumentCaptor<PricePolicy> saved = ArgumentCaptor.forClass(PricePolicy.class);
    verify(pricePolicyRepository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getOrganizationId()).isEqualTo(ORG_ID);
    assertThat(saved.getValue().getBillableMetricCode()).isEqualTo(BILLABLE_METRIC_CODE);
    assertThat(saved.getValue().getDimensionProperties()).containsExactly("model");
    assertThat(response.billableMetricCode()).isEqualTo(BILLABLE_METRIC_CODE);
    assertThat(response.dimensionProperties()).containsExactly("model");
  }

  private void billableMetricExists() {
    when(billableMetricRepository.existsById(new BillableMetricId(ORG_ID, BILLABLE_METRIC_CODE)))
        .thenReturn(true);
  }

  private PricePolicyResponse create(List<String> properties) {
    return service.create(ORG_ID, BILLABLE_METRIC_CODE, new CreatePricePolicyRequest(properties));
  }
}
