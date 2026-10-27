package com.meterengine.pricing.service;

import com.meterengine.audit.service.AuditLogService;
import com.meterengine.customer.repository.CustomerRepository;
import com.meterengine.global.error.ConflictException;
import com.meterengine.global.error.ErrorCode;
import com.meterengine.global.error.ErrorResponse.FieldError;
import com.meterengine.global.error.InvalidRequestException;
import com.meterengine.global.error.NotFoundException;
import com.meterengine.metric.service.BillableMetricUsageService;
import com.meterengine.organization.repository.OrganizationRepository;
import com.meterengine.pricing.dto.CreatePricePlanRequest;
import com.meterengine.pricing.dto.ListPricePlansResponse;
import com.meterengine.pricing.dto.PricePlanResponse;
import com.meterengine.pricing.entity.PricePlan;
import com.meterengine.pricing.repository.PricePlanRepository;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PricePlanService {
  private final PricePlanRepository pricePlanRepository;
  private final CustomerRepository customerRepository;
  private final OrganizationRepository organizationRepository;
  private final AuditLogService auditLogService;

  PricePlanService(
      PricePlanRepository pricePlanRepository,
      CustomerRepository customerRepository,
      OrganizationRepository organizationRepository,
      AuditLogService auditLogService) {
    this.pricePlanRepository = pricePlanRepository;
    this.customerRepository = customerRepository;
    this.organizationRepository = organizationRepository;
    this.auditLogService = auditLogService;
  }

  @Transactional(readOnly = true)
  public ListPricePlansResponse list(UUID organizationId, YearMonth month, UUID customerId) {
    validateCustomer(organizationId, customerId);
    return new ListPricePlansResponse(
        pricePlanRepository.findByOrganizationIdOrderByEffectiveFromDesc(organizationId).stream()
            .filter(plan -> Objects.equals(plan.getCustomerId(), customerId))
            .filter(
                plan ->
                    month == null
                        || (plan.getEffectiveFrom().compareTo(month.toString()) <= 0
                            && (plan.getEffectiveUntil() == null
                                || plan.getEffectiveUntil().compareTo(month.toString()) > 0)))
            .map(PricePlanResponse::from)
            .toList());
  }

  @Transactional
  public PricePlanResponse create(UUID organizationId, CreatePricePlanRequest request) {
    YearMonth current = YearMonth.now(BillableMetricUsageService.BILLING_ZONE);
    YearMonth start = request.effectiveFrom() == null ? current : request.effectiveFrom();
    if (start.isBefore(current))
      throw new ConflictException(ErrorCode.PRICE_PLAN_HISTORY_IMMUTABLE);
    if (request.effectiveUntil() != null && !request.effectiveUntil().isAfter(start))
      throw new InvalidRequestException(
          ErrorCode.VALIDATION_ERROR, List.of(new FieldError("effective_until", "시작 월보다 뒤여야 합니다")));
    validateCustomer(organizationId, request.customerId());
    organizationRepository
        .findForUpdateById(organizationId)
        .orElseThrow(() -> new NotFoundException(ErrorCode.ORGANIZATION_NOT_FOUND));
    PricePlan plan =
        pricePlanRepository.findByOrganizationIdOrderByEffectiveFromDesc(organizationId).stream()
            .filter(
                value ->
                    Objects.equals(value.getCustomerId(), request.customerId())
                        && value.getEffectiveFrom().equals(start.toString()))
            .findFirst()
            .orElse(null);
    String until = request.effectiveUntil() == null ? null : request.effectiveUntil().toString();
    Map<String, Object> previous = plan == null ? null : detail(plan);
    if (plan == null)
      plan =
          new PricePlan(
              organizationId,
              request.customerId(),
              start.toString(),
              until,
              request.baseFee(),
              request.minimumAmount());
    else plan.update(until, request.baseFee(), request.minimumAmount());
    pricePlanRepository.saveAndFlush(plan);
    Map<String, Object> detail = detail(plan);
    if (previous != null) detail.put("previous", previous);
    auditLogService.record(
        organizationId, "price_plan_created", "price_plan", plan.getId().toString(), detail);
    return PricePlanResponse.from(plan);
  }

  @Transactional
  public void delete(UUID organizationId, UUID id) {
    organizationRepository
        .findForUpdateById(organizationId)
        .orElseThrow(() -> new NotFoundException(ErrorCode.ORGANIZATION_NOT_FOUND));
    PricePlan plan =
        pricePlanRepository
            .findById(id)
            .filter(value -> value.getOrganizationId().equals(organizationId))
            .orElseThrow(() -> new NotFoundException(ErrorCode.PRICE_PLAN_NOT_FOUND));
    if (plan.getEffectiveFrom()
            .compareTo(YearMonth.now(BillableMetricUsageService.BILLING_ZONE).toString())
        < 0) throw new ConflictException(ErrorCode.PRICE_PLAN_HISTORY_IMMUTABLE);
    pricePlanRepository.delete(plan);
    pricePlanRepository.flush();
    auditLogService.record(
        organizationId, "price_plan_deleted", "price_plan", id.toString(), detail(plan));
  }

  private static Map<String, Object> detail(PricePlan plan) {
    Map<String, Object> detail = new LinkedHashMap<>();
    detail.put("customer_id", plan.getCustomerId());
    detail.put("effective_from", plan.getEffectiveFrom());
    detail.put("effective_until", plan.getEffectiveUntil());
    detail.put("base_fee", plan.getBaseFee());
    detail.put("minimum_amount", plan.getMinimumAmount());
    return detail;
  }

  private void validateCustomer(UUID organizationId, UUID customerId) {
    if (customerId != null
        && !customerRepository.existsByOrganizationIdAndId(organizationId, customerId))
      throw new NotFoundException(ErrorCode.CUSTOMER_NOT_FOUND);
  }
}
