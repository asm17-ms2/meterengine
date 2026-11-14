package com.meterengine.liveenvironment.service;

import com.meterengine.audit.service.AuditActor;
import com.meterengine.audit.service.AuditLogService;
import com.meterengine.payment.api.TossPaymentsMerchantObserved;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class LiveEnvironmentMerchantService {

  private static final Logger logger =
      LoggerFactory.getLogger(LiveEnvironmentMerchantService.class);

  private final JdbcTemplate jdbcTemplate;
  private final AuditLogService auditLogService;
  private final TransactionTemplate transactionTemplate;

  LiveEnvironmentMerchantService(
      JdbcTemplate jdbcTemplate,
      AuditLogService auditLogService,
      PlatformTransactionManager platformTransactionManager) {
    this.jdbcTemplate = jdbcTemplate;
    this.auditLogService = auditLogService;
    this.transactionTemplate = new TransactionTemplate(platformTransactionManager);
    this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  // 승인 뒤 처음 본 상점아이디만 신청한 값과 견준다. 결제를 막지 않도록 따로 커밋하고 실패는 로그로만 남긴다.
  @EventListener
  public void observe(TossPaymentsMerchantObserved event) {
    try {
      transactionTemplate.executeWithoutResult(status -> check(event));
    } catch (RuntimeException exception) {
      logger.warn(
          "live environment merchant id check failed: organization_id={}",
          event.organizationId(),
          exception);
    }
  }

  private void check(TossPaymentsMerchantObserved event) {
    // 여러 서버가 같은 조직의 첫 결제를 함께 처리해도 한 쪽만 행을 돌려받는다.
    List<Map<String, Object>> checked =
        jdbcTemplate.queryForList(
            """
            UPDATE live_environment_application
            SET merchant_id_checked_at = ?, observed_toss_payments_merchant_id = ?
            WHERE organization_id = ? AND status = 'approved' AND merchant_id_checked_at IS NULL
            RETURNING id, toss_payments_merchant_id
            """,
            Timestamp.from(Instant.now()),
            event.merchantId(),
            event.organizationId());
    for (Map<String, Object> application : checked) {
      String applied = (String) application.get("toss_payments_merchant_id");
      if (applied.equals(event.merchantId())) {
        continue;
      }
      UUID applicationId = (UUID) application.get("id");
      logger.warn(
          "live environment merchant id mismatched: organization_id={}, application_id={}",
          event.organizationId(),
          applicationId);
      auditLogService.record(
          AuditActor.system(event.organizationId()),
          "live_environment_merchant_id_mismatched",
          "live_environment_application",
          applicationId.toString(),
          Map.of("applied", applied, "observed", event.merchantId()));
    }
  }
}
