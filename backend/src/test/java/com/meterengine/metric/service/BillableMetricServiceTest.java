package com.meterengine.metric.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.meterengine.audit.service.AuditLogService;
import com.meterengine.event.api.EventPresence;
import com.meterengine.global.error.BusinessException;
import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ErrorResponse.FieldError;
import com.meterengine.global.error.InvalidRequestException;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.metric.dto.BillableMetricResponse;
import com.meterengine.metric.dto.CreateBillableMetricRequest;
import com.meterengine.metric.dto.ListBillableMetricsResponse;
import com.meterengine.metric.dto.UpdateBillableMetricRequest;
import com.meterengine.metric.entity.BillableMetric;
import com.meterengine.metric.entity.BillableMetricId;
import com.meterengine.metric.repository.BillableMetricPeriodRepository;
import com.meterengine.metric.repository.BillableMetricRepository;
import com.meterengine.organization.entity.Organization;
import com.meterengine.organization.repository.OrganizationRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class BillableMetricServiceTest {

  private static final UUID ORGANIZATION_ID = UUID.randomUUID();
  private static final String BILLABLE_METRIC_CODE = "token-usage";

  @Mock private BillableMetricRepository billableMetricRepository;
  @Mock private EventPresence eventService;
  @Mock private BillableMetricPeriodRepository periodRepository;
  @Mock private OrganizationRepository organizationRepository;
  @Mock private AuditLogService auditLogService;
  @Mock private EntityManager entityManager;

  private BillableMetricService billableMetricService;

  @BeforeEach
  void setUp() {
    billableMetricService =
        new BillableMetricService(
            billableMetricRepository,
            eventService,
            periodRepository,
            organizationRepository,
            auditLogService,
            entityManager);
    org.mockito.Mockito.lenient()
        .when(organizationRepository.findForUpdateById(ORGANIZATION_ID))
        .thenReturn(Optional.of(org.mockito.Mockito.mock(Organization.class)));
  }

  @Test
  void 지원하지_않는_집계_함수는_Invalid다() {
    assertThatThrownBy(() -> create("unsupported", "token"))
        .isInstanceOf(InvalidRequestException.class)
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(ErrorCode.INVALID_BILLABLE_METRIC);
    verify(billableMetricRepository, never()).saveAndFlush(any());
  }

  @Test
  void 지원하지_않는_집계_함수는_aggregation을_errors에_담는다() {
    assertThatThrownBy(() -> create("unsupported", "token"))
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
  void 차원_키의_중복이나_빈_키는_Invalid고_dimension_properties를_errors에_담는다() {
    for (List<String> dimensionProperties : List.of(List.of("model", "model"), List.of(" "))) {
      assertThatThrownBy(() -> create("sum", "token", dimensionProperties))
          .isInstanceOfSatisfying(
              InvalidRequestException.class,
              exception -> {
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_BILLABLE_METRIC);
                assertThat(exception.getErrors())
                    .extracting(FieldError::field)
                    .containsExactly("dimension_properties");
              });
    }
    verify(billableMetricRepository, never()).saveAndFlush(any());
  }

  @Test
  void 차원_키가_null이면_빈_배열로_저장된다() {
    create("sum", "token", null);

    ArgumentCaptor<BillableMetric> saved = ArgumentCaptor.forClass(BillableMetric.class);
    verify(billableMetricRepository).saveAndFlush(saved.capture());
    assertThat(saved.getValue().getDimensionProperties()).isEmpty();
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
  void 등록_저장에서_난_DataIntegrityViolationException은_바꾸지_않고_그대로_올려_보낸다() {
    DataIntegrityViolationException violation =
        new DataIntegrityViolationException("billable_metric_pk");
    when(billableMetricRepository.saveAndFlush(any())).thenThrow(violation);

    assertThatThrownBy(() -> create("sum", "token")).isSameAs(violation);
  }

  @Test
  void 같은_이벤트와_속성에도_다른_미터를_등록할_수_있다() {
    assertThat(create("sum", "token").aggregation()).isEqualTo("sum");
    verify(billableMetricRepository).saveAndFlush(any());
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
                    ORGANIZATION_ID,
                    "api-calls",
                    "호출 수",
                    "chat_completion",
                    "sum",
                    "calls",
                    List.of()),
                storedBillableMetric()));

    ListBillableMetricsResponse response = billableMetricService.list(ORGANIZATION_ID);

    assertThat(response.billableMetrics())
        .extracting(BillableMetricResponse::code)
        .containsExactly("api-calls", BILLABLE_METRIC_CODE);
  }

  @Test
  void 없는_미터를_수정하면_NotFound다() {
    when(billableMetricRepository.findById(
            new BillableMetricId(ORGANIZATION_ID, BILLABLE_METRIC_CODE)))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> update("chat_completion", "token"))
        .isInstanceOf(NotFoundException.class)
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(ErrorCode.BILLABLE_METRIC_NOT_FOUND);
  }

  @Test
  void 수정도_지원하지_않는_집계면_조회_전에_Invalid다() {
    assertThatThrownBy(
            () ->
                billableMetricService.update(
                    ORGANIZATION_ID,
                    BILLABLE_METRIC_CODE,
                    new UpdateBillableMetricRequest(
                        "이름", "chat_completion", "unsupported", "token", List.of())))
        .isInstanceOf(InvalidRequestException.class);
    verify(billableMetricRepository, never()).findById(any());
  }

  @Test
  void 수정은_요청_값으로_덮어쓰고_바뀐_모양이_응답이_된다() {
    BillableMetric stored = storedBillableMetric();
    when(billableMetricRepository.findById(stored.getId())).thenReturn(Optional.of(stored));

    BillableMetricResponse response = update("embedding", "chars");

    assertThat(stored.getName()).isEqualTo("바뀐 이름");
    assertThat(stored.getEventType()).isEqualTo("embedding");
    assertThat(stored.getTargetProperty()).isEqualTo("chars");
    assertThat(response.code()).isEqualTo(BILLABLE_METRIC_CODE);
    assertThat(response.eventType()).isEqualTo("embedding");
  }

  @Test
  void event_type과_target_property가_그대로면_이벤트와_중복을_확인하지_않는다() {
    BillableMetric stored = storedBillableMetric();
    when(billableMetricRepository.findById(stored.getId())).thenReturn(Optional.of(stored));

    update("chat_completion", "token");

    verify(eventService, never()).exists(any(), any(), any());
    verify(billableMetricRepository, never())
        .existsByOrganizationIdAndEventTypeAndTargetProperty(any(), any(), any());
  }

  @Test
  void 이벤트가_있는_미터의_event_type과_target_property는_바꿀_수_없다() {
    BillableMetric stored = storedBillableMetric();
    when(billableMetricRepository.findById(stored.getId())).thenReturn(Optional.of(stored));
    when(eventService.exists(ORGANIZATION_ID, "chat_completion")).thenReturn(true);

    assertThatThrownBy(() -> update("embedding", "chars"))
        .isInstanceOf(ConflictException.class)
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(ErrorCode.BILLABLE_METRIC_HAS_EVENTS);
    assertThat(stored.getEventType()).isEqualTo("chat_completion");
  }

  @Test
  void 이벤트_유무는_도입사_잠금_전에_확인한다() {
    BillableMetric stored = storedBillableMetric();
    when(billableMetricRepository.findById(stored.getId())).thenReturn(Optional.of(stored));

    update("embedding", "chars");

    InOrder order = inOrder(eventService, organizationRepository);
    order.verify(eventService).exists(ORGANIZATION_ID, "chat_completion");
    order.verify(organizationRepository).findForUpdateById(ORGANIZATION_ID);
  }

  @Test
  void 잠금_뒤_다시_읽은_event_type이_다르면_그_이벤트를_다시_확인한다() {
    BillableMetric stored = storedBillableMetric();
    when(billableMetricRepository.findById(stored.getId())).thenReturn(Optional.of(stored));
    doAnswer(
            invocation -> {
              stored.update(
                  "토큰 사용량",
                  "legacy_completion",
                  "sum",
                  "token",
                  List.of(),
                  BigDecimal.ONE,
                  "none",
                  0,
                  null);
              return null;
            })
        .when(entityManager)
        .refresh(stored);
    when(eventService.exists(ORGANIZATION_ID, "chat_completion")).thenReturn(false);
    when(eventService.exists(ORGANIZATION_ID, "legacy_completion")).thenReturn(true);

    assertThatThrownBy(() -> update("embedding", "chars"))
        .isInstanceOf(ConflictException.class)
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(ErrorCode.BILLABLE_METRIC_HAS_EVENTS);
    InOrder order = inOrder(eventService, organizationRepository, entityManager);
    order.verify(eventService).exists(ORGANIZATION_ID, "chat_completion");
    order.verify(organizationRepository).findForUpdateById(ORGANIZATION_ID);
    order.verify(entityManager).refresh(stored);
    order.verify(eventService).exists(ORGANIZATION_ID, "legacy_completion");
  }

  @Test
  void 이벤트가_없으면_다른_미터와_같은_이벤트와_속성으로_변경할_수_있다() {
    BillableMetric stored = storedBillableMetric();
    when(billableMetricRepository.findById(stored.getId())).thenReturn(Optional.of(stored));
    assertThat(update("embedding", "chars").targetProperty()).isEqualTo("chars");
  }

  @Test
  void 수정_flush에서_난_DataIntegrityViolationException은_바꾸지_않고_그대로_올려_보낸다() {
    BillableMetric stored = storedBillableMetric();
    when(billableMetricRepository.findById(stored.getId())).thenReturn(Optional.of(stored));
    DataIntegrityViolationException violation =
        new DataIntegrityViolationException(
            "billable_metric_organization_event_type_target_property_unique");
    doThrow(violation).when(billableMetricRepository).flush();

    assertThatThrownBy(() -> update("embedding", "chars")).isSameAs(violation);
  }

  @Test
  void 없는_미터를_삭제하면_NotFound다() {
    when(billableMetricRepository.findById(
            new BillableMetricId(ORGANIZATION_ID, BILLABLE_METRIC_CODE)))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> billableMetricService.delete(ORGANIZATION_ID, BILLABLE_METRIC_CODE))
        .isInstanceOf(NotFoundException.class)
        .extracting(exception -> ((BusinessException) exception).getErrorCode())
        .isEqualTo(ErrorCode.BILLABLE_METRIC_NOT_FOUND);
  }

  @Test
  void 미터를_보관해도_행과_현재_월_효력은_유지한다() {
    BillableMetric stored = storedBillableMetric();
    when(billableMetricRepository.findById(stored.getId())).thenReturn(Optional.of(stored));
    billableMetricService.delete(ORGANIZATION_ID, BILLABLE_METRIC_CODE);
    assertThat(stored.getArchivedAt()).isNotNull();
    assertThat(stored.getInactiveFrom())
        .isEqualTo(BillableMetricUsageService.currentMonth().plusMonths(1).toString());
    verify(billableMetricRepository, never()).delete(any());
    verify(periodRepository).save(any());
  }

  private BillableMetric storedBillableMetric() {
    return new BillableMetric(
        ORGANIZATION_ID,
        BILLABLE_METRIC_CODE,
        "토큰 사용량",
        "chat_completion",
        "sum",
        "token",
        List.of());
  }

  private BillableMetricResponse update(String eventType, String targetProperty) {
    return billableMetricService.update(
        ORGANIZATION_ID,
        BILLABLE_METRIC_CODE,
        new UpdateBillableMetricRequest("바뀐 이름", eventType, "sum", targetProperty, List.of()));
  }

  private BillableMetricResponse create(String aggregation, String targetProperty) {
    return create(aggregation, targetProperty, List.of());
  }

  private BillableMetricResponse create(
      String aggregation, String targetProperty, List<String> dimensionProperties) {
    return billableMetricService.create(
        ORGANIZATION_ID,
        new CreateBillableMetricRequest(
            BILLABLE_METRIC_CODE,
            "토큰 사용량",
            "chat_completion",
            aggregation,
            targetProperty,
            dimensionProperties));
  }
}
