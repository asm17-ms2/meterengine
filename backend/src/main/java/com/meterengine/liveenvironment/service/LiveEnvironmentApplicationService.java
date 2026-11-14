package com.meterengine.liveenvironment.service;

import com.meterengine.account.service.AuthRequestRateLimitService;
import com.meterengine.audit.service.AuditActor;
import com.meterengine.audit.service.AuditLogService;
import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ErrorResponse.FieldError;
import com.meterengine.global.error.InvalidRequestException;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.global.security.AuthenticatedPrincipal;
import com.meterengine.global.security.BusinessEnvironmentContext;
import com.meterengine.global.security.OrganizationEnvironments;
import com.meterengine.liveenvironment.dto.CreateLiveEnvironmentApplicationRequest;
import com.meterengine.liveenvironment.dto.ListLiveEnvironmentApplicationsResponse;
import com.meterengine.liveenvironment.dto.LiveEnvironmentApplicationResponse;
import com.meterengine.liveenvironment.dto.UpdateLiveEnvironmentApplicationStatusRequest;
import com.meterengine.liveenvironment.entity.LiveEnvironmentApplication;
import com.meterengine.liveenvironment.repository.LiveEnvironmentApplicationRepository;
import com.meterengine.liveenvironment.service.BusinessRegistrationStatus.State;
import com.meterengine.organization.entity.Organization;
import com.meterengine.organization.repository.OrganizationRepository;
import com.meterengine.payment.dto.UpdateTossPaymentsCredentialRequest;
import com.meterengine.payment.service.TossPaymentsCredentialService;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class LiveEnvironmentApplicationService {

  private static final String LIVE_MODE = "live";
  private static final String TARGET_TYPE = "live_environment_application";

  private static final String BUSINESS_REGISTRATION_NUMBER_FIELD = "business_registration_number";
  private static final String REJECTION_REASON_FIELD = "rejection_reason";
  private static final String TOSS_PAYMENTS_FIELD_PREFIX = "toss_payments_";

  private static final String OPERATING = "operating";
  private static final String SUSPENDED = "suspended";
  private static final String UNCHECKED = "unchecked";

  private static final String NUMBER_FORMAT_MESSAGE = "사업자등록번호 열 자리를 다시 확인하세요";
  private static final String UNREGISTERED_MESSAGE = "국세청에 등록되지 않은 사업자등록번호입니다";
  private static final String CLOSED_MESSAGE = "폐업한 사업자의 등록번호로는 신청할 수 없습니다";
  private static final String REJECTION_REASON_REQUIRED_MESSAGE = "거절할 때는 사유를 적어야 합니다";
  private static final String REJECTION_REASON_UNEXPECTED_MESSAGE = "승인할 때는 사유를 보내지 않습니다";

  private final LiveEnvironmentApplicationRepository liveEnvironmentApplicationRepository;
  private final OrganizationRepository organizationRepository;
  private final OrganizationEnvironments organizationEnvironments;
  private final BusinessRegistrationLookup businessRegistrationLookup;
  private final TossPaymentsCredentialService tossPaymentsCredentialService;
  private final AuditLogService auditLogService;
  private final AuthRequestRateLimitService authRequestRateLimitService;
  private final TransactionTemplate transactionTemplate;

  LiveEnvironmentApplicationService(
      LiveEnvironmentApplicationRepository liveEnvironmentApplicationRepository,
      OrganizationRepository organizationRepository,
      OrganizationEnvironments organizationEnvironments,
      BusinessRegistrationLookup businessRegistrationLookup,
      TossPaymentsCredentialService tossPaymentsCredentialService,
      AuditLogService auditLogService,
      AuthRequestRateLimitService authRequestRateLimitService,
      PlatformTransactionManager platformTransactionManager) {
    this.liveEnvironmentApplicationRepository = liveEnvironmentApplicationRepository;
    this.organizationRepository = organizationRepository;
    this.organizationEnvironments = organizationEnvironments;
    this.businessRegistrationLookup = businessRegistrationLookup;
    this.tossPaymentsCredentialService = tossPaymentsCredentialService;
    this.auditLogService = auditLogService;
    this.authRequestRateLimitService = authRequestRateLimitService;
    this.transactionTemplate = new TransactionTemplate(platformTransactionManager);
  }

  // 국세청과 토스페이먼츠를 부르는 동안 DB 연결과 조직 행 잠금을 쥐지 않으려고 트랜잭션을 마지막 저장에만 건다.
  // 실결제 키는 확인만 하고 신청 행에 둔다. 승인 전에는 실제 환경의 키로 쓰이지 않는다.
  public LiveEnvironmentApplicationResponse create(
      UUID organizationId,
      AuthenticatedPrincipal principal,
      CreateLiveEnvironmentApplicationRequest request) {
    String businessRegistrationNumber =
        request.businessRegistrationNumber().replaceAll("[-\\s]", "");
    if (!BusinessRegistrationNumber.isValid(businessRegistrationNumber)) {
      throw invalidNumber(NUMBER_FORMAT_MESSAGE);
    }
    requireApplicable(get(organizationId));
    // 여기부터 밖을 부른다. 거절돼 신청이 남지 않는 시도도 센다.
    authRequestRateLimitService.apply(organizationId);

    BusinessRegistrationStatus businessRegistrationStatus =
        businessRegistrationLookup.find(businessRegistrationNumber);
    String businessStatus =
        switch (businessRegistrationStatus.state()) {
          case UNREGISTERED -> throw invalidNumber(UNREGISTERED_MESSAGE);
          case CLOSED -> throw invalidNumber(CLOSED_MESSAGE);
          case OPERATING -> OPERATING;
          case SUSPENDED -> SUSPENDED;
          case UNAVAILABLE -> UNCHECKED;
        };
    String taxType =
        businessRegistrationStatus.state() == State.UNAVAILABLE
            ? null
            : businessRegistrationStatus.taxType();

    check(request);

    return transactionTemplate.execute(
        status -> {
          Organization organization =
              organizationRepository
                  .findForUpdateById(organizationId)
                  .orElseThrow(() -> new NotFoundException(ErrorCode.ORGANIZATION_NOT_FOUND));
          requireApplicable(organization);
          LiveEnvironmentApplication application =
              liveEnvironmentApplicationRepository.save(
                  new LiveEnvironmentApplication(
                      UUID.randomUUID(),
                      organizationId,
                      businessRegistrationNumber,
                      request.tossPaymentsMerchantId().strip(),
                      request.tossPaymentsClientKey(),
                      request.tossPaymentsSecretKey(),
                      businessStatus,
                      taxType,
                      principal.userAccountId(),
                      now()));
          auditLogService.record(
              organizationId,
              "live_environment_application_created",
              TARGET_TYPE,
              application.getId().toString(),
              Map.of("business_status", businessStatus));
          return LiveEnvironmentApplicationResponse.from(application, organization.getName());
        });
  }

  @Transactional(readOnly = true)
  public ListLiveEnvironmentApplicationsResponse list(UUID organizationId) {
    String organizationName = get(organizationId).getName();
    return new ListLiveEnvironmentApplicationsResponse(
        liveEnvironmentApplicationRepository
            .findByOrganizationIdOrderByCreatedAtDescIdDesc(organizationId)
            .stream()
            .map(
                application ->
                    LiveEnvironmentApplicationResponse.from(application, organizationName))
            .toList());
  }

  // 운영자가 보는 목록이다. status가 null이면 모든 신청을 최근 것부터 돌려준다.
  @Transactional(readOnly = true)
  public ListLiveEnvironmentApplicationsResponse list(String status) {
    List<LiveEnvironmentApplication> applications =
        status == null
            ? liveEnvironmentApplicationRepository.findByOrderByCreatedAtDescIdDesc()
            : liveEnvironmentApplicationRepository.findByStatusOrderByCreatedAtDescIdDesc(status);
    Map<UUID, String> organizationNames =
        organizationRepository
            .findAllById(
                applications.stream()
                    .map(LiveEnvironmentApplication::getOrganizationId)
                    .distinct()
                    .toList())
            .stream()
            .collect(Collectors.toMap(Organization::getId, Organization::getName));
    Function<LiveEnvironmentApplication, LiveEnvironmentApplicationResponse> respond =
        application ->
            LiveEnvironmentApplicationResponse.from(
                application, organizationNames.get(application.getOrganizationId()));
    return new ListLiveEnvironmentApplicationsResponse(applications.stream().map(respond).toList());
  }

  // 승인하면 조직의 실제 환경을 열고 신청한 실결제 키를 실제 환경의 키로 옮긴다.
  // 감사 행은 운영자가 신청한 조직에 한 일로 남긴다.
  @Transactional
  public LiveEnvironmentApplicationResponse update(
      AuthenticatedPrincipal principal,
      UUID applicationId,
      UpdateLiveEnvironmentApplicationStatusRequest request) {
    boolean isApproval = LiveEnvironmentApplication.APPROVED.equals(request.status());
    String rejectionReason =
        request.rejectionReason() == null ? "" : request.rejectionReason().strip();
    if (isApproval && !rejectionReason.isEmpty()) {
      throw invalidReason(REJECTION_REASON_UNEXPECTED_MESSAGE);
    }
    if (!isApproval && rejectionReason.isEmpty()) {
      throw invalidReason(REJECTION_REASON_REQUIRED_MESSAGE);
    }

    // 신청하는 쪽과 같은 순서(조직, 신청)로 잠가 서로 기다리다 멈추지 않게 한다.
    UUID organizationId =
        liveEnvironmentApplicationRepository
            .findOrganizationIdById(applicationId)
            .orElseThrow(
                () -> new NotFoundException(ErrorCode.LIVE_ENVIRONMENT_APPLICATION_NOT_FOUND));
    Organization organization =
        organizationRepository
            .findForUpdateById(organizationId)
            .orElseThrow(() -> new NotFoundException(ErrorCode.ORGANIZATION_NOT_FOUND));
    LiveEnvironmentApplication application =
        liveEnvironmentApplicationRepository
            .findForUpdateById(applicationId)
            .orElseThrow(
                () -> new NotFoundException(ErrorCode.LIVE_ENVIRONMENT_APPLICATION_NOT_FOUND));
    if (!application.isPending()) {
      throw new ConflictException(ErrorCode.LIVE_ENVIRONMENT_APPLICATION_ALREADY_REVIEWED);
    }

    OffsetDateTime reviewedAt = now();
    if (isApproval) {
      UpdateTossPaymentsCredentialRequest keys =
          new UpdateTossPaymentsCredentialRequest(
              application.getTossPaymentsClientKey(), application.getTossPaymentsSecretKey());
      application.approve(principal.userAccountId(), reviewedAt);
      organization.openLiveEnvironment(reviewedAt);
      BusinessEnvironmentContext.run(
          organizationEnvironments.resolve(organizationId, LIVE_MODE),
          () -> tossPaymentsCredentialService.store(organizationId, LIVE_MODE, keys));
    } else {
      application.reject(principal.userAccountId(), reviewedAt, rejectionReason);
    }
    auditLogService.record(
        AuditActor.from(principal).withOrganizationId(organizationId),
        isApproval
            ? "live_environment_application_approved"
            : "live_environment_application_rejected",
        TARGET_TYPE,
        applicationId.toString(),
        Map.of("business_status", application.getBusinessStatus()));
    return LiveEnvironmentApplicationResponse.from(application, organization.getName());
  }

  private Organization get(UUID organizationId) {
    return organizationRepository
        .findById(organizationId)
        .orElseThrow(() -> new NotFoundException(ErrorCode.ORGANIZATION_NOT_FOUND));
  }

  private void requireApplicable(Organization organization) {
    if (organization.isLiveEnvironmentOpen()) {
      throw new ConflictException(ErrorCode.LIVE_ENVIRONMENT_ALREADY_OPEN);
    }
    if (liveEnvironmentApplicationRepository.existsByOrganizationIdAndStatus(
        organization.getId(), LiveEnvironmentApplication.PENDING)) {
      throw new ConflictException(ErrorCode.LIVE_ENVIRONMENT_APPLICATION_IN_PROGRESS);
    }
  }

  private void check(CreateLiveEnvironmentApplicationRequest request) {
    try {
      tossPaymentsCredentialService.check(
          LIVE_MODE,
          new UpdateTossPaymentsCredentialRequest(
              request.tossPaymentsClientKey(), request.tossPaymentsSecretKey()));
    } catch (InvalidRequestException exception) {
      // 키 저장 API의 필드 이름(client_key, secret_key)을 신청서의 필드 이름으로 바꿔 입력 칸 옆에 닿게 한다.
      throw new InvalidRequestException(
          exception.getErrorCode(),
          exception.getErrors().stream()
              .map(
                  error ->
                      new FieldError(TOSS_PAYMENTS_FIELD_PREFIX + error.field(), error.message()))
              .toList());
    }
  }

  private static InvalidRequestException invalidNumber(String message) {
    return new InvalidRequestException(
        ErrorCode.INVALID_BUSINESS_REGISTRATION_NUMBER,
        List.of(new FieldError(BUSINESS_REGISTRATION_NUMBER_FIELD, message)));
  }

  private static InvalidRequestException invalidReason(String message) {
    return new InvalidRequestException(
        ErrorCode.VALIDATION_ERROR, List.of(new FieldError(REJECTION_REASON_FIELD, message)));
  }

  private static OffsetDateTime now() {
    return OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
  }
}
